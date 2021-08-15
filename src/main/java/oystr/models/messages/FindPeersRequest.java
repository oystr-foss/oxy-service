package oystr.models.messages;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@AllArgsConstructor
@Data
@Builder
public class FindPeersRequest {
    @Builder.Default
    private Boolean onlyRunning = false;
}