package oystr.services;

import org.asynchttpclient.proxy.ProxyServer;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public interface HttpClient<T> {
    CompletableFuture<T> get(String url, Map<String, String> headers, Executor ctx);
    CompletableFuture<T> head(String url, ProxyServer proxy, Executor ctx);
}
