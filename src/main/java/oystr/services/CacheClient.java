package oystr.services;

import oystr.models.Peer;

import java.util.List;

public interface CacheClient {
    void add(Peer peer);

    Peer getIfPresent(String key);

    void remove(String key);

    Long size();

    List<Peer> findAll();

    List<String> findAllSnapshots();

    List<Peer> findSnapshot(String prefix);

    void takeSnapshot();

    List<Peer> findAllRunning();

    void flush();
}
