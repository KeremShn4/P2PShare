package p2p.net;

import java.util.List;

import p2p.model.PeerInfo;
import p2p.model.RemoteFile;
import p2p.model.TransferInfo;

public interface ServiceListener {
    void peersChanged(List<PeerInfo> peers);

    void filesChanged(List<RemoteFile> files);

    void transferUpdated(TransferInfo transfer);

    void statusChanged(String status);
}
