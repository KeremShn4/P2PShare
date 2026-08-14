package p2p.model;

import java.nio.file.Path;

public class SharedFile {
    private final String fileId;
    private final String fileName;
    private final Path relativePath;
    private final Path fullPath;
    private final long size;
    private final int chunkCount;

    public SharedFile(String fileId, String fileName, Path relativePath, Path fullPath, long size, int chunkCount) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.relativePath = relativePath;
        this.fullPath = fullPath;
        this.size = size;
        this.chunkCount = chunkCount;
    }

    public String getFileId() {
        return fileId;
    }

    public String getFileName() {
        return fileName;
    }

    public Path getRelativePath() {
        return relativePath;
    }

    public Path getFullPath() {
        return fullPath;
    }

    public long getSize() {
        return size;
    }

    public int getChunkCount() {
        return chunkCount;
    }
}
