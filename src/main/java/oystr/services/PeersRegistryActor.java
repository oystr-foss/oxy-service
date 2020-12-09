package oystr.services;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.typesafe.config.Config;
import org.asynchttpclient.proxy.ProxyServer;
import oystr.models.Peer;
import oystr.models.PeerState;
import oystr.models.RequestMetadata;
import oystr.models.dao.RequestMetadataRepository;
import oystr.models.messages.*;
import play.Logger;
import play.libs.Json;
import play.libs.ws.WSResponse;
import scala.concurrent.ExecutionContext;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static oystr.models.PeerState.*;

public class PeersRegistryActor extends AbstractActor {
    private final HttpClient<WSResponse> http;

    private final Cache<String, Peer> cache;

    private final String loadBalancerUrl;

    private final Logger.ALogger logger;

    private final RequestMetadataRepository repo;

    private final String healthCheckUrl;

    private Map<String, Peer> luminatiPeers;

    public PeersRegistryActor(Services services, HttpClient<WSResponse> http, RequestMetadataRepository repo) {
        Config conf = services.conf();
        this.repo = repo;
        this.loadBalancerUrl = conf.getString("oplb.url");
        this.http = http;
        this.cache = Caffeine.newBuilder()
            .maximumSize(100)
            .build();
        this.logger = services.logger("application");

        Duration delay = conf.getDuration("oxy.health-check.delay");
        Duration interval = conf.getDuration("oxy.health-check.interval");
        this.healthCheckUrl = conf.getString("oxy.health-check.url");

        services
            .sys()
            .scheduler()
            .schedule(
                delay,
                interval,
                getSelf(),
                new HealthCheck(),
                ExecutionContext.global(),
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
                http.get(healthCheckUrl, proxy).get(5, TimeUnit.SECONDS);
                value.setLastHealthCheck(LocalDateTime.now());
                value.setState(RUNNING);
                cache.put(key, value);
            } catch (Exception e) {
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
            .match(HealthCheck.class, req -> {
                if(cache.estimatedSize() == 0) {
                    return;
                }

                logger.debug("starting health check with " + cache.estimatedSize() + " peers registered (even those not running)");
                healthCheck();
                logger.debug("finished health check with " + cache.estimatedSize() + " peers registered (even those not running)");
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
                    UUID id = UUID.randomUUID();
                    RequestMetadata metadata = RequestMetadata.builder().execution(execution).id(id).build();
                    repo.add(metadata);
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