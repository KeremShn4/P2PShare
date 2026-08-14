package p2p.model;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Objects;

public class PeerInfo {
    private final String nodeId;
    private final String hostName;
    private final InetAddress address;
    private final int port;
    private Instant lastSeen;

    public PeerInfo(String nodeId, String hostName, InetAddress address, int port) {
        this.nodeId = nodeId;
        this.hostName = hostName;
        this.address = address;
        this.port = port;
        this.lastSeen = Instant.now();
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getHostName() {
        return hostName;
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public void markSeen() {
        lastSeen = Instant.now();
    }

    public String getDisplayAddress() {
        return address.getHostAddress() + ":" + port;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PeerInfo)) {
            return false;
        }
        PeerInfo other = (PeerInfo) obj;
        return Objects.equals(nodeId, other.nodeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId);
    }
}
