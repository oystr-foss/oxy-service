package oystr.akka;

import akka.actor.ActorRef;
import akka.actor.Props;
import oystr.actors.MetricsCollectorActor;
import oystr.actors.PeersRegistryActor;
import oystr.services.HttpClient;
import oystr.services.Services;

import javax.inject.Inject;

public class RootActorsImpl implements RootActors {
    private final ActorRef peersRegistryActor;

    private final ActorRef metricsActor;

    @Inject
    public RootActorsImpl(HttpClient http, Services services) {
        this.peersRegistryActor = services
            .sys()
            .actorOf(Props.create(PeersRegistryActor.class, services, http), "peers-registry-actor");

        this.metricsActor = services
            .sys()
            .actorOf(Props.create(MetricsCollectorActor.class, services, http), "metrics-actor");
    }

    @Override
    public ActorRef peersRegistryActor() {
        return peersRegistryActor;
    }

    @Override
    public ActorRef metricsActor() {
        return metricsActor;
    }
}
