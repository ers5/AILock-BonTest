package sajo.AiLock_bonTest.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sajo.AiLock_bonTest.domain.enums.PermitCloseReason;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "permit_memory",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_permit_memory_permit",
                columnNames = "permit_id"
        ),
        indexes = {
                @Index(name = "idx_permit_memory_device_created", columnList = "device_id, created_at"),
                @Index(name = "idx_permit_memory_app_created", columnList = "app_id, created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PermitMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "permit_id", nullable = false)
    private Long permitId;

    @Column(name = "device_id", nullable = false, columnDefinition = "uuid")
    private UUID deviceId;

    @Column(name = "app_id", nullable = false)
    private Long appId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "start_turn_id")
    private Long startTurnId;

    @Column(name = "granting_turn_id", nullable = false)
    private Long grantingTurnId;

    @Column(name = "retrieval_text", nullable = false, columnDefinition = "text")
    private String retrievalText;

    @Column(name = "embedding", columnDefinition = "vector", insertable = false, updatable = false)
    private String embedding;

    @Column(name = "granted_sec", nullable = false)
    private int grantedSec;

    @Enumerated(EnumType.STRING)
    @Column(name = "close_reason", nullable = false)
    private PermitCloseReason closeReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private PermitMemory(Long permitId, UUID deviceId, Long appId, Long sessionId,
                         Long startTurnId, Long grantingTurnId, String retrievalText,
                         int grantedSec, PermitCloseReason closeReason, Instant createdAt) {
        this.permitId = permitId;
        this.deviceId = deviceId;
        this.appId = appId;
        this.sessionId = sessionId;
        this.startTurnId = startTurnId;
        this.grantingTurnId = grantingTurnId;
        this.retrievalText = retrievalText;
        this.grantedSec = grantedSec;
        this.closeReason = closeReason;
        this.createdAt = createdAt;
    }

    public static PermitMemory record(Long permitId, UUID deviceId, Long appId, Long sessionId,
                                      Long startTurnId, Long grantingTurnId, String retrievalText,
                                      int grantedSec, PermitCloseReason closeReason, Instant createdAt) {
        return new PermitMemory(permitId, deviceId, appId, sessionId, startTurnId, grantingTurnId,
                retrievalText, grantedSec, closeReason, createdAt);
    }
}
