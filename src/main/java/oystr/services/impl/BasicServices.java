package oystr.services.impl;

import akka.actor.ActorSystem;
import com.typesafe.config.Config;
import oystr.services.Services;
import play.Environment;
import play.Logger;
import play.libs.concurrent.HttpExecutionContext;

import javax.inject.Inject;
import java.time.Clock;

public class BasicServices
    implements Services {
    private Config conf;

    private Environment env;

    private Clock clock;

    private ActorSystem sys;

    private HttpExecutionContext ec;

    @Inject
    public BasicServices(Config conf, Environment env, Clock clock, ActorSystem sys, HttpExecutionContext ec) {
        this.conf = conf;
        this.env = env;
        this.clock = clock;
        this.sys = sys;
        this.ec = ec;
    }

    @Override
    public Config conf() {
        return conf;
    }

    @Override
    public Environment env() {
        return env;
    }

    @Override
    public Clock clock() {
        return clock;
    }

    @Override
    public ActorSystem sys() {
        return sys;
    }

    @Override
    public HttpExecutionContext ec() {
        return ec;
    }

    @Override
    public Logger.ALogger logger(String name) {
        return Logger.of(name);
    }
}