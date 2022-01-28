package oystr.actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.MonitoredResource;
import com.google.cloud.logging.*;
import com.typesafe.config.Config;
import org.asynchttpclient.Response;
import oystr.services.HttpClient;
import oystr.services.Services;
import play.Logger;
import play.libs.Json;
import scala.concurrent.ExecutionContext;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetricsCollectorActor extends AbstractActor {
    private final Clock clock;

    private final Logger.ALogger logger;

    private final HttpClient<Response> http;

    private final List<LogEntry> buffer = new ArrayList<>();

    private final MonitoredResource resource = MonitoredResource.newBuilder("global").build();

    private String apiUrl;

    private String apiHeader;

    private String apiKey;

    private Boolean enabled = false;

    private Logging stackdriver;

    public MetricsCollectorActor(Services services, HttpClient<Response> http)
        throws IOException {
        Config config = services.conf();
        this.clock = services.clock();
        this.logger = services.logger("application");
        this.http = http;

        if(config.hasPath("metrics.enabled") &&
            config.hasPath("metrics.stackdriver.key") &&
            config.hasPath("metrics.delay") &&
            config.hasPath("metrics.interval") &&
            config.hasPath("metrics.info.url") &&
            config.hasPath("metrics.info.api-header") &&
            config.hasPath("metrics.info.api-key")) {
            this.apiUrl = config.getString("metrics.info.url");
            this.apiHeader = config.getString("metrics.info.api-header");
            this.apiKey = config.getString("metrics.info.api-key");

            this.enabled = config.getBoolean("metrics.enabled");
            String key = config.getString("metrics.stackdriver.key");

            InputStream is = new FileInputStream(key);
            ServiceAccountCredentials credentials = ServiceAccountCredentials.fromStream(is);
            this.stackdriver = LoggingOptions
                .newBuilder()
                .setCredentials(credentials)
                .build()
                .getService();

            Duration delay = config.getDuration("metrics.delay");
            Duration interval = config.getDuration("metrics.interval");
            services
                .sys()
                .scheduler()
                .schedule(
                    delay,
                    interval,
                    getSelf(),
                    new Flush(),
                    ExecutionContext.global(),
                    ActorRef.noSender()
                );

            logger.info("Metrics enabled.");
            return;
        }

        logger.info("Metrics disabled due to missing configuration.");
    }

    private void flush() {
        if(enabled && !buffer.isEmpty()) {
            try {
                stackdriver.write(buffer);
                logger.info(String.format("Flushing %d data points", buffer.size()));
                buffer.clear();
            } catch (Exception e) {
                logger.error("Error while writing data to stackdriver", e);
            }
        }
    }

    private void collect(String execution) {
        if(enabled && execution != null) {
            String url = String.format(apiUrl, execution);
            Map<String, String> headers = new HashMap<>();
            headers.put(apiHeader, apiKey);

            http
                .get(url, headers, getContext().dispatcher())
                .whenCompleteAsync((res, err) -> {
                    if(err != null || res.getStatusCode() != 200) {
                        logger.error(String.format("Couldn't get info for %s.", execution));
                        return;
                    }

                    ObjectReader reader = Json.mapper().readerFor(new TypeReference<Map<String, Object>>() {});
                    try {
                        Map<String, Object> map = reader.readValue(res.getResponseBody());
                        LogEntry entry = LogEntry
                            .newBuilder(Payload.JsonPayload.of(map))
                            .setLogName("proxy")
                            .setSeverity(Severity.INFO)
                            .setResource(resource)
                            .setReceiveTimestamp(clock.millis())
                            .build();

                        buffer.add(entry);
                    } catch (JsonProcessingException e) {
                        logger.error("Error parsing execution info.", e);
                    }
                });
        }
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(String.class, this::collect)
            .match(Flush.class, any -> flush())
            .matchAny(req -> logger.debug(req.toString()))
            .build();
    }

    private static class Flush {
        public Flush() {}
    }
}
