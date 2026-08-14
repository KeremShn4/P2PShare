package p2p;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import p2p.model.AppConfig;
import p2p.model.PeerInfo;
import p2p.model.RemoteFile;
import p2p.model.TransferInfo;
import p2p.net.P2PService;
import p2p.net.ServiceListener;

public class HeadlessNode implements ServiceListener {
    private final P2PService service = new P2PService();
    private final boolean autoDownload;
    private final Set<String> requestedFiles = ConcurrentHashMap.newKeySet();

    public HeadlessNode(boolean autoDownload) {
        this.autoDownload = autoDownload;
        service.addListener(this);
    }

    public static void main(String[] args) throws Exception {
        Path sharedFolder = Path.of(value("SHARED_FOLDER", args, 0, "/shared"));
        String secret = value("SECRET", args, 1, "cse471");
        int port = Integer.parseInt(value("TCP_PORT", args, 2, "5001"));
        Set<String> excluded = parseExcluded(value("EXCLUDED_FOLDERS", args, 3, ""));
        boolean autoDownload = Boolean.parseBoolean(value("AUTO_DOWNLOAD", args, 4, "false"));

        HeadlessNode node = new HeadlessNode(autoDownload);
        Runtime.getRuntime().addShutdownHook(new Thread(node.service::stop));
        node.service.start(new AppConfig(sharedFolder, secret, port, excluded));
        System.out.println("Headless P2P node started on port " + port + " with shared folder " + sharedFolder);
        new CountDownLatch(1).await();
    }

    private static String value(String envName, String[] args, int index, String defaultValue) {
        if (args.length > index && !args[index].isBlank()) {
            return args[index];
        }
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return defaultValue;
    }

    private static Set<String> parseExcluded(String text) {
        Set<String> excluded = new HashSet<>();
        for (String item : text.split(",")) {
            String value = item.trim();
            if (!value.isEmpty()) {
                excluded.add(value);
            }
        }
        return excluded;
    }

    @Override
    public void peersChanged(List<PeerInfo> peers) {
        System.out.println("Peers: " + peers.size());
        for (PeerInfo peer : peers) {
            System.out.println(" - " + peer.getHostName() + " " + peer.getDisplayAddress());
        }
    }

    @Override
    public void filesChanged(List<RemoteFile> files) {
        System.out.println("Remote files: " + files.size());
        for (RemoteFile file : files) {
            System.out.println(" - " + file.getPreferredName() + " sources=" + file.getSourceCount()
                    + " size=" + file.getSize());
            if (autoDownload && requestedFiles.add(file.getFileId())) {
                service.download(file);
            }
        }
    }

    @Override
    public void transferUpdated(TransferInfo transfer) {
        System.out.println("Transfer: " + transfer.getFileName() + " "
                + transfer.getTransferredBytes() + "/" + transfer.getTotalBytes()
                + " " + transfer.getPercentage() + "% " + transfer.getStatus());
    }

    @Override
    public void statusChanged(String status) {
        System.out.println(status);
    }
}
