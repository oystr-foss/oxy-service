package oystr.models.dao;

import lombok.Builder;
import lombok.Data;
import oystr.models.RequestMetadata;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static java.util.concurrent.CompletableFuture.supplyAsync;

@Data
@Builder
@Singleton
public class JPARequestMetadataRepository implements RequestMetadataRepository {
    private final JPAApi api;

    private final DatabaseExecutionContext ctx;

    @Inject
    public JPARequestMetadataRepository(JPAApi api, DatabaseExecutionContext ctx) {
        this.api = api;
        this.ctx = ctx;
    }

    @Override
    public CompletionStage<RequestMetadata> add(RequestMetadata metadata) {
        return supplyAsync(() -> {
            try {
                return wrap(em -> insert(em, metadata));
            } catch(Exception e) {
                e.printStackTrace();
            }
            return null;
        }, ctx);
    }

    private <T> T wrap(Function<EntityManager, T> function) {
        return api.withTransaction(function);
    }

    private RequestMetadata insert(EntityManager em, RequestMetadata metadata) {
        em.persist(metadata);
        return metadata;
    }
}
