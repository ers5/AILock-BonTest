package sajo.AiLock_bonTest.dto.chat;
import java.util.List;
import java.util.UUID;

public record ChatContext(
        Long appId,
        String appName,
        Long sessionId,
        UUID deviceId,
        int turnCount,
        boolean hardRejectActive,
        int currentRequestBoundaryTurnIndex,
        List<ChatTurnSnapshot> turns
) {
}