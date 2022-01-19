package org.ericghara;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class MovieCollection {

    private final MovieFolder rootFolder;

    public MovieCollection(String pathString) {
        Path rootPath = FileSystems.getDefault().getPath(pathString).toAbsolutePath();
        Collector collector = new Collector(rootPath);
        rootFolder = collector.getRootFolder();
    }

    public Path getRootPath() {
        return rootFolder.getFolderPath();
    }

    public boolean containsFolder(Path path) {
        return openFolder(path).isPresent();
    }

    public boolean containsFile(Path filePath) {
        Path parent = filePath.getParent();
        Path filename = filePath.getFileName();
        Optional<MovieFolder> folder = openFolder(parent);
        return folder.isPresent() && folder.get().containsFile(filename);
    }

    Optional<MovieFolder> openFolder(Path path) {
        FileClassifier.mustBeAbsolutePath(path);
        Path rootPath = getRootPath();
        Optional <MovieFolder> curFolder = path.startsWith(rootPath) ? Optional.of(rootFolder) : Optional.empty();
        // Note: a for each loop of relativized paths doesn't work because path "" is considered to have a length 1
        for (int i = rootPath.getNameCount(); i < path.getNameCount()
                && curFolder.isPresent(); i++) {
            curFolder = curFolder.get()
                                 .getFolder(path.getName(i) );
        }
        return curFolder;
    }

    MovieFolder openFolder(Path p, String exceptionMsg)  {
        return openFolder(p).orElseThrow( () -> new IllegalArgumentException(exceptionMsg) );
    }

    void deleteFile(Path path) {
        BiConsumerThrows<Path,Path> copyIO = (src, nul) ->
                Files.delete(src);
        TetraConsumer<MovieFolder, MovieFolder, Path, Path> updateRecords = (src, nul0, filename, nul1) -> {
            FileType type = src.getFileType(filename)
                    .orElseThrow(IllegalArgumentException::new); // this exception will never be thrown
            src.deleteRecord(filename, type);
        };
        fileIOHelper(path, null, copyIO, updateRecords);
    }

    void copyFile(Path source, Path destination) {
        BiConsumerThrows<Path,Path> copyIO = (src, dest) ->
                Files.copy(src, dest, StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
        TetraConsumer<MovieFolder, MovieFolder, Path, Path> updateRecords = (src, dest, srcFilename, destFilename) -> {
            FileType type = src.getFileType(srcFilename)
                               .orElseThrow(IllegalArgumentException::new); // this exception will never be thrown
            dest.addFile(destFilename, type);
        };
        fileIOHelper(source, destination, copyIO, updateRecords);
    }

    void moveFile(Path source, Path destination) {
        BiConsumerThrows<Path,Path> moveIO = (src, dest) ->
                Files.move(src, dest, LinkOption.NOFOLLOW_LINKS);
        TetraConsumer<MovieFolder, MovieFolder, Path, Path> updateRecords = (src, dest, srcFilename, destFilename) -> {
            FileType type = src.getFileType(srcFilename)
                    .orElseThrow(IllegalArgumentException::new); // this exception will never be thrown
            src.deleteRecord(srcFilename, type);
            dest.addFile(destFilename, type);
        };
        fileIOHelper(source, destination, moveIO, updateRecords);
    }

    /**
     * Encapsulates shared functionality required by file IO methods: {@link MovieCollection#deleteFile},
     * {@link MovieCollection#moveFile}, {@link MovieCollection#copyFile}.  The destination path may be null for
     * operations which only require a source path.  The IO operation may return any exception which will be
     * caught and rethrown as an {@link IllegalArgumentException};
     *
     * @param source full filepath including filename
     * @param destination full filepath including filename
     * @param ioOperation BiConsumerThrows which performs file IO operations
     * @param updateRecords TetraConsumer which performs MovieFolder record operations
     * @see BiConsumerThrows
     * @see TetraConsumer
     */
    private void fileIOHelper(Path source, Path destination, BiConsumerThrows<Path, Path> ioOperation,
                              TetraConsumer<MovieFolder, MovieFolder, Path, Path> updateRecords ) {
        Path sourceParent = source.getParent();
        Path sourceFileName = source.getFileName();
        MovieFolder sourceFolder = openFolder(sourceParent,
                "Could not resolve the source path: " + source);
        FileType type = sourceFolder.getFileType(sourceFileName).orElseThrow( () ->
                new IllegalArgumentException("The source file could not be located: " + source) );
        Path destinationFileName = null;
        MovieFolder destinationFolder = null;
        if (Objects.nonNull(destination) ) { // support for operations with  1 or 2 targets
            Path destinationParent = destination.getParent();
            destinationFileName = destination.getFileName();
            destinationFolder = openFolder(destinationParent,
                    "Could not resolve the destination path: " + destination);
            if (Objects.nonNull(type) && destinationFolder.contains(destinationFileName, type) ) {  // filesystem io for files
                throw new IllegalArgumentException("The destination folder already contains the file: " + source);
            }
            else if (destinationFolder.contains(destinationFileName, type) ) {
                throw new IllegalArgumentException("The destination folder contains a folder with the same name: " + source);
            }
        }
        try {
            ioOperation.accept(source, destination);
        } catch (Exception e) {
            throw new IllegalArgumentException("A low level file IO error occurred " +
                    source +" to " + destination  + " - check file permissions.", e);
        }
        updateRecords.accept(sourceFolder, destinationFolder, sourceFileName, destinationFileName);
    }

    void moveFolder(Path source, Path destination) {
        BiConsumerThrows<Path,Path> moveIO = (src, dest) -> Files.move(src,dest, LinkOption.NOFOLLOW_LINKS);
        TetraConsumer<MovieFolder, MovieFolder, MovieFolder, Path> updateRecords = (srcFolder, srcParent, dstParent, dstFolderName) -> {
            srcParent.deleteRecord(srcFolder.getFolderPath().getFileName(), FileType.Folder);
            srcFolder.changePath(dstFolderName, -1); // set to dummy depth and new folder name
            dstParent.addFolder(srcFolder);
            updateSubfolderPaths(srcFolder);
        };
        folderIOHelper(source, destination, moveIO, updateRecords);
    }

    void copyFolder(Path source, Path destination) {
        BiConsumerThrows<Path,Path> moveIO = (src, dest) -> { // copies only the folder (not contents)
            Files.copy(src, dest, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES);
        };
        TetraConsumer<MovieFolder, MovieFolder, MovieFolder, Path> createRecords = (srcFolder, srcParent, dstParent, dstFolderName) -> {
            int newDepth = dstParent.getDepth() + 1;
            Path newPath = dstParent.toAbsolutePath(dstFolderName );
            MovieFolder dstFolder = new MovieFolder(newPath, newDepth);
            dstParent.addFolder(dstFolder);                   // add new folder record (no files added)
            srcFolder.getAllFiles().forEach( (f) ->           // copy all files and update MovieFolder records
                    copyFile(srcFolder.toAbsolutePath(f), dstFolder.toAbsolutePath(f) ) );
        };
        Function<Path, Path> toDest = (f) -> {         // generates destination path from src MovieFolder
            Path relPath = source.relativize(f);
            return destination.resolve(relPath);
        };
        // walks source subfolder tree and copies all folders and their contents to the destination
        getSubFolders(source).map(MovieFolder::getFolderPath)
                             .forEach( (f) ->
                                     folderIOHelper(f, toDest.apply(f), moveIO, createRecords ) );
    }

    void deleteFolder(Path path) {
        BiConsumerThrows<Path, Path> deleteIO = (target, nul) -> Files.delete(path);
        TetraConsumer<MovieFolder, MovieFolder, MovieFolder, Path> updateRecords = (target, parent, nul, nul0) -> {
            Path folderName = target.getFolderPath().getFileName();
            parent.deleteRecord(folderName, FileType.Folder);
        };
        folderIOHelper(path, null, deleteIO, updateRecords);
    }

    void folderIOHelper(Path source, Path destination, BiConsumerThrows<Path, Path> ioOperation,
                        TetraConsumer<MovieFolder, MovieFolder, MovieFolder, Path> recordOps ) {
        MovieFolder srcFolder = openFolder(source).orElseThrow(
                () -> new IllegalArgumentException("Could not open the source: " + source) );
        MovieFolder srcParent = openFolder(source.getParent(), "" ); // will never be thrown
        MovieFolder dstParent = null;
        Path dstFolderName = null;
        if (Objects.nonNull(destination) ) {
            dstFolderName = destination.getFileName();
            dstParent = openFolder(destination.getParent(),
                    "Could not open the parent destination folder of: " + destination);
        }
        try {
            ioOperation.accept(source, destination);
        } catch (Exception e) {
            throw new IllegalArgumentException("A low level file IO error occurred " +
                    source +" to " + destination  + " - check folder permissions.", e);
        }
         recordOps.accept(srcFolder, srcParent, dstParent, dstFolderName);
    }

    /**
     * Performs a breadth first search beginning at the origin subfolder.  The first folder to be returned in the
     * search will always be the origin folder subsequent directories will be returned in the order of increasing
     * depth from the origin directory, with directories occurring at the same depth being returned in an undefined
     * order.
     * @param origin MovieFolder to begin the search from
     * @return Stream of the origin MovieFolder and all MovieFolders that are children of origin.
     */
    Stream<MovieFolder> getSubFolders(MovieFolder origin) {
        Objects.requireNonNull(origin, "Received a null origin folder.");
        Stream.Builder<MovieFolder> folders = Stream.builder();
        LinkedList<MovieFolder> queue = new LinkedList<>();
        queue.addLast(origin);
        while (!queue.isEmpty()) {
            IntStream.range(0, queue.size()).forEach((i) -> {
                MovieFolder cur = queue.removeFirst();
                folders.accept(cur);
                cur.getFolders().forEach(queue::addLast);
                });
        }
        return folders.build();
    }

    /**
     * See {@link MovieCollection#getSubFolders(MovieFolder)}
     * @param origin absolute path to the starting subfolder
     * @return Stream of the origin MovieFolder and all MovieFolders that are children of origin.
     */
    Stream<MovieFolder> getSubFolders(Path origin) {
        MovieFolder originFolder = openFolder(origin, "Could not open the source folder: " + origin);
        return getSubFolders(originFolder);
    }

    private void updateSubfolderPaths(MovieFolder origin) {
        getSubFolders(origin).forEach( (f) ->
            f.getFolders().forEach(f::addFolder) );
    }

    private int getDepth(Path path) {
        return path.getNameCount() - getRootPath().getNameCount();
    }

    @FunctionalInterface
    public interface BiConsumerThrows<T, U> {

        void accept(T t, U u) throws Exception;
    }

    @FunctionalInterface
    public interface TetraConsumer<T, U, V, X> {

        void accept(T t, U u, V v, X x);
    }

    /**
     * Simple test client that takes two arguments:
     * <ol>
     *     <li>Path to movie folder (may be relative or absolute)</li>
     *     <li>The path to a folder within the movie folder (may be relative or absolute)</li>
     * </ol>
     * Prints to stdout the movie folder directory
     * @param args path to a movie folder and a path to a folder within that folder
     */
    public static void main(String[] args) {
        String pathString = args[0];
        Path query = Paths.get(args[1]);
        MovieCollection col = new MovieCollection(pathString);
        Path absQuery = query.isAbsolute() ? query : col.getRootPath().resolve(query);
        System.out.println(col.rootFolder);
        System.out.println(col.openFolder(absQuery));
    }

    private static class Collector {
        private final int MIN_VIDEO_SIZE_MB = 50;

        Path rootPath;
        MovieFolder rootFolder;
        LinkedList<MovieFolder> folderStack;
        private final FileClassifier fileClassifier;


        private Collector(Path rootPath) {
            mustBeDir(rootPath);
            this.rootPath = rootPath;
            folderStack = new LinkedList<>();
            fileClassifier = new FileClassifier();
            walkStream().filter(FileClassifier::isReadableAndWriteable)
                        .map(this::manageStack)
                        .forEach(this::sortFiles);
            rootFolder = folderStack.removeFirst(); // ...it's a stack 99.9% of the time.
        }

        private MovieFolder getRootFolder() {
            return rootFolder;
        }

        private void mustBeDir(Path path) {
            if (!FileClassifier.isDir(path)) {
                throw new IllegalArgumentException("Starting path must be a directory and cannot be a symlink");
            }
        }

        // note implemented differently than the outer getDepth method as rootFolder may be null
        private int getDepth(Path path) {
            return path.getNameCount() - rootPath.getNameCount();
        }

        private int currentDepth() {
            return folderStack.size() - 1;
        }

        private Path manageStack(Path path) {
            int placeAtDepth = getDepth(path)-1;
            int curDepth = currentDepth();
            int stackPops = curDepth -  placeAtDepth;
            if (stackPops < 0) {
                throw new IllegalArgumentException("Calculated a negative number of stackPops which is impossible: " + path );
            }
            IntStream.range(0, stackPops)
                     .forEach((i) -> folderStack.removeLast());
        return path;
        }

        private Path sortFiles(Path path) {
            if (FileClassifier.isFile(path)) {
                MovieFolder folder = Objects.requireNonNull(folderStack.peekLast(), "Received a null folder!");
                addFile(folder, path);
            }
            else if (FileClassifier.isDir(path)) {
                addFolder(path);
            }
            else {
                System.out.println("Path target couldn't be classified as a directory or regular file: " + path);
            }
            return path;
        }

        private void addFile(MovieFolder folder, Path path) {
            Path filename = path.getFileName();
            if (fileClassifier.fileSizeLargerThan(path, MIN_VIDEO_SIZE_MB) ) {
                if (fileClassifier.isVideo(filename) )  {
                    folder.addFile(filename, FileType.Movie);
                }
                else {
                    folder.addFile(filename, FileType.Unusual);
                }
            }
            // Note: currently rejecting potential isSub matches with file sizes > MIN_VIDEO_SIZE_MB.
            else if (fileClassifier.isSub(filename)) {
                folder.addFile(filename, FileType.Sub);
            }
            else {
                folder.addFile(filename, FileType.PossiblyJunk);
            }
        }

        private void addFolder(Path path) {
            int depth = getDepth(path);
            MovieFolder newFolder = new MovieFolder(path, depth);
            if (!folderStack.isEmpty()) {
                MovieFolder curFolder = Objects.requireNonNull(folderStack.peekLast());
                curFolder.addFolder(newFolder);
            }
            folderStack.addLast(newFolder);
        }

        /**
         * Creates the stream of paths from walking from the root path.  Does not follow Symlinks, and filters files
         * which are neither readable nor writeable.
         *
         * @return Paths from current directory
         */
        Stream<Path> walkStream() {
            Stream.Builder<Path> walkStream = Stream.builder();
            try {
                // Custom SimpleFileVisitor which suppresses IOExceptions (unlike Files.walk(Path) )
                Files.walkFileTree( rootPath,new SimpleFileVisitor<>() {
                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                                try {
                                        walkStream.add(file);
                                } catch (Exception e) {
                                    System.out.println("Suppressed an IOException in SimpleFileVisitor.FileVisit: "
                                    + file + ".");
                                }
                                return FileVisitResult.CONTINUE;
                            }
                            @Override
                            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                                System.out.println("Suppressed an IOException in SimpleFileVisitor.FileVisitFailed: "
                                        + file + ".");
                                exc.printStackTrace();
                                exc.addSuppressed(new IOException());
                                return FileVisitResult.CONTINUE;
                            }
                            @Override
                            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                                walkStream.add(dir);
                                return FileVisitResult.CONTINUE;
                            }
                            @Override
                            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                                if (Objects.nonNull(exc) ) {
                                    exc.addSuppressed(new IOException());
                                    System.out.println("Suppressed an IOException in SimpleFileVisitor.postVisitDirectory: "
                                            + dir + ".");
                                }
                                    return FileVisitResult.CONTINUE;
                            }

                        } ); } catch (Exception e) {
                                System.out.println("Suppressed an exception in SimpleFileVisitor.FileVisit.");
                                e.printStackTrace();
            }
            return walkStream.build();
        }
    }
}
