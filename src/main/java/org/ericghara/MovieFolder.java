package org.ericghara;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import java.util.stream.Stream;

enum FileType {
    Movie(0), Sub(1), Unusual(2), PossiblyJunk(3);

    private final int id;

    FileType(int id) {
        this.id = id;
    }

    int id() {
        return id;
    }

}

class MovieFolder {
    private final Path folderPath;
    private final int depth;
    private final HashMap<Path, MovieFolder> folders;
    private final ArrayList<HashSet<Path>> allFiles;

    MovieFolder(Path path, int depth) {
        folderPath = path.toAbsolutePath();
        this.depth = depth;
        folders = new HashMap<>();
        allFiles = new ArrayList<>(4);
        Arrays.asList(FileType.values()).forEach((v) -> allFiles.add(new HashSet<>()));
    }

    /**
     * Returns true if the file is contained in this folder, false if not.  Finds any file irrespective of
     * {@code FileType}.
     * @param filename must be only the filename, cannot have the parent
     * @return boolean
     */
    public boolean containsFile(Path filename) {
        FileClassifier.mustBeFilename(filename);
        return getFileType(filename).isPresent();
    }

    public boolean containsFile(Path filename, FileType type) {
        FileClassifier.mustBeFilename(filename);
        return allFiles.get(type.id() ).contains(filename);
    }

    public boolean containsFolder(Path folderName) {
        FileClassifier.mustBeFilename(folderName);
        return folders.containsKey(folderName);
    }

    public int getNumFolders() {
        return folders.size();
    }

    public int getNumMovies() {
        return getMovies().size();
    }

    public int getNumPossiblyJunk() {
        return getPossiblyJunk().size();
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

    HashMap<Path, MovieFolder> getFolders() {
        return folders;
    }

    void addFolder(MovieFolder folder) {
        Path folderName = folder.getFolderPath().getFileName();
        folders.put(folderName, folder);
    }

    public int getNumUnusuals() {
        return getUnusuals().size();
    }

    /**
     * Reports if this movie folder contains no files and no subfolders.
     * @return true if empty, false if contains fails and/or subfolders
     */
    public boolean isEmpty() {
        int numFiles = allFiles.stream().map(HashSet::size).reduce(0, Integer::sum);
        return numFiles == 0 && folders.size() == 0;
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
        final FileType[] fileTypes = FileType.values();
        final int NumFileTypes = fileTypes.length;
        OptionalInt id = IntStream.range(0, NumFileTypes)
                .filter( (i) -> allFiles.get(i).contains(filename) )
                .findFirst();
        return id.isPresent() ? Optional.of(fileTypes[id.getAsInt()] ) : Optional.empty();
    }


    boolean addFile(Path filename, FileType type) {
        FileClassifier.mustBeFilename(filename);
        return allFiles.get(type.id() ).add(filename);
    }

    void addSub(Path filename) {
        FileClassifier.mustBeFilename(filename);
        getSubs().add(filename);
    }

    private HashSet<Path> getSubs() {
        return allFiles.get(FileType.Sub.id());
    }

    int getNumSubs() {
        return getSubs().size();
    }

    void addMovie(Path filename) {
        FileClassifier.mustBeFilename(filename);
        getMovies().add(filename);
    }

    private HashSet<Path> getMovies() {
        return allFiles.get(FileType.Movie.id());
    }



    void addUnusual(Path filename) {
        FileClassifier.mustBeFilename(filename);
        getUnusuals().add(filename);
    }

    private HashSet<Path> getUnusuals() {
        return allFiles.get(FileType.Unusual.id());
    }



    void addPossiblyJunk(Path filename) {
        FileClassifier.mustBeFilename(filename);
        getPossiblyJunk().add(filename);
    }

    private HashSet<Path> getPossiblyJunk() {
        return allFiles.get(FileType.PossiblyJunk.id());
    }



    Optional<MovieFolder> getFolder(Path folderName) {
        FileClassifier.mustBeFilename(folderName);
        return Optional.ofNullable(folders.get(folderName) );
    }

    void deleteFolder(Path folderName) {
        Path absPath = toAbsolutePath(folderName);
        if (!containsFolder(folderName) ) {
            throw new IllegalArgumentException("Was unable to locate the folder for deletion: " + absPath );
        }
        try {
            Files.delete(absPath);
        } catch (Exception e) {
            throw new IllegalArgumentException("The Directory " + absPath +
                    " could not be deleted, due to a low level error," +
                    "refer to the stack trace for more details." );
        }
    }

    /**
     * Deletes the specified file.
     * @param filename name of file to be deleted, path must not have any other components
     */
    void deleteFile(Path filename) {
        FileClassifier.mustBeFilename(filename);
        boolean success = Stream.of(FileType.values())
                                .anyMatch( (type) -> allFiles.get(type.id()).remove(filename));
        Path absPath = toAbsolutePath(filename);
        if (!success) {
            throw new IllegalArgumentException("Could not locate the file:"
                    + absPath );
        }
        try {
            Files.delete(absPath);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not delete: " + absPath);
        }
    }

    Stream<Path> getAllFiles() {
        Stream.Builder<Path> builder = Stream.builder();
        allFiles.forEach( (set) -> set.forEach(builder::add) );
        return builder.build();
    }
}
