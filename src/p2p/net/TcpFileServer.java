package p2p.net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import p2p.AppInfo;
import p2p.model.SharedFile;

public class TcpFileServer {
    private final int port;
    private final String secretHash;
    private final String nodeId;
    private final String hostName;
    private final List<SharedFile> localFiles;
    private final Map<String, SharedFile> filesById;
    private final ExecutorService clients = Executors.newCachedThreadPool();
    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread serverThread;

    public TcpFileServer(int port, String secretHash, String nodeId, String hostName,
            List<SharedFile> localFiles, Map<String, SharedFile> filesById) {
        this.port = port;
        this.secretHash = secretHash;
        this.nodeId = nodeId;
        this.hostName = hostName;
        this.localFiles = localFiles;
        this.filesById = filesById;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        serverThread = new Thread(this::acceptLoop, "p2p-tcp-server");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
        clients.shutdownNow();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                clients.submit(() -> handleClient(socket));
            } catch (IOException e) {
                if (running) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        try (Socket client = socket;
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                OutputStream output = client.getOutputStream()) {
            client.setSoTimeout(30000);
            String line = reader.readLine();
            if (line == null) {
                return;
            }
            String[] parts = line.split(" ");
            if ("HELLO".equals(parts[0]) && parts.length == 1) {
                writeText(output, "P2P471 HELLO_SERVICE " + Protocol.encode(hostName) + " " + port + "\n");
                return;
            }
            if (parts.length < 2 || !secretHash.equals(parts[1])) {
                writeText(output, "P2P471 ERROR bad-secret\n");
                return;
            }
            if ("HELLO".equals(parts[0])) {
                writeText(output, "P2P471 HELLO_OK " + nodeId + " " + Protocol.encode(hostName) + " " + port + "\n");
            } else if ("LIST".equals(parts[0])) {
                writeList(output);
            } else if ("GET".equals(parts[0]) && parts.length >= 4) {
                sendChunk(output, parts[2], Integer.parseInt(parts[3]));
            } else {
                writeText(output, "P2P471 ERROR unknown-command\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void writeList(OutputStream output) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
        synchronized (localFiles) {
            for (SharedFile file : localFiles) {
                writer.write("FILE " + file.getFileId() + " " + file.getSize() + " "
                        + file.getChunkCount() + " " + Protocol.encode(file.getFileName()));
                writer.newLine();
            }
        }
        writer.write("END");
        writer.newLine();
        writer.flush();
    }

    private void sendChunk(OutputStream output, String fileId, int chunkIndex) throws IOException {
        SharedFile file = filesById.get(fileId);
        if (file == null || chunkIndex < 0 || chunkIndex >= file.getChunkCount()) {
            writeText(output, "P2P471 ERROR missing-file\n");
            return;
        }
        long offset = (long) chunkIndex * AppInfo.CHUNK_SIZE;
        int length = (int) Math.min(AppInfo.CHUNK_SIZE, file.getSize() - offset);
        writeText(output, "P2P471 OK " + length + "\n");
        byte[] buffer = new byte[8192];
        try (RandomAccessFile raf = new RandomAccessFile(file.getFullPath().toFile(), "r")) {
            raf.seek(offset);
            int remaining = length;
            while (remaining > 0) {
                int read = raf.read(buffer, 0, Math.min(buffer.length, remaining));
                if (read == -1) {
                    break;
                }
                output.write(buffer, 0, read);
                remaining -= read;
            }
        }
        output.flush();
    }

    private void writeText(OutputStream output, String text) throws IOException {
        output.write(text.getBytes(StandardCharsets.UTF_8));
        output.flush();
    }
}
