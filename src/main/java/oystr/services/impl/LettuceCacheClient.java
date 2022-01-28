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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class LettuceCacheClient implements CacheClient {
    private final RedisCommands<String, Peer> syncCommands;
    private final String redisBaseKey = "peers";
    private final Integer dbNumber;

    @Inject
    public LettuceCacheClient(Codec<Peer> codec, Services services) throws Exception {
        String redisUrl = services.conf().getString("redis.url");
        this.dbNumber = parseDbNumber(redisUrl);

        RedisClient redisClient = RedisClient.create(redisUrl);
        StatefulRedisConnection<String, Peer> connection = redisClient.connect(codec);
        syncCommands = connection.sync();
    }

    private Integer parseDbNumber(String url) throws Exception {
        Pattern pattern = Pattern.compile("redis://[^/]+/(\\d+)");
        Matcher matcher = pattern.matcher(url);

        if(!matcher.find()) {
            return 0;
        }

        String match = matcher.group(1);

        try {
            return Integer.parseInt(match);
        } catch (NumberFormatException e) {
            throw new Exception("Invalid Redis database!", e);
        }
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
        return find(redisBaseKey);
    }

    @Override
    public List<String> findAllSnapshots() {
        syncCommands.select(dbNumber + 1);
        List<String> keys = syncCommands
            .scan()
            .getKeys()
            .stream()
            .filter(k -> k.contains("snapshot"))
            .map(k -> k.replace("peers-", "").replace("-snapshot", ""))
            .collect(Collectors.toList());
        syncCommands.select(dbNumber);

        return keys;
    }

    @Override
    public List<Peer> findSnapshot(String prefix) {
        syncCommands.select(dbNumber + 1);
        List<Peer> snapshot = find(String.format("peers-%s-snapshot", prefix));
        syncCommands.select(dbNumber);

        return snapshot;
    }

    private List<Peer> find(String key) {
        Map<String, Peer> peers = syncCommands.hgetall(key);

        return new ArrayList<>(peers.values());
    }

    @Override
    public void takeSnapshot() {
        Map<String, Peer> map = new HashMap<>();
        findAll().forEach(peer -> map.put(peer.toHash(), peer));

        if(map.isEmpty()) {
            return;
        }

        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        String key = String.format("%s-%s-snapshot", redisBaseKey, date);
        Duration ttl = Duration.ofDays(2);

        syncCommands.select(dbNumber + 1);
        syncCommands.hmset(key, map);
        syncCommands.expire(key, ttl);
        syncCommands.select(dbNumber);
    }

    @Override
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
