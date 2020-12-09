package oystr.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.Type;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@Entity(name = "metadata")
public class RequestMetadata {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "UUID", unique = true, nullable = false, updatable = false)
    @Type(type = "org.hibernate.type.PostgresUUIDType")
    private UUID id;

    @NotBlank
    private String execution;

    @Column(insertable = false, updatable = false)
    private LocalDateTime created;
}
