package oystr.akka;

import akka.actor.ActorRef;

public interface RootActors {
    ActorRef peersRegistryActor();
    ActorRef metricsActor();
}