package oystr.services;

import org.asynchttpclient.proxy.ProxyServer;
import java.util.concurrent.CompletableFuture;

public interface HttpClient<T> {
    CompletableFuture<T> get(String url, ProxyServer proxy);
}
