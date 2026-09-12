package sajo.AiLock_bonTest.dto.permit;

import sajo.AiLock_bonTest.domain.enums.PermitCloseReason;

import java.time.Instant;
import java.util.UUID;

public record PermitMemoryContext(
        Long permitId,
        UUID deviceId,
        Long appId,
        Long sessionId,
        Long startTurnId,
        Long grantingTurnId,
        String retrievalText,
        int grantedSec,
        PermitCloseReason closeReason,
        Instant createdAt
) {
}