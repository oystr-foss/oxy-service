package oystr.models.messages;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import oystr.models.Peer;

@AllArgsConstructor
@Data
@Builder
public class TaintPeerRequest {
    private String serviceId;
}