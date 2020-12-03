package oystr.services;

import akka.actor.ActorSystem;
import com.typesafe.config.Config;
import play.Environment;
import play.Logger;
import play.libs.concurrent.HttpExecutionContext;

import java.time.Clock;

public interface Services {
    Config conf();
    Environment env();
    Clock clock();
    ActorSystem sys();
    HttpExecutionContext ec();
    Logger.ALogger logger(String name);
}
