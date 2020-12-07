package oystr.services.impl;

import org.asynchttpclient.AsyncHttpClient;
import oystr.services.HttpClient;
import org.asynchttpclient.Request;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.Response;
import org.asynchttpclient.proxy.ProxyServer;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;

public class FutureBasedHttpClient implements HttpClient<Response> {
    private final AsyncHttpClient client;

    @Inject
    public FutureBasedHttpClient(AsyncHttpClient client) {
        this.client = client;
    }

    @Override
    public CompletableFuture<Response> get(String url, ProxyServer proxy) {
        Request request = new RequestBuilder()
            .setUrl(url)
            .setRequestTimeout(5 * 1000 /* 5s */)
            .setProxyServer(proxy)
            .build();

        return client.executeRequest(request).toCompletableFuture();
    }
}
