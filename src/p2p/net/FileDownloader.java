package p2p.net;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import p2p.AppInfo;
import p2p.model.PeerInfo;
import p2p.model.RemoteFile;
import p2p.model.TransferInfo;

public class FileDownloader implements Runnable {
    public interface ProgressListener {
        void updated(TransferInfo transfer);
    }

    private final Path sharedFolder;
    private final String secretHash;
    private final RemoteFile remoteFile;
    private final ProgressListener listener;
    private final TransferInfo transfer;

    public FileDownloader(Path sharedFolder, String secretHash, RemoteFile remoteFile, ProgressListener listener) {
        this.sharedFolder = sharedFolder;
        this.secretHash = secretHash;
        this.remoteFile = remoteFile;
        this.listener = listener;
        this.transfer = new TransferInfo(remoteFile.getPreferredName(), remoteFile.getSize());
    }

    @Override
    public void run() {
        List<PeerInfo> sources = remoteFile.getSources();
        if (sources.isEmpty()) {
            transfer.setStatus("No source");
            listener.updated(transfer);
            return;
        }

        Path target = uniqueTarget(sharedFolder.resolve(remoteFile.getPreferredName()));
        Path temp = target.resolveSibling(target.getFileName().toString() + ".part");
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(4, sources.size()));
        try {
            Files.createDirectories(sharedFolder);
            try (RandomAccessFile raf = new RandomAccessFile(temp.toFile(), "rw")) {
                raf.setLength(remoteFile.getSize());
            }
            transfer.setStatus("Downloading");
            listener.updated(transfer);

            @SuppressWarnings("unchecked")
            Future<Void>[] futures = new Future[remoteFile.getChunkCount()];
            for (int i = 0; i < remoteFile.getChunkCount(); i++) {
                final int chunkIndex = i;
                futures[i] = pool.submit(() -> {
                    downloadChunkWithFallback(sources, temp, chunkIndex);
                    return null;
                });
            }
            for (Future<Void> future : futures) {
                future.get();
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            transfer.setStatus("Completed");
            listener.updated(transfer);
        } catch (Exception e) {
            transfer.setStatus("Failed: " + e.getMessage());
            listener.updated(transfer);
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
            }
        } finally {
            pool.shutdownNow();
        }
    }

    public TransferInfo getTransfer() {
        return transfer;
    }

    private void downloadChunkWithFallback(List<PeerInfo> sources, Path temp, int chunkIndex) throws IOException {
        IOException lastError = null;
        int start = chunkIndex % sources.size();
        for (int i = 0; i < sources.size(); i++) {
            PeerInfo peer = sources.get((start + i) % sources.size());
            try {
                downloadChunk(peer, temp, chunkIndex);
                return;
            } catch (IOException e) {
                lastError = e;
            }
        }
        throw lastError == null ? new IOException("No source available") : lastError;
    }

    private void downloadChunk(PeerInfo peer, Path temp, int chunkIndex) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(peer.getAddress(), peer.getPort()), 4000);
            socket.setSoTimeout(30000);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            writer.write("GET " + secretHash + " " + remoteFile.getFileId() + " " + chunkIndex);
            writer.newLine();
            writer.flush();

            InputStream input = socket.getInputStream();
            String header = readLine(input);
            String[] parts = header.split(" ");
            if (parts.length < 3 || !"P2P471".equals(parts[0]) || !"OK".equals(parts[1])) {
                throw new IOException("Bad chunk response");
            }
            int length = Integer.parseInt(parts[2]);
            long offset = (long) chunkIndex * AppInfo.CHUNK_SIZE;
            byte[] buffer = new byte[8192];
            int remaining = length;
            try (RandomAccessFile raf = new RandomAccessFile(temp.toFile(), "rw")) {
                raf.seek(offset);
                while (remaining > 0) {
                    int read = input.read(buffer, 0, Math.min(buffer.length, remaining));
                    if (read == -1) {
                        throw new IOException("Unexpected end of chunk");
                    }
                    raf.write(buffer, 0, read);
                    remaining -= read;
                    transfer.addTransferredBytes(read);
                    listener.updated(transfer);
                }
            }
        }
    }

    private String readLine(InputStream input) throws IOException {
        StringBuilder builder = new StringBuilder();
        int value;
        while ((value = input.read()) != -1) {
            if (value == '\n') {
                break;
            }
            if (value != '\r') {
                builder.append((char) value);
            }
        }
        return builder.toString();
    }

    private Path uniqueTarget(Path base) {
        if (!Files.exists(base)) {
            return base;
        }
        String name = base.getFileName().toString();
        String stem = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            stem = name.substring(0, dot);
            ext = name.substring(dot);
        }
        for (int i = 1; i < 1000; i++) {
            Path candidate = base.resolveSibling(stem + "_" + i + ext);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        return base.resolveSibling(stem + "_" + System.currentTimeMillis() + ext);
    }
}
