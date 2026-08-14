package p2p.net;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import p2p.model.AppConfig;
import p2p.model.PeerInfo;
import p2p.model.RemoteFile;
import p2p.model.SharedFile;
import p2p.model.TransferInfo;
import p2p.util.FileScanner;
import p2p.util.HashUtil;

public class P2PService {
    private final String nodeId = UUID.randomUUID().toString();
    private final List<ServiceListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();
    private final Map<String, RemoteFile> remoteFiles = new ConcurrentHashMap<>();
    private final List<SharedFile> localFiles = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, SharedFile> localFilesById = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;
    private ExecutorService workers;

    private AppConfig config;
    private String secretHash;
    private String hostName;
    private TcpFileServer tcpServer;
    private UdpDiscoveryService udpDiscovery;
    private volatile boolean running;

    public void addListener(ServiceListener listener) {
        listeners.add(listener);
    }

    public void start(AppConfig config) throws IOException {
        if (running) {
            stop();
        }
        if (!Files.isDirectory(config.getSharedFolder())) {
            throw new IOException("Shared folder does not exist");
        }
        this.config = config;
        this.secretHash = HashUtil.sha256(config.getSecret());
        this.hostName = InetAddress.getLocalHost().getHostName();
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.workers = Executors.newCachedThreadPool();

        scanLocalFiles();
        tcpServer = new TcpFileServer(config.getTcpPort(), secretHash, nodeId, hostName, localFiles, localFilesById);
        tcpServer.start();

        udpDiscovery = new UdpDiscoveryService(secretHash, nodeId, hostName, config.getTcpPort(),
                new UdpDiscoveryService.DiscoveryHandler() {
                    @Override
                    public void peerFound(PeerInfo peer) {
                        verifyAndAddPeer(peer);
                    }

                    @Override
                    public void fileFound(PeerInfo peer, String fileId, long size, int chunkCount, String fileName) {
                        addRemoteFile(peer, fileId, size, chunkCount, fileName);
                    }
                });
        udpDiscovery.start();
        running = true;

        scheduler.scheduleAtFixedRate(this::safeScanAndAnnounce, 0, 8, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::discover, 0, 5, TimeUnit.SECONDS);
        status("Connected on TCP port " + config.getTcpPort() + " / UDP discovery port " + p2p.AppInfo.DISCOVERY_PORT);
    }

    public void stop() {
        running = false;
        if (udpDiscovery != null) {
            udpDiscovery.stop();
        }
        if (tcpServer != null) {
            tcpServer.stop();
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (workers != null) {
            workers.shutdownNow();
        }
        peers.clear();
        remoteFiles.clear();
        firePeersChanged();
        fireFilesChanged();
        status("Disconnected");
    }

    public boolean isRunning() {
        return running;
    }

    public void download(RemoteFile file) {
        if (!running || config == null) {
            return;
        }
        FileDownloader downloader = new FileDownloader(config.getSharedFolder(), secretHash, file, this::fireTransferUpdated);
        fireTransferUpdated(downloader.getTransfer());
        workers.submit(downloader);
    }

    private void safeScanAndAnnounce() {
        if (!running) {
            return;
        }
        try {
            scanLocalFiles();
            udpDiscovery.announceFiles(new ArrayList<>(localFiles));
            for (PeerInfo peer : peers.values()) {
                fetchListFromPeer(peer);
            }
        } catch (Exception e) {
            status("Scan failed: " + e.getMessage());
        }
    }

    private void discover() {
        if (running && udpDiscovery != null) {
            udpDiscovery.sendDiscovery();
        }
    }

    private void scanLocalFiles() throws IOException {
        List<SharedFile> scanned = FileScanner.scan(config.getSharedFolder(), config.getExcludedFolders());
        synchronized (localFiles) {
            localFiles.clear();
            localFiles.addAll(scanned);
        }
        localFilesById.clear();
        for (SharedFile file : scanned) {
            localFilesById.putIfAbsent(file.getFileId(), file);
        }
    }

    private void verifyAndAddPeer(PeerInfo peer) {
        if (peer.getNodeId().equals(nodeId)) {
            return;
        }
        workers.submit(() -> {
            if (!TcpClient.hello(peer, secretHash)) {
                return;
            }
            PeerInfo old = peers.putIfAbsent(peer.getNodeId(), peer);
            if (old == null) {
                firePeersChanged();
            } else {
                old.markSeen();
            }
            fetchListFromPeer(peer);
        });
    }

    private void fetchListFromPeer(PeerInfo peer) {
        workers.submit(() -> {
            try {
                for (RemoteFile file : TcpClient.listFiles(peer, secretHash)) {
                    addRemoteFile(peer, file.getFileId(), file.getSize(), file.getChunkCount(), file.getPreferredName());
                }
            } catch (IOException ignored) {
            }
        });
    }

    private void addRemoteFile(PeerInfo peer, String fileId, long size, int chunkCount, String fileName) {
        if (localFilesById.containsKey(fileId)) {
            return;
        }
        RemoteFile remoteFile = remoteFiles.computeIfAbsent(fileId, id -> new RemoteFile(id, size, chunkCount));
        remoteFile.addSource(peer, fileName);
        fireFilesChanged();
    }

    private void firePeersChanged() {
        List<PeerInfo> snapshot = new ArrayList<>(peers.values());
        snapshot.sort((a, b) -> a.getDisplayAddress().compareToIgnoreCase(b.getDisplayAddress()));
        for (ServiceListener listener : listeners) {
            listener.peersChanged(snapshot);
        }
    }

    private void fireFilesChanged() {
        List<RemoteFile> snapshot = new ArrayList<>(remoteFiles.values());
        snapshot.sort((a, b) -> a.getPreferredName().compareToIgnoreCase(b.getPreferredName()));
        for (ServiceListener listener : listeners) {
            listener.filesChanged(snapshot);
        }
    }

    private void fireTransferUpdated(TransferInfo transfer) {
        for (ServiceListener listener : listeners) {
            listener.transferUpdated(transfer);
        }
    }

    private void status(String status) {
        for (ServiceListener listener : listeners) {
            listener.statusChanged(status);
        }
    }
}
