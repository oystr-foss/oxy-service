package oystr.services;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface HttpClient<T> {
    CompletableFuture<T> get(String url, Map<String, String> headers);

    <B extends String, JsonNode, InputStream, BodyWritable> CompletableFuture<T> post(String url, Map<String, String> headers, B body);

    <B extends String, JsonNode, InputStream, BodyWritable> CompletableFuture<T> put(String url, Map<String, String> headers, B body);

    CompletableFuture<T> delete(String url, Map<String, String> headers);
}
