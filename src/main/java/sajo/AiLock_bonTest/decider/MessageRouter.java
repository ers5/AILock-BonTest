package sajo.AiLock_bonTest.decider;


import sajo.AiLock_bonTest.dto.chat.ChatTurnSnapshot;
import sajo.AiLock_bonTest.dto.ai.RouterResult;

import java.util.List;

public interface MessageRouter {

    RouterResult route(
            String appName,
            String userInput,
            List<ChatTurnSnapshot> chatTurns
    );
}
