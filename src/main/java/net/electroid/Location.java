package net.electroid;

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

/**
 * A file or folder that is synced to Dropbox.
 *
 * Contains two paths that reference...
 * a) a local file stored on a hard disk
 * b) a remote file stored on Dropbox's servers
 */
public class Location {

    private static Path localPathRoot;
    private static Path remotePathRoot;

    private static final Hash hasher = new Hash();
    private final Path localPath;
    private final Path remotePath;

    private Location(Path localPath, Path remotePath) {
        this.localPath = localPath;
        this.remotePath = remotePath;
    }

    /**
     * Returns the locally defined file.
     * @return the local file.
     */
    public File file() {
        return local().toFile();
    }

    /**
     * Check whether the file exists locally.
     * This may occur if the file has not yet been synced.
     * @return whether the file occurs locally.
     */
    public boolean exists() {
        return file().exists();
    }

    /**
     * Generate a hash of the current local file.
     * This can be used to compare against the remote
     * version to see if changes have been made.
     * @return the unique hash of the file.
     */
    public String hash() {
        return exists() ? hasher.hash(local()) : "";
    }

    /**
     * Get the last instant the file was modified.
     * @return time the file was modified.
     */
    public Instant modified() {
        return Instant.ofEpochMilli(exists() ? file().lastModified() : 0);
    }

    /**
     * Return the absolute path to the local file.
     * @return absolute path to the local file.
     */
    public Path local() {
        return localPath;
    }

    /**
     * Return the absolute path to the remote file.
     * @return absolute path to the remote file.
     */
    public Path remote() {
        return remotePath;
    }

    /**
     * Ensure that a file or folder has a locally defined parent folder.
     * @return whether the operation resulted in a new folder being created.
     */
    public boolean mkdir() {
        return (directory() ? local() : local().getParent()).toFile().mkdirs();
    }

    /**
     * Whether the file is a directory type.
     * @return whether the file is a directory.
     */
    public boolean directory() {
        return file().isDirectory();
    }

    /**
     * Recursively return all sub-files of this file.
     * Returns itself if the file is not a directory.
     * @return sub-file of this file.
     */
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

    /**
     * Get the parent directory of the remote file.
     * @return parent directory of the remote file.
     */
    public Path remoteParent() {
        String parent = remote().getParent().toString();
        if(parent.equalsIgnoreCase("/")) {
            parent = ""; // Special case for the Dropbox API
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

    /**
     * Get the root location of where files are being stored locally.
     * @return root location of local file storage.
     */
    public static Location root() {
        return new Location(localPathRoot, remotePathRoot);
    }

    /**
     * Get a combined local and remote location given just the local path.
     * @param localPath path to a folder or directory locally.
     * @return the combined local and remote location.
     */
    public static Location fromLocal(Path localPath) {
        return new Location(localPath, Paths.get(remotePathRoot.toString() + localPath.toString().replaceAll(localPathRoot.toString(), "")));
    }

    /**
     * Get a combined local and remote location given just the remote path.
     * @param remotePath path to a folder or directory locally.
     * @return the combined local and remote location.
     */
    public static Location fromRemote(Path remotePath) {
        return new Location(Paths.get(localPathRoot.toString() + remotePath.toString().replaceAll(remotePathRoot.toString(), "")), remotePath);
    }

    /**
     * Get the combined local and remote location given a Metadata
     * object from the Dropbox API.
     * @param metadata file metadata from Dropbox.
     * @return the combined local and remote location.
     */
    public static Location fromMetadata(Metadata metadata) {
        return fromRemote(Paths.get(metadata.getPathLower()));
    }

    /**
     * Set the root relative location for both the local and remote files.
     * @param localPathRoot root relative location.
     * @param remotePathRoot root remote location.
     */
    public static void setRoot(Path localPathRoot, Path remotePathRoot) {
        Location.localPathRoot = localPathRoot;
        Location.remotePathRoot = remotePathRoot;
    }

}