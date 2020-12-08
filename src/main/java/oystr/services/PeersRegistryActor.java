package oystr.services;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import oystr.models.Peer;
import oystr.models.PeerState;
import oystr.models.RequestMetadata;
import oystr.models.dao.RequestMetadataRepository;
import oystr.models.messages.*;
import play.Logger;
import play.libs.Json;
import play.libs.ws.WSResponse;
import org.asynchttpclient.proxy.ProxyServer;
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

    public PeersRegistryActor(Services services, HttpClient<WSResponse> http, RequestMetadataRepository repo) {
        this.repo = repo;
        this.loadBalancerUrl = services.conf().getString("oplb.url");
        this.http = http;
        this.cache = Caffeine.newBuilder()
            .maximumSize(100)
            .build();
        this.logger = services.logger("application");

        Duration interval = services.conf().getDuration("oxy.health-check");
        services
            .sys()
            .scheduler()
            .schedule(
                Duration.ofSeconds(15),
                interval,
                getSelf(),
                new HealthCheck(),
                ExecutionContext.global(),
                ActorRef.noSender()
            );
    }

    public void healthCheck() {
        ConcurrentMap<String, Peer> peers = cache.asMap();
        peers.forEach((key, value) -> {
            ProxyServer proxy = new ProxyServer.Builder(value.getHost(), value.getPort()).build();

            try {
                http.get("https://ipinfo.io/", proxy).get(5, TimeUnit.SECONDS);
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
        return Json.toJson(cache.asMap().values());
    }

    public JsonNode findAllActive() {
        List<Peer> listServices = getRunningPeers();
        return Json.toJson(listServices);
    }

    public Optional<JsonNode> findRandom(Boolean onlyRunning) {
        List<Peer> listServices =  onlyRunning ? getRunningPeers() : new ArrayList<>(cache.asMap().values());

        if(listServices.isEmpty()) {
            return Optional.empty();
        }

        Integer size = listServices.size();
        Integer idx  = new Random().nextInt(size);
        return Optional.of(Json.toJson(listServices.get(idx)));
    }

    public List<Peer> getRunningPeers()
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