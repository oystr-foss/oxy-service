package oystr.services.impl;

import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Request;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.Response;
import org.asynchttpclient.proxy.ProxyServer;
import oystr.services.HttpClient;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Singleton
public class FutureBasedHttpClient implements HttpClient<Response> {
    private final AsyncHttpClient client;

    private final Map<String, String> ua = new HashMap<>();

    @Inject
    public FutureBasedHttpClient(AsyncHttpClient client) {
        this.client = client;
        this.ua.put("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.90 Safari/537.36");
    }

    @Override
    public CompletableFuture<Response> get(String url, Map<String, String> headers) {
        return client
            .prepareGet(url)
            .setSingleHeaders(headers)
            .setRequestTimeout(10 * 1000)
            .execute()
            .toCompletableFuture();
    }

    @Override
    public CompletableFuture<Response> head(String url, ProxyServer proxy) {
        return client
            .prepareHead(url)
            .setProxyServer(proxy)
            .setSingleHeaders(ua)
            .setRequestTimeout(10 * 1000)
            .execute()
            .toCompletableFuture();
    }
}
