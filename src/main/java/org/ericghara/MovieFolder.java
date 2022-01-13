package org.ericghara;

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

    HashMap<Path, MovieFolder> getFolders() {
        return folders;
    }

    void addFolder(MovieFolder folder) {
        Path folderName = folder.getFolderPath().getFileName();
        folders.put(folderName, folder);
    }

    int getNumFolders() {
        return folders.size();
    }

    void addSub(Path filename) {
        FileClassifier.mustBeFilename(filename);
        getSubs().add(filename);
    }

    HashSet<Path> getSubs() {
        return allFiles.get(FileType.Sub.id());
    }

    int getNumSubs() {
        return getSubs().size();
    }

    void addMovie(Path filename) {
        FileClassifier.mustBeFilename(filename);
        getMovies().add(filename);
    }

    HashSet<Path> getMovies() {
        return allFiles.get(FileType.Movie.id());
    }

    int getNumMovies() {
        return getMovies().size();
    }

    void addUnusual(Path filename) {
        FileClassifier.mustBeFilename(filename);
        getUnusuals().add(filename);
    }

    HashSet<Path> getUnusuals() {
        return allFiles.get(FileType.Unusual.id());
    }

    int getNumUnusuals() {
        return getUnusuals().size();
    }

    void addPossiblyJunk(Path filename) {
        FileClassifier.mustBeFilename(filename);
        getPossiblyJunk().add(filename);
    }

    HashSet<Path> getPossiblyJunk() {
        return allFiles.get(FileType.PossiblyJunk.id());
    }

    int getNumPossiblyJunk() {
        return getPossiblyJunk().size();
    }

    Path getFolderPath() {
        return folderPath;
    }

    int getDepth() {
        return depth;
    }

    /**
     * Returns true if the file is contained in this folder, false if not.  Finds any file irrespective of
     * {@code FileType}.
     * @param filename must be only the filename, cannot have the parent
     * @return boolean
     */
    boolean containsFile(Path filename) {
        FileClassifier.mustBeFilename(filename);
        return getFileType(filename).isPresent();
    }

    boolean containsFolder(Path folderName) {
        FileClassifier.mustBeFilename(folderName);
        return folders.containsKey(folderName);
    }

    MovieFolder getFolder(Path folderName) {
        FileClassifier.mustBeFilename(folderName);
        return folders.get(folderName);
    }

    Path toAbsolutePath(Path filename) {
        FileClassifier.mustBeFilename(filename);
        return folderPath.resolve(filename);
    }

    //checks if this dir is a parent of the path.  Must provide an absolute path
    boolean fileBelongsHere(Path path) {
        FileClassifier.mustBeAbsolutePath(path);
        return path.getParent().equals(folderPath);
    }

    Optional<FileType> getFileType(Path filename) {
        FileClassifier.mustBeFilename(filename);
        final FileType[] FileTypes = FileType.values();
        final int NumFileTypes = FileTypes.length;
        OptionalInt id = IntStream.range(0, NumFileTypes)
                                    .filter( (i) -> allFiles.get(i)
                                                            .contains(filename) )
                                    .findFirst();
        return id.isPresent() ? Optional.of(FileTypes[id.getAsInt()] ) : Optional.empty();
    }

    Stream<Path> getAllFiles() {
        Stream.Builder<Path> builder = Stream.builder();
        allFiles.forEach( (set) -> set.forEach(builder::add) );
        return builder.build();
    }

    @Override
    public String toString(){
        return folderPath.toString();
    }

}
