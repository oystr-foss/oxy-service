import com.google.inject.AbstractModule;
import oystr.akka.RootActors;
import oystr.akka.RootActorsImpl;
import oystr.services.HttpClient;
import oystr.services.Services;
import oystr.services.impl.BasicServices;
import oystr.services.impl.WSHttpClient;

import java.time.Clock;

public class Module extends AbstractModule {
    @Override
    protected void configure() {
        bind(Clock.class).toInstance(Clock.systemDefaultZone());
        bind(Services.class).to(BasicServices.class).asEagerSingleton();
        bind(RootActors.class).to(RootActorsImpl.class).asEagerSingleton();
        bind(HttpClient.class).to(WSHttpClient.class);
    }
}