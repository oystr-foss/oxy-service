package oystr.models.dao;

import oystr.models.RequestMetadata;

import java.util.concurrent.CompletionStage;

public interface RequestMetadataRepository {
    CompletionStage<RequestMetadata> add(RequestMetadata person);
}
