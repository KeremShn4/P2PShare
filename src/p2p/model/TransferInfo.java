package p2p.model;

public class TransferInfo {
    private final String fileName;
    private final long totalBytes;
    private volatile long transferredBytes;
    private volatile String status;

    public TransferInfo(String fileName, long totalBytes) {
        this.fileName = fileName;
        this.totalBytes = totalBytes;
        this.status = "Starting";
    }

    public String getFileName() {
        return fileName;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public long getTransferredBytes() {
        return transferredBytes;
    }

    public void addTransferredBytes(long bytes) {
        transferredBytes += bytes;
    }

    public int getPercentage() {
        if (totalBytes <= 0) {
            return 100;
        }
        return (int) Math.min(100, (transferredBytes * 100) / totalBytes);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
