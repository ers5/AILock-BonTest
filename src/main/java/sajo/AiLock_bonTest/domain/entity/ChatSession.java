package sajo.AiLock_bonTest.domain.entity;

import sajo.AiLock_bonTest.domain.enums.SessionStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;


@Entity
@Table(
    name = "chat_session",
    indexes = @Index(name = "idx_session_status_expires", columnList = "status, session_expires_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatSession {

    private static final SecureRandom RANDOM_NUMBER = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, columnDefinition = "uuid")
    private UUID deviceId;

    @Column(name = "app_id", nullable = false)
    private Long appId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    @Column(name = "turn_count", nullable = false)
    private int turnCount;

    @Column(name = "session_expires_at", nullable = false)
    private Instant sessionExpiresAt;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "hard_reject_until")
    private Instant hardRejectUntil;

    @Column(name="unlock_code",length = 4)
    private String unlockCode;

    private ChatSession(UUID deviceId, Long appId, Instant sessionExpiresAt, Instant openedAt) {
        this.deviceId = deviceId;
        this.appId = appId;
        this.status = SessionStatus.ACTIVE;
        this.turnCount = 0;
        this.sessionExpiresAt = sessionExpiresAt;
        this.openedAt = openedAt;
        this.hardRejectUntil=null;
        this.unlockCode = "%04d".formatted(RANDOM_NUMBER.nextInt(10000));
    }

    public static ChatSession open(UUID deviceId, Long appId, Instant openedAt, Instant sessionExpiresAt) {
        return new ChatSession(deviceId, appId, sessionExpiresAt, openedAt);
    }

    public void activateHardReject(Instant now, long durationSeconds) {
        Instant newUntil = now.plusSeconds(durationSeconds);
        if(this.hardRejectUntil==null|| newUntil.isAfter(this.hardRejectUntil)) this.hardRejectUntil=newUntil;
    }
    public void releaseHardRejectIfExpired(Instant now) {
        if (this.hardRejectUntil != null
                && !now.isBefore(this.hardRejectUntil)) {
            this.hardRejectUntil = null;
        }
    }

    public boolean isHardRejectActive(Instant now) {
        return this.hardRejectUntil != null
                && now.isBefore(this.hardRejectUntil);
    }

}
