package sajo.AiLock_bonTest.decider.llm.router;

import sajo.AiLock_bonTest.domain.enums.MessageIntent;
import sajo.AiLock_bonTest.domain.enums.MessageTone;
import sajo.AiLock_bonTest.dto.ai.RouterRaw;

public final class RouterCodeMapper {

    private RouterCodeMapper() {}

    public static MessageIntent toIntent(RouterRaw.IntentCode code) {
        return switch (code) {
            case U -> MessageIntent.UNLOCK_REQUEST;
            case W -> MessageIntent.WHY;
            case S -> MessageIntent.SMALL_TALK;
        };
    }

    public static MessageTone toTone(RouterRaw.ToneCode code) {
        return switch (code) {
            case A -> MessageTone.ABUSE;
            case N -> MessageTone.NORMAL;
        };
    }
}