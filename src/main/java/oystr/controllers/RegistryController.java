package oystr.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import oystr.akka.Inquire;
import oystr.akka.RootActors;
import oystr.models.Peer;
import oystr.models.PeerState;
import oystr.models.messages.AddPeerRequest;
import oystr.models.messages.DeletePeerRequest;
import oystr.models.messages.FindPeersRequest;
import oystr.models.messages.MetadataRequest;
import oystr.services.Services;
import oystr.validators.PojoValidator;
import play.Logger;
import play.libs.Json;
import play.mvc.*;

import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class RegistryController extends Controller {
    private final Logger.ALogger logger;

    private final PojoValidator validator;

    private final Services services;

    private final RootActors actors;

    private final String executionHeader;

    @Inject
    public RegistryController(Services services, RootActors actors) {
        this.executionHeader = services.conf().getString("oplb.execution-header");
        this.actors = actors;
        this.services = services;
        this.validator = PojoValidator.getInstance();
        this.logger = services.logger("application");
    }

    public CompletionStage<Result> loadBalancer(Http.Request request) {
        Optional<String> execution = request.header(executionHeader);
        MetadataRequest req = MetadataRequest.builder().executionId(execution).build();

        req.getExecutionId().ifPresent(exec -> {
            // Fire and forget
            Inquire.inquire(actors.metricsActor(), exec, services.sys());
        });

        return Inquire
            .inquire(actors.peersRegistryActor(), req, services.sys())
            .handleAsync((res, error) -> {
                if(error != null) {
                    return Results.internalServerError();
                }
                return Results.ok((String) res);
            });
    }

    @BodyParser.Of(BodyParser.Json.class)
    public CompletableFuture<Result> register(Http.Request request) {
        JsonNode json = request.body().asJson();
        Peer peer = Json.fromJson(json, Peer.class);
        List<ObjectNode> violations = validate(peer);

        if(!violations.isEmpty()) {
            return CompletableFuture.completedFuture(Results.badRequest(violations.toString()));
        }

        peer.setRegisteredAt(LocalDateTime.now(services.clock()));
        peer.setState(PeerState.UNKNOWN);
        peer.setLastHealthCheck(null);

        AddPeerRequest req = AddPeerRequest.builder().peer(peer).build();
        Inquire
            .inquire(actors.peersRegistryActor(), req, services.sys())
            .whenComplete((res, throwable) -> {
                if(throwable != null) {
                    throwable.printStackTrace();
                }
            });

        logger.debug(peer.toString());
        return CompletableFuture.completedFuture(Results.ok(Json.toJson(peer)));
    }

    public CompletionStage<Result> findAll() {
        return find(false, false);
    }

    public CompletionStage<Result> findAllActive() {
        return find(true, false);
    }

    public CompletionStage<Result> flush() {
        DeletePeerRequest req = DeletePeerRequest.builder().build();
        return delete(req);
    }

    public CompletionStage<Result> delete(String serviceId, String host, Integer port) {
        DeletePeerRequest req = DeletePeerRequest
            .builder()
            .serviceId(serviceId)
            .host(host)
            .port(port)
            .deleteAll(false)
            .build();
        return delete(req);
    }

    public CompletionStage<Result> findRandom() {
        return find(false, true);
    }

    private <T> List<ObjectNode> validate(T data) {
        return validator.validate(data);
    }

    public CompletionStage<Result> find(Boolean onlyRunning, Boolean random) {
        FindPeersRequest req = FindPeersRequest
            .builder()
            .onlyRunning(onlyRunning)
            .random(random)
            .build();

        return Inquire
            .inquire(actors.peersRegistryActor(), req, services.sys())
            .handleAsync((res, throwable) -> {
                if (throwable != null) {
                    return Results.internalServerError(throwable.getMessage());
                }
                Optional<JsonNode> r = (Optional<JsonNode>) res;
                return r.map(Results::ok).orElse(Results.notFound());
            });
    }

    public CompletionStage<Result> delete(DeletePeerRequest req) {
        return Inquire
            .inquire(actors.peersRegistryActor(), req, services.sys())
            .handleAsync((res, throwable) -> {
                if (throwable != null) {
                    return Results.internalServerError(throwable.getMessage());
                }
                return Results.noContent();
            });
    }
}
