package sajo.AiLock_bonTest.dto.chat;

import sajo.AiLock_bonTest.domain.enums.AiAction;

public record ChatResponse(
        String aiMessage,
        Integer grantedSec,
        AiAction aiAction
) {
}
