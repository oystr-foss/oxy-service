import akka.stream.Materializer;
import oystr.services.Services;
import play.Logger;
import play.mvc.Filter;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class LoggingFilter extends Filter {
    private final Logger.ALogger logger;

    @Inject
    public LoggingFilter(Materializer mat, Services services) {
        super(mat);
        this.logger = services.logger("application");
    }

    @Override
    public CompletionStage<Result> apply(Function<Http.RequestHeader, CompletionStage<Result>> next, Http.RequestHeader rh) {
        Long startTime = System.currentTimeMillis();
        return next
            .apply(rh)
            .thenApply(result -> {
                Long endTime = System.currentTimeMillis();
                Long requestTime = endTime - startTime;

                logger.info(String.format("%s %s %s (%dms)", rh.method(), rh.uri(), result.status(), requestTime));
                return result.withHeader("Request-Time", requestTime.toString());
            });
    }
}
