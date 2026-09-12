package sajo.AiLock_bonTest.dto.chat;

import sajo.AiLock_bonTest.dto.ai.AiDecideResponse;
import sajo.AiLock_bonTest.dto.ai.RouterResult;

import java.time.Instant;

public record ChatSaveCommand(
        Long sessionId,
        int expectedTurnCount,
        String userInput,
        RouterResult routed,
        boolean abuseTriggered,
        AiDecideResponse decision,
        float[] embedding,
        String modelName,
        Instant createdAt
) {
}