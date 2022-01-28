package oystr.models.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@AllArgsConstructor
@Data
@Builder
public class FindSnapshotRequest {
    @JsonProperty(required = true)
    @NotBlank
    private String date;
}