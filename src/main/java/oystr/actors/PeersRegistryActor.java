package oystr.actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Scheduler;
import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.config.Config;
import org.asynchttpclient.Response;
import org.asynchttpclient.proxy.ProxyServer;
import oystr.models.Peer;
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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static oystr.models.PeerState.*;


public class PeersRegistryActor extends AbstractActor {
    private final HttpClient<Response> http;

    private final CacheClient cache;

    private final String loadBalancerUrl;

    private final Logger.ALogger logger;

    private final String checkUrl;

    private final Boolean isLeader;

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

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(Discovery.class, this::autoDiscovery)
            .match(HealthCheck.class, req -> healthCheck())
            .match(AddPeerRequest.class, req -> cache.add(req.getPeer()))
            .match(FindPeersRequest.class, req -> {
                Boolean onlyRunning = req.getOnlyRunning();
                JsonNode res = onlyRunning ? findAllActive() : findAll();
                getSender().tell(Optional.of(res), getSelf());
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
                        .ifPresent(actor -> actor.tell(execution, getSelf()));
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
        String payload = String.format("%s-%s-%s", req.getServiceId(), req.getHost(), req.getPort());
        String key = Base64.getEncoder().encodeToString(payload.getBytes());
        cache.remove(key);
    }

    private void autoDiscovery(Discovery req) throws IOException, InterruptedException {
        if (!isLeader) {
            return;
        }
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
                cache.add(peer);
            }
        }
    }

    private void healthCheck() {
        if(!isLeader || cache.size() == 0) {
            return;
        }
        logger.debug("running health check with " + cache.size() + " registered peers (even those not running)");

        Collection<Peer> peers = cache.findAll();
        peers.forEach(value -> {
            ProxyServer proxy = new ProxyServer.Builder(value.getHost(), value.getPort()).build();

            try {
                Response res = http
                        .head(checkUrl, proxy)
                        .get(10, TimeUnit.SECONDS);

                logger.debug(String.format("healthcheck finished for %s with %d and no errors.", value, res.getStatusCode()));

                value.setLastHealthCheck(LocalDateTime.now());
                value.setState(RUNNING);
                cache.add(value);
            } catch (Exception e) {
                logger.debug(String.format("[%s] healthcheck finished for %s with an error: '%s'.", e.getClass().getSimpleName(), value, e.getMessage()));

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
                        logger.debug("Peer removed: " + value);
                        cache.remove(value.toHash());
                }
            }
        });
    }
}