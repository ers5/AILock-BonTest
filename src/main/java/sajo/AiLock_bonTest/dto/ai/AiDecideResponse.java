package sajo.AiLock_bonTest.dto.ai;

import sajo.AiLock_bonTest.domain.enums.ActionStandard;
import sajo.AiLock_bonTest.domain.enums.AiAction;


import java.util.List;

public record AiDecideResponse(
        AiAction aiAction,
        String aiMessage,
        String reason,
        Integer proposedSec,
        List<ActionStandard> failedStandards
) {
}
