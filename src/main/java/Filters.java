import play.filters.cors.CORSFilter;
import play.filters.gzip.GzipFilter;
import play.http.DefaultHttpFilters;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class Filters extends DefaultHttpFilters {
    @Inject
    public Filters(CORSFilter cors, LoggingFilter logging, GzipFilter gzip) {
        super(cors, logging, gzip);
    }
}
