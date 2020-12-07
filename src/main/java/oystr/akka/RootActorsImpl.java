package oystr.akka;

import akka.actor.ActorRef;
import akka.actor.Props;
import oystr.models.dao.RequestMetadataRepository;
import oystr.services.HttpClient;
import oystr.services.PeersRegistryActor;
import oystr.services.Services;

import javax.inject.Inject;

public class RootActorsImpl implements RootActors {
    private final ActorRef peersRegistryActor;

    @Inject
    public RootActorsImpl(HttpClient http, Services services, RequestMetadataRepository repo) {
        this.peersRegistryActor = services
            .sys()
            .actorOf(Props.create(PeersRegistryActor.class, services, http, repo), "peers-registry-actor");
    }

    @Override
    public ActorRef peersRegistryActor() {
        return peersRegistryActor;
    }
}
