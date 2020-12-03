package oystr.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import oystr.models.Service;
import oystr.services.Services;
import oystr.validators.PojoValidator;
import play.Logger;
import play.libs.Json;
import play.mvc.*;

import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class RegistryController extends Controller {
    private final Logger.ALogger logger;

    private final PojoValidator validator;
    private final Services services;

    @Inject
    public RegistryController(Services services) {
        this.services = services;
        this.validator = PojoValidator.getInstance();
        this.logger = services.logger("application");
    }

    @BodyParser.Of(BodyParser.Json.class)
    public CompletableFuture<Result> register(Http.Request request) {
        JsonNode json = request.body().asJson();
        Service service = Json.fromJson(json, Service.class);
        List<ObjectNode> violations = validate(service);

        if(!violations.isEmpty()) {
            return CompletableFuture.completedFuture(Results.badRequest(violations.toString()));
        }

        service.setRegisteredAt(LocalDateTime.now(services.clock()));
        logger.debug(service.toString());
        return CompletableFuture.completedFuture(Results.ok("Hello World"));
    }

    private <T> List<ObjectNode> validate(T data) {
        return validator.validate(data);
    }
}
