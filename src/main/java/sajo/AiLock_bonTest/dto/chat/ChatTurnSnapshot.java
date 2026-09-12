package sajo.AiLock_bonTest.dto.chat;
import sajo.AiLock_bonTest.domain.entity.ChatTurn;
import sajo.AiLock_bonTest.domain.enums.MessageIntent;

import java.time.Instant;

public record ChatTurnSnapshot(
        Long id,
        int turnIndex,
        MessageIntent messageIntent,
        String userInput,
        String aiMessage,
        Instant createdAt
) {
    public static ChatTurnSnapshot from(ChatTurn turn) {
        return new ChatTurnSnapshot(
                turn.getId(),
                turn.getTurnIndex(),
                turn.getMessageIntent(),
                turn.getUserInput(),
                turn.getAiMessage(),
                turn.getCreatedAt()
        );
    }
}
