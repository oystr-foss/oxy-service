import com.google.inject.AbstractModule;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import oystr.akka.RootActors;
import oystr.akka.RootActorsImpl;
import oystr.models.dao.JPARequestMetadataRepository;
import oystr.models.dao.RequestMetadataRepository;
import oystr.services.HttpClient;
import oystr.services.Services;
import oystr.services.impl.BasicServices;
import oystr.services.impl.FutureBasedHttpClient;

import java.time.Clock;

public class Module extends AbstractModule {
    @Override
    protected void configure() {
        bind(Clock.class).toInstance(Clock.systemDefaultZone());
        bind(Services.class).to(BasicServices.class).asEagerSingleton();
        bind(RootActors.class).to(RootActorsImpl.class).asEagerSingleton();
        bind(RequestMetadataRepository.class).to(JPARequestMetadataRepository.class).asEagerSingleton();
        bind(AsyncHttpClient.class).toInstance(new DefaultAsyncHttpClient());
        bind(HttpClient.class).to(FutureBasedHttpClient.class);
    }
}