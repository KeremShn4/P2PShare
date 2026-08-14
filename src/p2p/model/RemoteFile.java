package p2p.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RemoteFile {
    private final String fileId;
    private final long size;
    private final int chunkCount;
    private final Map<String, String> namesByPeer = new LinkedHashMap<>();
    private final Map<String, PeerInfo> sourcesByNode = new LinkedHashMap<>();

    public RemoteFile(String fileId, long size, int chunkCount) {
        this.fileId = fileId;
        this.size = size;
        this.chunkCount = chunkCount;
    }

    public synchronized void addSource(PeerInfo peer, String fileName) {
        sourcesByNode.put(peer.getNodeId(), peer);
        String oldName = namesByPeer.get(peer.getNodeId());
        if (oldName == null || oldName.equals(fileName)) {
            namesByPeer.put(peer.getNodeId(), fileName);
        } else if (!oldName.contains(fileName)) {
            namesByPeer.put(peer.getNodeId(), oldName + ", " + fileName);
        }
    }

    public String getFileId() {
        return fileId;
    }

    public long getSize() {
        return size;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public synchronized String getPreferredName() {
        if (namesByPeer.isEmpty()) {
            return fileId;
        }
        return namesByPeer.values().iterator().next();
    }

    public synchronized List<PeerInfo> getSources() {
        return Collections.unmodifiableList(new ArrayList<>(sourcesByNode.values()));
    }

    public synchronized int getSourceCount() {
        return sourcesByNode.size();
    }

    public synchronized String getKnownNames() {
        return String.join(", ", namesByPeer.values());
    }
}
