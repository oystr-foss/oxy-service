package oystr.models.messages;

import lombok.*;
import oystr.models.Peer;

@AllArgsConstructor
@Data
@Builder
public class AddPeerRequest {
    private Peer peer;
}