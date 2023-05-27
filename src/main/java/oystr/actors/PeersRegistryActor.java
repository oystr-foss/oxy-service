package oystr.actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Scheduler;
import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.config.Config;
import org.asynchttpclient.Response;
import org.asynchttpclient.proxy.ProxyServer;
import oystr.models.Peer;
import oystr.models.PeerState;
import oystr.models.messages.*;
import oystr.services.CacheClient;
import oystr.services.HttpClient;
import oystr.services.Services;
import play.Logger;
import play.libs.Json;
import scala.concurrent.ExecutionContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static oystr.models.PeerState.*;


public class PeersRegistryActor extends AbstractActor {
    private final HttpClient<Response> http;

    private final CacheClient cache;

    private final String loadBalancerUrl;

    private final Logger.ALogger logger;

    private final String checkUrl;

    private final Boolean isLeader;

    private final ExecutorService executor;

    private Map<String, Peer> luminatiPeers;

    @SuppressWarnings("WrapperTypeMayBePrimitive")
    public PeersRegistryActor(Services services, HttpClient<Response> http, CacheClient cacheClient) {
        Config conf = services.conf();

        this.loadBalancerUrl = conf.getString("oplb.url");
        this.http = http;
        this.cache = cacheClient;
        this.logger = services.logger("application");
        this.checkUrl = conf.getString("oxy.health-check.url");
        String role = conf.getString("oxy.role");
        this.isLeader = role.equalsIgnoreCase("leader");

        // If the leader is starting, then flush the cache to avoid showing stale/offline peers.
        if(isLeader) {
            cache.flush();
        }

        Duration healthCheckDelay = conf.getDuration("oxy.health-check.delay");
        Duration healthCheckInterval = conf.getDuration("oxy.health-check.interval");
        Duration discoveryDelay = conf.getDuration("oxy.discovery.delay");
        Duration discoveryInterval = conf.getDuration("oxy.discovery.interval");
        Duration snapshotDelay = conf.getDuration("oxy.snapshot.delay");
        Duration snapshotInterval = conf.getDuration("oxy.snapshot.interval");

        Integer threadPoolSize = conf.getInt("oxy.thread-pool.size");
        this.executor = Executors.newFixedThreadPool(threadPoolSize);
        ExecutionContext ctx = ExecutionContext.fromExecutor(executor);

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

        scheduler.schedule(
            snapshotDelay,
            snapshotInterval,
            getSelf(),
            new Snapshot(),
            getContext().getDispatcher(),
            ActorRef.noSender()
        );

        if(conf.hasPath("luminati.address")) {
            String luminatiAddress = conf.getString("luminati.address");
            Peer default1 = Peer.builder().serviceId("default1").name("default1").host(luminatiAddress).port(24000).build();
            Peer default2 = Peer.builder().serviceId("default2").name("default2").host(luminatiAddress).port(24001).build();
            luminatiPeers = new HashMap<>();
            luminatiPeers.put(default1.toHash(), default1);
            luminatiPeers.put(default2.toHash(), default2);
        }
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(Discovery.class, req -> autoDiscovery())
            .match(Snapshot.class, req -> {
                if(isLeader) {
                    cache.takeSnapshot();
                }
            })
            .match(HealthCheck.class, req -> healthCheck())
            .match(AddPeerRequest.class, req -> cache.add(req.getPeer()))
            .match(FindHiddenPeerRequest.class, req -> {
                Peer nullablePeer = cache.getIfPresent(req.getAccount());
                Optional<Peer> maybePeer = Optional.ofNullable(nullablePeer);
                JsonNode res = Json.toJson(maybePeer);
                getSender().tell(res, getSender());
            })
            .match(FindPeersRequest.class, req -> {
                Boolean onlyRunning = req.getOnlyRunning();
                JsonNode res = onlyRunning ? findAllActive() : findAll();
                getSender().tell(res, getSelf());
            })
            .match(FindSnapshotRequest.class, req -> {
                List<Peer> data = cache.findSnapshot(req.getDate());
                JsonNode res = Json.toJson(data);
                getSender().tell(res, getSender());
            })
            .match(FindSnapshotKeysRequest.class, req -> {
                List<String> data = cache.findAllSnapshots();
                JsonNode res = Json.toJson(data);
                getSender().tell(res, getSender());
            })
            .match(TaintPeerRequest.class, req -> {
                String key = Base64.getEncoder().encodeToString(req.getServiceId().getBytes());
                Peer peer = cache.getIfPresent(key);
                if(peer != null) {
                    PeerState state = peer.getState().equals(AVOID) ? UNKNOWN : AVOID;
                    peer.setState(state);
                    cache.add(peer);
                }
                getSender().tell(true, getSelf());
            })
            .match(DeletePeerRequest.class, req -> {
                Boolean deleteAll = req.getDeleteAll();
                if(deleteAll) {
                    cache.flush();
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
                        .ifPresent(actor -> actor.tell(execution, ActorRef.noSender()));
                }
                getSender().tell(loadBalancerUrl, getSelf());
            })
            .matchAny(req -> logger.debug(req.toString()))
            .build();
    }

    private JsonNode findAll() {
        return cache.size() == 0 && luminatiPeers != null ?
            Json.toJson(luminatiPeers.values()) :
            Json.toJson(cache.findAll());
    }

    private JsonNode findAllActive() {
        List<Peer> listServices = cache.findAllRunning();

        return listServices.isEmpty() && luminatiPeers != null ?
            Json.toJson(luminatiPeers.values()) :
            Json.toJson(listServices);
    }

    private void delete(DeletePeerRequest req) {
        String key = Base64.getEncoder().encodeToString(req.getServiceId().getBytes());
        cache.remove(key);
    }

    private void autoDiscovery() throws IOException, InterruptedException {
        if (!isLeader) {
            return;
        }
        // TODO: It's our internal use case. Since OpenVPN knows who are connected, we may discover clients automatically.
        // TODO: We are sharing the /tmp directory of both containers.
        String[] cmd = {"/bin/sh", "-c", "cat /tmp/openvpn-status.log | grep \"192.168.\" | awk -F\",\" '{print $1 \" \" $2}'"};
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

            if(name.equalsIgnoreCase("oystr-sa")) {
                continue;
            }

            name = name.replaceAll("[^\\d\\w]+", "");
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
                cache.add(peer);
            }
        }
    }

    private void healthCheck() {
        List<Peer> all = cache
            .findAll()
            .stream()
            .filter(p -> !p.getState().equals(AVOID))
            .collect(Collectors.toList());

        if(!isLeader || all.size() == 0) {
            return;
        }

        logger.debug(String.format("[SUCCESS] running health check with %d registered peers (except for those tainted)", all.size()));
        all.forEach(value -> {
            if(value.getState().equals(AVOID)) {
                return;
            }
            ProxyServer proxy = new ProxyServer.Builder(value.getHost(), value.getPort()).build();

            http
                .head(checkUrl, proxy, executor)
                .whenCompleteAsync((res, err) -> {
                    if(err != null || res == null) {
                        if(res == null) {
                            logger.error(String.format("[%s] healthcheck finished for %s with an ExecutionException (host unavailable), TimeoutException or InterruptedException.", "ERROR", value));
                        }

                        if(err != null) {
                            logger.error(String.format("[%s] healthcheck finished for %s with an error: '%s'.", err.getClass().getSimpleName(), value, err.getMessage()));
                        }

                        value.setLastHealthCheck(LocalDateTime.now());
                        switch (value.getState()) {
                            case UNKNOWN:
                            case RUNNING:
                                value.setState(PENDING);
                                cache.add(value);
                                break;
                            case PENDING:
                                value.setState(FAILING);
                                cache.add(value);
                                break;
                            case FAILING:
                                value.setState(DISABLED);
                                cache.add(value);
                                break;
                            case DISABLED:
                                logger.debug(String.format("[%s] Peer removed: %s", getClass().getSimpleName(), value));
                                cache.remove(value.toHash());
                        }
                        return;
                    }

                    logger.debug(String.format("[%s] healthcheck finished for %s with %d and no errors.", getClass().getSimpleName(), value, res.getStatusCode()));

                    value.setLastHealthCheck(LocalDateTime.now());
                    value.setState(RUNNING);
                    cache.add(value);
                }, executor);
        });
    }
}