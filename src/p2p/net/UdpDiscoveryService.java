package p2p.net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import p2p.AppInfo;
import p2p.model.PeerInfo;
import p2p.model.SharedFile;

public class UdpDiscoveryService {
    public interface DiscoveryHandler {
        void peerFound(PeerInfo peer);

        void fileFound(PeerInfo peer, String fileId, long size, int chunkCount, String fileName);
    }

    private final String secretHash;
    private final String nodeId;
    private final String hostName;
    private final int tcpPort;
    private final DiscoveryHandler handler;
    private final Set<String> seenMessages = Collections.synchronizedSet(new HashSet<>());
    private volatile boolean running;
    private DatagramSocket socket;
    private Thread listenerThread;

    public UdpDiscoveryService(String secretHash, String nodeId, String hostName, int tcpPort, DiscoveryHandler handler) {
        this.secretHash = secretHash;
        this.nodeId = nodeId;
        this.hostName = hostName;
        this.tcpPort = tcpPort;
        this.handler = handler;
    }

    public void start() throws SocketException {
        socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        socket.setBroadcast(true);
        socket.bind(new InetSocketAddress(AppInfo.DISCOVERY_PORT));
        running = true;
        listenerThread = new Thread(this::listenLoop, "p2p-udp-discovery");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public void stop() {
        running = false;
        if (socket != null) {
            socket.close();
        }
    }

    public void sendDiscovery() {
        String messageId = UUID.randomUUID().toString();
        seenMessages.add(messageId);
        String message = String.join("|", Protocol.PREFIX, "DISCOVER", secretHash, nodeId,
                Protocol.encode(hostName), Integer.toString(tcpPort), Integer.toString(Protocol.FLOOD_TTL), messageId);
        sendToBroadcasts(message);
    }

    public void announceFiles(List<SharedFile> files) {
        synchronized (files) {
            for (SharedFile file : files) {
                String message = String.join("|", Protocol.PREFIX, "FILE", secretHash, nodeId,
                        Protocol.encode(hostName), Integer.toString(tcpPort), file.getFileId(),
                        Long.toString(file.getSize()), Integer.toString(file.getChunkCount()),
                        Protocol.encode(file.getFileName()));
                sendToBroadcasts(message);
            }
        }
    }

    private void listenLoop() {
        byte[] buffer = new byte[65535];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String message = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                handleMessage(message, packet.getAddress());
            } catch (IOException e) {
                if (running) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void handleMessage(String message, InetAddress address) {
        String[] parts = message.split("\\|");
        if (parts.length < 6 || !Protocol.PREFIX.equals(parts[0]) || !secretHash.equals(parts[2])) {
            return;
        }
        String type = parts[1];
        String remoteNodeId = parts[3];
        if (nodeId.equals(remoteNodeId)) {
            return;
        }
        String remoteHost = Protocol.decode(parts[4]);
        int remotePort = Integer.parseInt(parts[5]);
        PeerInfo peer = new PeerInfo(remoteNodeId, remoteHost, address, remotePort);
        if ("DISCOVER".equals(type) && parts.length >= 8) {
            String messageId = parts[7];
            if (seenMessages.add(messageId)) {
                handler.peerFound(peer);
                sendHello(address);
                int ttl = Integer.parseInt(parts[6]);
                if (ttl > 0) {
                    String forwarded = String.join("|", Protocol.PREFIX, "DISCOVER", secretHash, remoteNodeId,
                            parts[4], parts[5], Integer.toString(ttl - 1), messageId);
                    sendToBroadcasts(forwarded);
                }
                trimSeenMessages();
            }
        } else if ("HELLO".equals(type)) {
            handler.peerFound(peer);
        } else if ("FILE".equals(type) && parts.length >= 10) {
            handler.peerFound(peer);
            handler.fileFound(peer, parts[6], Long.parseLong(parts[7]), Integer.parseInt(parts[8]), Protocol.decode(parts[9]));
        }
    }

    private void sendHello(InetAddress address) {
        String message = String.join("|", Protocol.PREFIX, "HELLO", secretHash, nodeId,
                Protocol.encode(hostName), Integer.toString(tcpPort));
        sendTo(message, address);
    }

    private void sendToBroadcasts(String message) {
        for (InetAddress address : broadcastAddresses()) {
            sendTo(message, address);
        }
    }

    private void sendTo(String message, InetAddress address) {
        try {
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length, address, AppInfo.DISCOVERY_PORT);
            socket.send(packet);
        } catch (IOException e) {
            if (running) {
                e.printStackTrace();
            }
        }
    }

    private List<InetAddress> broadcastAddresses() {
        List<InetAddress> addresses = new ArrayList<>();
        try {
            addresses.add(InetAddress.getByName("255.255.255.255"));
            addresses.add(InetAddress.getByName("127.0.0.1"));
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!networkInterface.isUp()) {
                    continue;
                }
                networkInterface.getInterfaceAddresses().forEach(interfaceAddress -> {
                    InetAddress broadcast = interfaceAddress.getBroadcast();
                    if (broadcast != null) {
                        addresses.add(broadcast);
                    }
                });
            }
        } catch (Exception ignored) {
        }
        return addresses;
    }

    private void trimSeenMessages() {
        if (seenMessages.size() > 2048) {
            seenMessages.clear();
        }
    }
}
