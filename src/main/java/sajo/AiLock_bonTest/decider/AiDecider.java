package sajo.AiLock_bonTest.decider;

import sajo.AiLock_bonTest.dto.ai.AiDecideResponse;
import sajo.AiLock_bonTest.dto.chat.ChatTurnSnapshot;
import sajo.AiLock_bonTest.dto.ai.RouterResult;

import java.util.List;

public interface AiDecider {
    AiDecideResponse decide(
            String appName,
            boolean hardRejectActive,
            String userInput,
            RouterResult routerResult,
            boolean isNearDup,
            int ragScore,
            String ragContext,
            List<ChatTurnSnapshot> turns,
            String nearDupTexts
    );

    String modelName();
}
