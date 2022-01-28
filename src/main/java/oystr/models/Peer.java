package oystr.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@Data
@Builder
public class Peer implements Serializable {
    @JsonProperty(value = "service_id", required = true)
    @NotBlank
    private String serviceId;

    @JsonProperty(required = true)
    @NotBlank
    private String host;

    @JsonProperty(required = true)
    @NotNull
    private Integer port;

    private String name;

    @JsonProperty(value = "last_health_check")
    private LocalDateTime lastHealthCheck;

    @JsonProperty(value = "registered_at")
    private LocalDateTime registeredAt;

    private PeerState state;

    @Override
    public String toString() {
        return String.format("[%s] %s:%s -> (registered_at: %s | last_health_check: %s)", serviceId, host, port, registeredAt, lastHealthCheck);
    }

    public String toHash() {
        return Base64.getEncoder().encodeToString(serviceId.getBytes());
    }
}