package network.stratus;

import com.dropbox.core.v2.files.Metadata;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Location {

    private static Path localPathRoot;
    private static Path remotePathRoot;
    private final Path localPath;
    private final Path remotePath;

    Location(Path localPath, Path remotePath) {
        this.localPath = localPath;
        this.remotePath = remotePath;
    }

    public File file() {
        return local().toFile();
    }

    public boolean exists() {
        return file().exists();
    }

    public String hash() {
        return exists() ? new Hash().hash(local()) : "";
    }

    public Instant modified() {
        return Instant.ofEpochMilli(exists() ? file().lastModified() : 0);
    }

    public Path local() {
        return localPath;
    }

    public Path remote() {
        return remotePath;
    }

    public boolean mkdir() {
        return (directory() ? local() : local().getParent()).toFile().mkdirs();
    }

    public boolean directory() {
        return file().isDirectory();
    }

    public List<Location> all() {
        Stream<Location> stream;
        if(directory()) {
            try {
                stream = Files.walk(local()).map(Location::fromLocal);
            } catch(IOException e) {
                e.printStackTrace();
                stream = Stream.empty();
            }
        } else {
            stream = Stream.of(this);
        }
        return stream.collect(Collectors.toList());
    }

    public Path remoteParent() {
        String parent = remote().getParent().toString();
        if(parent.equalsIgnoreCase("/")) {
            parent = ""; // Special case for Dropbox API
        }
        return Paths.get(parent);
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null &&
               obj instanceof Location &&
               remote().equals(((Location) obj).remote());
    }

    @Override
    public int hashCode() {
        return remote().hashCode();
    }

    @Override
    public String toString() {
        return "Location{local=" + local() + ", remote=" + remote() + "}";
    }

    public static Location root() {
        return new Location(localPathRoot, remotePathRoot);
    }

    public static Location fromLocal(Path localPath) {
        return new Location(localPath, Paths.get(remotePathRoot.toString() + localPath.toString().replaceAll(localPathRoot.toString(), "")));
    }

    public static Location fromRemote(Path remotePath) {
        return new Location(Paths.get(localPathRoot.toString() + remotePath.toString().replaceAll(remotePathRoot.toString(), "")), remotePath);
    }

    public static Location fromMetadata(Metadata metadata) {
        return fromRemote(Paths.get(metadata.getPathLower()));
    }

    public static void setRoot(Path localPathRoot, Path remotePathRoot) {
        Location.localPathRoot = localPathRoot;
        Location.remotePathRoot = remotePathRoot;
    }

}