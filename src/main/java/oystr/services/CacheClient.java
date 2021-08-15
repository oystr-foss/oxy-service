package oystr.services;

import oystr.models.Peer;

import java.util.Collection;
import java.util.List;

public interface CacheClient {
    void add(Peer peer);

    Peer getIfPresent(String key);

    void remove(String key);

    Long size();

    List<Peer> findAll();

    List<Peer> findAllRunning();

    void flush();
}
