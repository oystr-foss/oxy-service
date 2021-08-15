import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import org.asynchttpclient.AsyncHttpClient;
import oystr.akka.RootActors;
import oystr.akka.RootActorsImpl;
import oystr.models.Peer;
import oystr.services.CacheClient;
import oystr.services.Codec;
import oystr.services.HttpClient;
import oystr.services.Services;
import oystr.services.impl.BasicServices;
import oystr.services.impl.FutureBasedHttpClient;
import oystr.services.impl.JdkCodec;
import oystr.services.impl.LettuceCacheClient;

import java.time.Clock;

import static org.asynchttpclient.Dsl.asyncHttpClient;

public class Module extends AbstractModule {
    @Override
    protected void configure() {
        bind(Clock.class).toInstance(Clock.systemDefaultZone());
        bind(RootActors.class).to(RootActorsImpl.class).asEagerSingleton();
        bind(CacheClient.class).to(LettuceCacheClient.class).asEagerSingleton();
        bind(Services.class).to(BasicServices.class).asEagerSingleton();
        bind(AsyncHttpClient.class).toInstance(asyncHttpClient());
        bind(HttpClient.class).to(FutureBasedHttpClient.class);
        bind(new TypeLiteral<Codec<Peer>>(){}).to(JdkCodec.class);
    }
}