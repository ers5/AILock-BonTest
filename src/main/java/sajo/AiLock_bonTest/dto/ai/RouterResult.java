package sajo.AiLock_bonTest.dto.ai;

import sajo.AiLock_bonTest.domain.enums.MessageIntent;
import sajo.AiLock_bonTest.domain.enums.MessageTone;

public record RouterResult(
        MessageIntent userIntent,
        MessageTone userTone,
        Double intentMargin
) {}
