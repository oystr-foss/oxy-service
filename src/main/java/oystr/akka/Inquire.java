package oystr.akka;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import scala.compat.java8.FutureConverters;
import scala.concurrent.Promise;

import java.util.concurrent.CompletionStage;

public class Inquire extends AbstractActor {
    private final Promise<Object> promise;

    private final ActorRef manager;

    public Inquire(ActorRef manager, Promise<Object> promise) {
        super();
        this.manager = manager;
        this.promise = promise;
    }

    public static <T> CompletionStage<Object> inquire(ActorRef target, T msg, ActorSystem sys) {
        Promise promise = Promise.apply();
        sys.actorOf(Props.create(Inquire.class, target, promise)).tell(msg, target);

        // noinspection unchecked
        return (CompletionStage<Object>) FutureConverters.toJava(promise.future());
    }

    private Receive waitingForReply() {
        return receiveBuilder()
            .matchAny(any -> {
                promise.success(any);
                getContext().stop(getSelf());
            })
            .build();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .matchAny(any -> {
                getContext().become(waitingForReply());
                manager.tell(any, getSelf());
            })
            .build();
    }
}
