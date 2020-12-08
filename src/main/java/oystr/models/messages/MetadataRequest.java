package oystr.models.messages;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Optional;

@AllArgsConstructor
@Data
@Builder
public class MetadataRequest {
    private Optional<String> executionId;
}
