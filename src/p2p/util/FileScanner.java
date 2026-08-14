package p2p.util;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import p2p.AppInfo;
import p2p.model.SharedFile;

public final class FileScanner {
    private FileScanner() {
    }

    public static List<SharedFile> scan(Path root, Set<String> excludedFolders) throws IOException {
        List<SharedFile> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!Files.isReadable(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (!dir.equals(root) && isExcluded(root.relativize(dir), excludedFolders)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile()) {
                    return FileVisitResult.CONTINUE;
                }
                long size = attrs.size();
                String hash = HashUtil.sha256(file);
                String fileId = hash + "-" + size;
                int chunks = Math.max(1, (int) ((size + AppInfo.CHUNK_SIZE - 1) / AppInfo.CHUNK_SIZE));
                Path relative = root.relativize(file);
                files.add(new SharedFile(fileId, file.getFileName().toString(), relative, file, size, chunks));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    private static boolean isExcluded(Path relativeDir, Set<String> excludedFolders) {
        String value = relativeDir.toString().replace('\\', '/');
        for (String excluded : excludedFolders) {
            String normalized = excluded.trim().replace('\\', '/');
            if (!normalized.isEmpty() && (value.equals(normalized) || value.startsWith(normalized + "/"))) {
                return true;
            }
        }
        return false;
    }
}
