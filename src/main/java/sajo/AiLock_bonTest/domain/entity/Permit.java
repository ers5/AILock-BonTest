package sajo.AiLock_bonTest.domain.entity;

import sajo.AiLock_bonTest.domain.enums.PermitCloseReason;
import sajo.AiLock_bonTest.domain.enums.PermitStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "permit",
        indexes = @Index(name = "idx_permit_status_expires", columnList = "status, expires_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Permit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "start_turn_id")
    private Long startTurnId;

    @Column(name = "granting_turn_id")
    private Long grantingTurnId;

    @Column(name = "granted_sec", nullable = false)
    private int grantedSec;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PermitStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "close_reason")
    private PermitCloseReason closeReason;

    @Column(name = "closed_at")
    private Instant closedAt;

    private Permit(Long sessionId, Long startTurnId, Long grantingTurnId, int grantedSec, Instant issuedAt) {
        this.sessionId = sessionId;
        this.startTurnId = startTurnId;
        this.grantingTurnId = grantingTurnId;
        this.grantedSec = grantedSec;
        this.issuedAt = issuedAt;
        this.status = PermitStatus.OFFERED;
    }

    public static Permit offer(Long sessionId, Long startTurnId, Long grantingTurnId, int grantedSec, Instant issuedAt) {
        return new Permit(sessionId, startTurnId, grantingTurnId, grantedSec, issuedAt);
    }

    private static final int GRACE_SECONDS=0;

    public boolean activate(Instant startedAt) {
        if (this.status != PermitStatus.OFFERED) {
            return false;
        }
        this.status = PermitStatus.ACTIVE;
        this.expiresAt = startedAt.plusSeconds(this.grantedSec+GRACE_SECONDS);
        return true;
    }

    public void reoffer(int newGrantedSec, Long grantingTurnId) {
        if (this.status != PermitStatus.OFFERED) {
            throw new IllegalStateException("OFFERED 상태에서만 재협상 가능: " + this.status);
        }
        this.grantedSec = newGrantedSec;
        this.grantingTurnId = grantingTurnId;
    }

    public boolean close(PermitCloseReason reason, Instant closedAt) {
        if (this.status != PermitStatus.ACTIVE) {
            return false;
        }
        this.status = PermitStatus.CLOSED;
        this.closeReason = reason;
        this.closedAt = closedAt;
        return true;
    }

    public boolean closeByClient(Instant closedAt) {
        if (this.status != PermitStatus.ACTIVE) {
            return false;
        }
        this.status = PermitStatus.CLOSED;
        this.closeReason = closedAt.isBefore(this.expiresAt)
                ? PermitCloseReason.CLOSED_EARLY
                : PermitCloseReason.EXPIRED_TIMEOUT;
        this.closedAt = closedAt;
        return true;
    }

    public boolean closeOffered(Instant closedAt) {
        if (this.status != PermitStatus.OFFERED) {
            return false;
        }
        this.status = PermitStatus.CLOSED;
        this.closeReason = PermitCloseReason.SESSION_ENDED;
        this.closedAt = closedAt;
        return true;
    }
}
