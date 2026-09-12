package sajo.AiLock_bonTest.dto.admin;

import sajo.AiLock_bonTest.domain.enums.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminDataResponse(
        Instant generatedAt,
        Summary summary,
        List<DeviceData> devices
) {
    public record Summary(
            long deviceCount,
            long activeSessionCount,
            long activePermitCount,
            long hardRejectSessionCount,
            long conversationCount,
            long permitMemoryCount
    ) {}

    public record DeviceData(
            UUID deviceId,
            String nickName,
            int totalSessionCount,
            long activeSessionCount,
            Instant lastActivityAt,
            List<SessionData> sessions
    ) {}

    public record SessionData(
            Long sessionId,
            UUID deviceId,
            Long appId,
            String appName,
            String packageName,
            SessionStatus status,
            String unlockCode,
            int turnCount,
            Instant openedAt,
            Instant sessionExpiresAt,
            Instant closedAt,
            Instant hardRejectUntil,
            boolean hardRejectActive,
            List<TurnData> turns,
            List<PermitData> permits,
            List<PermitMemoryData> permitMemories
    ) {}

    public record TurnData(
            Long turnId,
            int turnIndex,
            MessageIntent messageIntent,
            MessageTone messageTone,
            String userInput,
            AiAction aiAction,
            String aiReason,
            String aiMessage,
            Integer proposedSec,
            String modelName,
            Instant createdAt,
            boolean embedding,
            List<ActionStandard> failedStandards
    ) {}

    public record PermitData(
            Long permitId,
            Long startTurnId,
            Long grantingTurnId,
            int grantedSec,
            Instant issuedAt,
            Instant expiresAt,
            PermitStatus status,
            PermitCloseReason closeReason,
            Instant closedAt
    ) {}

    public record PermitMemoryData(
            Long memoryId,
            Long permitId,
            Long startTurnId,
            Long grantingTurnId,
            String retrievalText,
            int grantedSec,
            PermitCloseReason closeReason,
            Instant createdAt
    ) {}
}