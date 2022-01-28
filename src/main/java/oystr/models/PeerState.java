package oystr.models;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum PeerState {
    UNKNOWN("unknown"),
    RUNNING("running"),
    PENDING("pending"),
    FAILING("failing"),
    DISABLED("disabled"),
    AVOID("avoid");

    @Getter
    public String value;
}