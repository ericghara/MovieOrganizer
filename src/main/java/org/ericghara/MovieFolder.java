package org.ericghara;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

enum FileType {
    Folder(-1), Movie(0), Sub(1), Unusual(2), PossiblyJunk(3);

    final static int NUM_FILE_TYPES = 4; // FileTypes indexed in allFiles array;
    final static int OFFSET = FileType.values().length - NUM_FILE_TYPES;

    private final int id;

    static int numFileTypes() {
        return NUM_FILE_TYPES; }

    static int offset() {
        return OFFSET;
    }

    static FileType getFileType(int id) {
        int i = OFFSET + id;
        return FileType.values()[i];
    }

    FileType(int id) {
        this.id = id;
    }

    int id() {
        return id;
    }

}

class MovieFolder {
    private final HashMap<Path, MovieFolder> folders;
    private final ArrayList<HashSet<Path>> allFiles;
    private Path folderPath;
    private int depth;

    MovieFolder(Path path, int depth) {
        folderPath = path.toAbsolutePath();
        this.depth = depth;
        folders = new HashMap<>();
        allFiles = new ArrayList<>(FileType.numFileTypes() );
        IntStream.range(0, FileType.numFileTypes() )
                 .forEach( (i) -> allFiles.add(new HashSet<>() ) );
    }

    public boolean contains(Path name, FileType type) {
        FileClassifier.mustBeFilename(name);
        return getFilenames(type).contains(name);
    }

    public boolean containsFile(Path name) {
        Optional<FileType> file = getFileType(name);
        return file.isPresent();
    }

    public int getNum(FileType type) {
        return getFilenames(type).size();
    }

    /**
     * Returns a path set of the specified FileType (including Folders)
     *
     * @param type The category File, or Folder
     * @return all target paths matching the type
     */
    Set<Path> getFilenames(FileType type) {
        int id = type.id();
        if (id >= 0)  {
            return allFiles.get(id);
        }
        else if (type.equals(FileType.Folder) ) {
            return folders.keySet();
        }
        else {
            throw new IllegalArgumentException("The given FileType has not been fully implemented: "
                    + type.name() );
        }
    }

    Collection<MovieFolder> getFolders() {
        return folders.values();
    }

    public Path getFolderPath() {
        return folderPath;
    }

    public int getDepth() {
        return depth;
    }

    @Override
    public String toString(){
        return folderPath.toString();
    }

    void addFile(Path filename, FileType type) {
        FileClassifier.mustBeFilename(filename);
        boolean success = allFiles.get(type.id() ).add(filename);
        if (!success) {
            throw new IllegalArgumentException(String.format(
                    "The folder: %s already contains a file named %s", this, filename) );
        }
    }

    void addFolder(MovieFolder folder) {
        Path folderName = folder.getFolderPath().getFileName();
        Path fullPath = getFolderPath().resolve(folderName);
        if (!Files.exists(fullPath, LinkOption.NOFOLLOW_LINKS) ) {
            throw new IllegalArgumentException("A MovieFolder reference cannot be made for the folder because" +
                    "it does not exist in this location: " + fullPath);
        }
        folder.changePath(fullPath, getDepth()+1);
        folders.put(folderName, folder);
    }

    /**
     * Reports if this movie folder contains no files and no sub-folders.
     * @return true if empty, false if contains fails and/or sub-folders
     */
    public boolean isEmpty() {
        return Stream.of(FileType.values() ).allMatch( (t) -> getFilenames(t).isEmpty() );
    }

    public Path toAbsolutePath(Path filename) {
        FileClassifier.mustBeFilename(filename);
        return folderPath.resolve(filename);
    }

    //checks if this dir is a parent of the path.  Must provide an absolute path
    public boolean fileBelongsHere(Path path) {
        FileClassifier.mustBeAbsolutePath(path);
        return path.getParent().equals(folderPath);
    }

    public Optional<FileType> getFileType(Path filename) {
        FileClassifier.mustBeFilename(filename);
        OptionalInt id = IntStream.range(0, FileType.numFileTypes() )
                .filter( (i) -> allFiles.get(i).contains(filename) )
                .findFirst();
        return id.isPresent() ? Optional.of(FileType.getFileType(id.getAsInt() ) ) : Optional.empty();
    }

    /**
     * Deletes record from this {@code MovieFolder} object.  The file/dir <em>must</em> no longer
     * be contained in this folder on the filesystem.
     * @param name - file targeted for record deletion (must be a name only)
     * @param type - type of file to be deleted
     * @see MovieCollection#deleteFile
     */
    public void deleteRecord(Path name, FileType type) {
        Path absPath = toAbsolutePath(name);
        if (Files.exists(absPath, LinkOption.NOFOLLOW_LINKS) ) {
            throw new IllegalArgumentException("The file/folder must be deleted from the filesystem" +
                    " before its record can be updated: " + absPath);
        }
        if (!getFilenames(type).remove(name) ) { // perform remove and confirm key existed
            throw new IllegalArgumentException("Could not locate the record for" +
                    " deletion: " + absPath );
        }
    }

    Optional<MovieFolder> getFolder(Path folderName) {
        FileClassifier.mustBeFilename(folderName);
        return Optional.ofNullable(folders.get(folderName) );
    }

    void changePath(Path path, int depth) {
        this.folderPath = path;
        this.depth = depth;
    }

    Stream<Path> getAllFiles() {
        Stream.Builder<Path> builder = Stream.builder();
        allFiles.forEach( (set) -> set.forEach(builder::add) );
        return builder.build();
    }
}
