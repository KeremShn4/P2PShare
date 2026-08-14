package p2p.model;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class AppConfig {
    private final Path sharedFolder;
    private final String secret;
    private final int tcpPort;
    private final Set<String> excludedFolders;

    public AppConfig(Path sharedFolder, String secret, int tcpPort, Set<String> excludedFolders) {
        this.sharedFolder = sharedFolder;
        this.secret = secret;
        this.tcpPort = tcpPort;
        this.excludedFolders = new HashSet<>(excludedFolders);
    }

    public Path getSharedFolder() {
        return sharedFolder;
    }

    public String getSecret() {
        return secret;
    }

    public int getTcpPort() {
        return tcpPort;
    }

    public Set<String> getExcludedFolders() {
        return Collections.unmodifiableSet(excludedFolders);
    }
}
