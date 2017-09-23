package network.stratus;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.http.StandardHttpRequestor;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.DeleteErrorException;
import com.dropbox.core.v2.files.DeletedMetadata;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.ListFolderGetLatestCursorResult;
import com.dropbox.core.v2.files.ListFolderLongpollResult;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.files.SearchMatch;
import com.dropbox.core.v2.files.SearchMode;
import com.dropbox.core.v2.files.SearchResult;
import com.dropbox.core.v2.files.WriteMode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Client {

    private final DbxClientV2 client;
    private final DbxClientV2 clientLongpoll;

    public Client(String accessToken) {
        this.client = client(accessToken);
        this.clientLongpoll = client(accessToken, Duration.ofMinutes(5));
    }

    public Optional<Metadata> metadata(Location location) throws DbxException {
        try {
            SearchResult search = client.files()
                .searchBuilder(location.remoteParent().toString(), location.remote().getFileName().toString())
                .withMode(SearchMode.FILENAME)
                .withMaxResults(1L)
                .start();
            return search.getMatches().stream()
                .map(SearchMatch::getMetadata)
                .findFirst();
        } catch(DbxException e) {
            return Optional.empty();
        }
    }

    public Optional<FileMetadata> metadataFile(Location location) throws DbxException {
        Optional<Metadata> metadata = metadata(location);
        if(metadata.isPresent() && metadata.get() instanceof FileMetadata) {
            return Optional.of((FileMetadata) metadata.get());
        } else {
            return Optional.empty();
        }
    }

    public boolean uploadable(Location location, AtomicBoolean... isNew) throws DbxException {
        File file = location.file();
        if(!file.exists() || file.isHidden()) {
            return false;
        } else {
            if(location.directory()) {
                ListFolderResult list = client.files().listFolder(location.remoteParent().toString());
                while(!list.getEntries().isEmpty()) {
                    for(Metadata metadata : list.getEntries()) {
                        if(metadata instanceof FolderMetadata &&
                           metadata.getPathLower().equalsIgnoreCase(location.remote().toString())) {
                            return false;
                        }
                    }
                    list = client.files().listFolderContinue(list.getCursor());
                }
                return true;
            } else {
                Optional<FileMetadata> metadata = metadataFile(location);
                if(metadata.isPresent()) {
                    if(metadata.get().getContentHash().equalsIgnoreCase(location.hash())) {
                        return false;
                    } else {
                        Instant remoteModified = metadata.get().getClientModified().toInstant(),
                                localModified = location.modified();
                        return localModified.isAfter(remoteModified);
                    }
                } else {
                    if(isNew.length > 0) isNew[0].set(true);
                    return true;
                }
            }
        }
    }

    public boolean upload(Location location) throws IOException, DbxException {
        AtomicBoolean isNew = new AtomicBoolean(false);
        if(uploadable(location, isNew)) {
            if(location.directory()) {
                client.files().createFolderV2(location.remote().toString());
            } else {
                InputStream input = new FileInputStream(location.file());
                client.files().uploadBuilder(location.remote().toString())
                    .withClientModified(new Date(location.modified().toEpochMilli()))
                    .withAutorename(false)
                    .withMode(isNew.get() ? WriteMode.ADD : WriteMode.OVERWRITE)
                    .uploadAndFinish(input);
            }
            return true;
        }
        return false;
    }

    public boolean downloadable(Location location) throws DbxException {
        if(location.directory()) {
            return true;
        } else {
            Optional<FileMetadata> metadata = metadataFile(location);
            if(metadata.isPresent()) {
                if(metadata.get().getContentHash().equalsIgnoreCase(location.hash())) {
                    return false;
                } else {
                    Instant remoteModified = metadata.get().getClientModified().toInstant(),
                            localModified = location.modified();
                    return remoteModified.isAfter(localModified);
                }
            } else {
                return false;
            }
        }
    }

    public boolean download(Location location) throws IOException, DbxException {
        if(downloadable(location)) {
            location.mkdir();
            if(!location.directory()) {
                FileOutputStream output = new FileOutputStream(location.local().toString());
                client.files().downloadBuilder(location.remote().toString()).download(output);
                return true;
            }
        }
        return false;
    }

    public boolean delete(Location location) throws DbxException {
        try {
            client.files().deleteV2(location.remote().toString());
            return true;
        } catch(DeleteErrorException e) {
            // Ignore de-syncing delete errors
        }
        return false;
    }

    public int downloadBatch(Location location) throws DbxException, InterruptedException {
        ListFolderResult result = client.files()
                .listFolderBuilder(location.remote().toString())
                .withIncludeDeleted(false)
                .withIncludeMountedFolders(true)
                .withIncludeMediaInfo(false)
                .withRecursive(true)
                .start();
        AtomicInteger changed = new AtomicInteger(0);
        Random random = new Random();
        List<Thread> threads = new ArrayList<>();
        while(true) {
            for(Metadata metadata : result.getEntries()) {
                Location loc = Location.fromMetadata(metadata);
                Thread fetch = new Thread(() -> {
                    try {
                        if(download(loc)) changed.incrementAndGet();
                    } catch(IOException | DbxException e) {
                        e.printStackTrace();
                    }
                });
                fetch.setName(loc.remote().toString());
                fetch.setPriority(1);
                fetch.start();
                threads.add(fetch);
                Thread.sleep(random.nextInt(10));
            }
            if(result.getHasMore()) {
                result = client.files().listFolderContinue(result.getCursor());
            } else {
                break;
            }
            Thread.sleep(100);
        }
        while(threads.stream().anyMatch(Thread::isAlive)) {
            Thread.sleep(100);
        }
        return changed.intValue();
    }

    public void pull(Location location) throws IOException, DbxException, InterruptedException {
        ListFolderGetLatestCursorResult resultCursor = client.files()
            .listFolderGetLatestCursorBuilder(location.remote().toString())
            .withIncludeDeleted(true)
            .withIncludeMountedFolders(true)
            .withIncludeMediaInfo(false)
            .withRecursive(true)
            .start();
        String cursor = resultCursor.getCursor();
        while(true) {
            ListFolderLongpollResult resultLongpoll = clientLongpoll.files()
                .listFolderLongpoll(cursor, 120);
            if(resultLongpoll.getChanges()) {
                while(true) {
                    ListFolderResult resultList = client.files()
                        .listFolderContinue(cursor);
                    for(Metadata metadata : resultList.getEntries()) {
                        Location loc = Location.fromMetadata(metadata);
                        if(metadata instanceof FileMetadata) {
                            download(loc);
                        } else if(metadata instanceof FolderMetadata) {
                            loc.mkdir();
                        } else if(metadata instanceof DeletedMetadata) {
                            loc.local().toFile().delete();
                        }
                    }
                    cursor = resultList.getCursor();
                    if(!resultList.getHasMore()) break;
                }
            }
            Long backoff = resultLongpoll.getBackoff();
            if(backoff != null) {
                Thread.sleep(TimeUnit.SECONDS.toMillis(backoff));
            }
        }
    }

    public void push(Location location) throws IOException, InterruptedException, DbxException {
        location.mkdir();
        Supplier<Map<Location, Long>> traversal = () -> {
            try {
                return Files.walk(location.local())
                    .collect(Collectors.toMap(Location::fromLocal, path -> path.toFile().lastModified()));
            } catch(IOException ioe) {
                throw new RuntimeException(ioe);
            }
        };
        Map<Location, Long> cache = traversal.get();
        while(true) {
            Map<Location, Long> current = traversal.get();
            Set<Location> deleted = new HashSet<>();
            for(Location loc : cache.keySet()) {
                if(!current.containsKey(loc)) {
                    for(Location loc1 : loc.all()) {
                        delete(loc1);
                        deleted.add(loc1);
                    }
                }
            }
            for(Location loc : current.keySet()) {
                if(deleted.contains(loc)) continue;
                if(!cache.containsKey(loc) ||
                   loc.modified().toEpochMilli() > cache.get(loc)) {
                    for(Location loc1 : loc.all()) {
                        upload(loc1);
                    }
                }
            }
            cache = current;
            Thread.sleep(1000);
        }
    }

    private DbxClientV2 client(String accessToken, Duration... timeout) {
        StandardHttpRequestor.Config.Builder builder = StandardHttpRequestor.Config.DEFAULT_INSTANCE.copy();
        StandardHttpRequestor.Config config;
        if(timeout.length > 0) {
            config = builder.withReadTimeout(timeout[0].toNanos(), TimeUnit.NANOSECONDS).build();
        } else {
            config = builder.build();
        }
        StandardHttpRequestor requestor = new StandardHttpRequestor(config);
        DbxRequestConfig requestConfig = DbxRequestConfig.newBuilder(id())
                .withHttpRequestor(requestor)
                .build();
        return new DbxClientV2(requestConfig, accessToken);
    }

    private String id() {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch(UnknownHostException uhe) {
            hostname = "unknown-" + UUID.randomUUID();
        }
        return "dropbox-sync-" + hostname;
    }

}
