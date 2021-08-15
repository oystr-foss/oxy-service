package oystr.services.impl;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import oystr.models.Peer;
import oystr.models.PeerState;
import oystr.services.CacheClient;
import oystr.services.Codec;
import oystr.services.Services;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class LettuceCacheClient implements CacheClient {
    private final RedisCommands<String, Peer> syncCommands;
    private final String redisBaseKey = "peers";

    @Inject
    public LettuceCacheClient(Codec<Peer> codec, Services services) {
        String redisUrl = services.conf().getString("redis.url");
        RedisClient redisClient = RedisClient.create(redisUrl);
        StatefulRedisConnection<String, Peer> connection = redisClient.connect(codec);
        syncCommands = connection.sync();
    }

    @Override
    public void add(Peer peer) {
        Map<String, Peer> map = new HashMap<>();
        map.put(peer.toHash(), peer);
        syncCommands.hmset(redisBaseKey, map);
    }

    @Override
    public Peer getIfPresent(String key) {
        if(syncCommands.hexists(redisBaseKey, key)) {
            return syncCommands.hget(redisBaseKey, key);
        }
        return null;
    }

    @Override
    public void remove(String key) {
        syncCommands.hdel(redisBaseKey, key);
    }

    @Override
    public List<Peer> findAll() {
        Map<String, Peer> peers = syncCommands.hgetall(redisBaseKey);

        return new ArrayList<>(peers.values());
    }

    public Long size() {
        return syncCommands.hlen(redisBaseKey);
    }

    @Override
    public List<Peer> findAllRunning() {
        return findAll()
            .stream()
            .filter(p -> p.getState().equals(PeerState.RUNNING))
            .collect(Collectors.toList());
    }

    @Override
    public void flush() {
        syncCommands.flushdb();
    }
}
