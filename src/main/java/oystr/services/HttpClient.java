package oystr.services;

import org.asynchttpclient.proxy.ProxyServer;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface HttpClient<T> {
    CompletableFuture<T> get(String url, Map<String, String> headers);
    CompletableFuture<T> head(String url, ProxyServer proxy);
}
