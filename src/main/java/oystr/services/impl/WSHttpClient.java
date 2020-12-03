package oystr.services.impl;

import oystr.services.HttpClient;
import play.libs.ws.WSClient;
import play.libs.ws.WSResponse;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class WSHttpClient
    implements HttpClient<WSResponse> {
    private final WSClient ws;

    @Inject
    public WSHttpClient(WSClient ws) {
        this.ws = ws;
    }

    @Override
    public CompletableFuture<WSResponse> get(String url, Map<String, String> headers) {
        return ws.url(url).setHeaders(toHeaders(headers)).get().toCompletableFuture();
    }

    @Override
    public <B extends String, JsonNode, InputStream, BodyWritable> CompletableFuture<WSResponse> post(String url, Map<String, String> headers, B body) {
        return ws.url(url).setHeaders(toHeaders(headers)).post(body).toCompletableFuture();
    }

    @Override
    public <B extends String, JsonNode, InputStream, BodyWritable> CompletableFuture<WSResponse> put(String url, Map<String, String> headers, B body) {
        return ws.url(url).setHeaders(toHeaders(headers)).put(body).toCompletableFuture();
    }

    @Override
    public CompletableFuture<WSResponse> delete(String url, Map<String, String> headers) {
        return ws.url(url).setHeaders(toHeaders(headers)).delete().toCompletableFuture();
    }

    private Map<String, List<String>> toHeaders(Map<String, String> headers) {
        Map<String, List<String>> h = new HashMap<>();
        headers.forEach((k, v) -> h.putIfAbsent(k, Collections.singletonList(v)));

        return h;
    }
}
