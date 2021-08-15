package oystr.actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Scheduler;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.typesafe.config.Config;
import org.asynchttpclient.Response;
import org.asynchttpclient.proxy.ProxyServer;
import oystr.models.Peer;
import oystr.models.PeerState;
import oystr.models.messages.*;
import oystr.services.HttpClient;
import oystr.services.Services;
import play.Logger;
import play.libs.Json;
import scala.concurrent.ExecutionContext;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static oystr.models.PeerState.*;

public class PeersRegistryActor extends AbstractActor {
    private final HttpClient<Response> http;

    private final Cache<String, Peer> cache;

    private final String loadBalancerUrl;

    private final Logger.ALogger logger;

    private final String checkUrl;

    private Map<String, Peer> luminatiPeers;

    public PeersRegistryActor(Services services, HttpClient<Response> http) {
        Config conf = services.conf();
        this.loadBalancerUrl = conf.getString("oplb.url");
        this.http = http;
        this.cache = Caffeine
            .newBuilder()
            .maximumSize(200)
            .build();
        this.logger = services.logger("application");

        Duration healthCheckDelay = conf.getDuration("oxy.health-check.delay");
        Duration healthCheckInterval = conf.getDuration("oxy.health-check.interval");
        Duration discoveryDelay = conf.getDuration("oxy.discovery.delay");
        Duration discoveryInterval = conf.getDuration("oxy.discovery.interval");
        this.checkUrl = conf.getString("oxy.health-check.url");
        Integer threadPoolSize = conf.getInt("oxy.thread-pool.size");

        ExecutionContext ctx = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(threadPoolSize));

        Scheduler scheduler = services.sys().scheduler();
        scheduler.schedule(
            healthCheckDelay,
            healthCheckInterval,
            getSelf(),
            new HealthCheck(),
            ctx,
            ActorRef.noSender()
        );

        scheduler.schedule(
            discoveryDelay,
            discoveryInterval,
            getSelf(),
            new Discovery(),
            ctx,
            ActorRef.noSender()
        );

        if(conf.hasPath("luminati.address")) {
            String luminatiAddress = conf.getString("luminati.address");
            Peer default1 = Peer.builder().name("default1").host(luminatiAddress).port(24000).build();
            Peer default2 = Peer.builder().name("default2").host(luminatiAddress).port(24001).build();
            luminatiPeers = new HashMap<>();
            luminatiPeers.put(default1.toHash(), default1);
            luminatiPeers.put(default2.toHash(), default2);
        }
    }

    private void healthCheck() {
        ConcurrentMap<String, Peer> peers = cache.asMap();
        peers.forEach((key, value) -> {
            ProxyServer proxy = new ProxyServer.Builder(value.getHost(), value.getPort()).build();

            try {
               Response res = http
                    .head(checkUrl, proxy)
                    .get(10, TimeUnit.SECONDS);

                logger.debug(String.format("healthcheck finished for %s with %d and no errors.", value, res.getStatusCode()));

                value.setLastHealthCheck(LocalDateTime.now());
                value.setState(RUNNING);
                cache.put(key, value);
            } catch (Exception e) {
                logger.debug(String.format("[%s] healthcheck finished for %s with an error: '%s'.", e.getClass().getSimpleName(), value, e.getMessage()));

                value.setLastHealthCheck(LocalDateTime.now());
                switch (value.getState()) {
                    case UNKNOWN:
                    case RUNNING:
                        value.setState(PENDING);
                        cache.put(key, value);
                        break;
                    case PENDING:
                        value.setState(FAILING);
                        cache.put(key, value);
                        break;
                    case FAILING:
                        value.setState(DISABLED);
                        cache.put(key, value);
                        break;
                    case DISABLED:
                        logger.debug("Peer removed: " + value);
                        cache.invalidate(key);
                }
            }
        });
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(Discovery.class, req -> {
                // TODO: It's our internal use case. Since OpenVPN knows who are connected, we may discover clients automatically.
                // TODO: We are sharing the /tmp directory of both containers.
                String[] cmd = {"/bin/sh", "-c", "cat /tmp/openvpn-status.log | grep \"192.168.255\" | awk -F\",\" '{print $1 \" \" $2}'"};
                Process p = Runtime.getRuntime().exec(cmd);
                p.waitFor();

                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while((line = reader.readLine()) != null) {
                    String[] raw = line.split(" ");
                    if(raw.length < 2) {
                        continue;
                    }

                    String host = raw[0];
                    String name = raw[1];
                    Peer peer = Peer
                        .builder()
                        .serviceId(String.format("proxy-%s", name))
                        .host(host)
                        .port(8888)
                        .name(name)
                        .registeredAt(LocalDateTime.now())
                        .state(UNKNOWN)
                        .build();

                    Peer existingPeer = cache.getIfPresent(peer.toHash());
                    if(existingPeer == null || existingPeer.getState() == DISABLED) {
                        logger.info(String.format("Discovered: %s", peer));
                        cache.put(peer.toHash(), peer);
                    }
                }
            })
            .match(HealthCheck.class, req -> {
                if(cache.estimatedSize() == 0) {
                    return;
                }

                logger.debug("running health check with " + cache.estimatedSize() + " registered peers (even those not running)");
                healthCheck();
            })
            .match(AddPeerRequest.class, req -> {
                Peer peer = req.getPeer();
                cache.put(peer.toHash(), peer);
            })
            .match(FindPeersRequest.class, req -> {
                Boolean onlyRunning = req.getOnlyRunning();
                Boolean random      = req.getRandom();

                if(random) {
                    getSender().tell(findRandom(onlyRunning), getSelf());
                }

                JsonNode res = onlyRunning ? findAllActive() : findAll();
                getSender().tell(Optional.of(res), getSelf());
            })
            .match(DeletePeerRequest.class, req -> {
                Boolean deleteAll = req.getDeleteAll();
                if(deleteAll) {
                    flush();
                } else {
                    delete(req);
                }
                getSender().tell(true, getSelf());
            })
            .match(MetadataRequest.class, req -> {
                if(req.getExecutionId().isPresent()) {
                    String execution = req.getExecutionId().get();
                    getContext()
                        .findChild("metrics-actor")
                        .ifPresent(actor -> actor.tell(execution, getSelf()));
                }
                getSender().tell(loadBalancerUrl, getSelf());
            })
            .matchAny(req -> logger.debug(req.toString()))
            .build();
    }

    public JsonNode findAll() {
        return cache.estimatedSize() == 0 && luminatiPeers != null ?
            Json.toJson(luminatiPeers.values()) :
            Json.toJson(cache.asMap().values());
    }

    public JsonNode findAllActive() {
        List<Peer> listServices = getRunningPeers();
        return listServices.isEmpty() && luminatiPeers != null ?
            Json.toJson(luminatiPeers.values()) :
            Json.toJson(listServices);
    }

    @SuppressWarnings("WrapperTypeMayBePrimitive")
    public Optional<JsonNode> findRandom(Boolean onlyRunning) {
        Collection<Peer> peers = cache.estimatedSize() == 0 && luminatiPeers != null ? luminatiPeers.values() : cache.asMap().values();
        List<Peer> listServices =  onlyRunning ? getRunningPeers() : new ArrayList<>(peers);

        if(listServices.isEmpty()) {
            return Optional.empty();
        }

        Integer size = listServices.size();
        Integer idx  = new Random().nextInt(size);
        return Optional.of(Json.toJson(listServices.get(idx)));
    }

    private List<Peer> getRunningPeers()
    {
        return cache.asMap()
            .values()
            .stream()
            .filter(s -> s.getState().equals(PeerState.RUNNING))
            .collect(Collectors.toList());
    }

    public void flush() {
        cache.invalidateAll();
    }

    public void delete(DeletePeerRequest req) {
        String payload = String.format("%s-%s-%s", req.getServiceId(), req.getHost(), req.getPort());
        String key = Base64.getEncoder().encodeToString(payload.getBytes());
        cache.invalidate(key);
    }
}