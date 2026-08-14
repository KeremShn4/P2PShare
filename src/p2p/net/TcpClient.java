package p2p.net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import p2p.model.PeerInfo;
import p2p.model.RemoteFile;

public final class TcpClient {
    private TcpClient() {
    }

    public static boolean hello(PeerInfo peer, String secretHash) {
        try (Socket socket = connect(peer)) {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer.write("HELLO " + secretHash);
            writer.newLine();
            writer.flush();
            String line = reader.readLine();
            return line != null && line.startsWith("P2P471 HELLO_OK");
        } catch (IOException e) {
            return false;
        }
    }

    public static List<RemoteFile> listFiles(PeerInfo peer, String secretHash) throws IOException {
        List<RemoteFile> files = new ArrayList<>();
        try (Socket socket = connect(peer)) {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer.write("LIST " + secretHash);
            writer.newLine();
            writer.flush();
            String line;
            while ((line = reader.readLine()) != null) {
                if ("END".equals(line)) {
                    break;
                }
                String[] parts = line.split(" ", 5);
                if (parts.length == 5 && "FILE".equals(parts[0])) {
                    RemoteFile file = new RemoteFile(parts[1], Long.parseLong(parts[2]), Integer.parseInt(parts[3]));
                    file.addSource(peer, Protocol.decode(parts[4]));
                    files.add(file);
                }
            }
        }
        return files;
    }

    private static Socket connect(PeerInfo peer) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(peer.getAddress(), peer.getPort()), 3000);
        socket.setSoTimeout(30000);
        return socket;
    }
}
