package oystr.models.messages;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@AllArgsConstructor
@Data
@Builder
public class DeletePeerRequest {
    private String  serviceId;
    private String  host;
    private Integer port;
    @Builder.Default
    private Boolean deleteAll = true;
}