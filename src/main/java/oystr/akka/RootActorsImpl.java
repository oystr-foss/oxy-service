package oystr.akka;

import akka.actor.ActorRef;
import oystr.services.HttpClient;
import oystr.services.Services;

import javax.inject.Inject;

public class RootActorsImpl implements RootActors {
    @Inject
    public RootActorsImpl(HttpClient http, Services services) {
    }

    @Override
    public ActorRef notificationsActor() {
        return null;
    }
}
