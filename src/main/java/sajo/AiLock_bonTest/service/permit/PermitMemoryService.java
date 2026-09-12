package sajo.AiLock_bonTest.service.permit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import sajo.AiLock_bonTest.domain.entity.App;
import sajo.AiLock_bonTest.domain.entity.ChatSession;
import sajo.AiLock_bonTest.domain.entity.ChatTurn;
import sajo.AiLock_bonTest.domain.entity.Permit;
import sajo.AiLock_bonTest.domain.enums.MessageIntent;
import sajo.AiLock_bonTest.dto.chat.ChatTurnSnapshot;
import sajo.AiLock_bonTest.dto.permit.PermitMemoryContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PermitMemoryService {

    private final EmbeddingModel embeddingModel;
    private final PermitMemoryTransactionService permitMemoryTransactionService;

    public String buildCurrentRetrievalText(String appName, List<ChatTurnSnapshot> previousTurns,
                                            String currentInput, int boundaryTurnIndex) {
        List<ChatTurnSnapshot> contextTurns = previousTurns.stream()
                .filter(turn -> turn.turnIndex() > boundaryTurnIndex)
                .filter(turn -> turn.messageIntent() == MessageIntent.UNLOCK_REQUEST)
                .sorted(Comparator.comparingInt(ChatTurnSnapshot::turnIndex))
                .toList();

        List<String> userInputs = new ArrayList<>();
        for (ChatTurnSnapshot contextTurn : contextTurns) {
            userInputs.add(contextTurn.userInput());
        }
        userInputs.add(currentInput);

        return buildRetrievalText(appName, userInputs);
    }

    private ChatTurn findStartTurn(List<ChatTurn> turns, Permit permit, ChatTurn fallback) {
        if (permit.getStartTurnId() == null) {
            return fallback;
        }
        return turns.stream()
                .filter(turn -> permit.getStartTurnId().equals(turn.getId()))
                .findFirst()
                .orElse(fallback);
    }

    private List<ChatTurn> selectContextTurns(List<ChatTurn> turns, int startTurnIndex, int grantingTurnIndex) {
        return turns.stream()
                .filter(turn -> turn.getTurnIndex() >= startTurnIndex)
                .filter(turn -> turn.getTurnIndex() <= grantingTurnIndex)
                .filter(turn -> turn.getMessageIntent() == MessageIntent.UNLOCK_REQUEST)
                .sorted(Comparator.comparingInt(ChatTurn::getTurnIndex))
                .toList();
    }

    private String buildRetrievalTextFromTurns(String appName, List<ChatTurn> turns) {
        return buildRetrievalText(appName, turns.stream()
                .map(ChatTurn::getUserInput)
                .toList());
    }

    private String buildRetrievalText(String appName, List<String> userInputs) {
        StringBuilder sb = new StringBuilder();
        sb.append("앱: ").append(appName == null ? "알 수 없음" : appName).append('\n');
        sb.append("사용자 요청:\n");
        for (String input : userInputs) {
            if (input == null || input.isBlank()) {
                continue;
            }
            sb.append("- ").append(input.strip()).append('\n');
        }
        return sb.toString().strip();
    }

    public PermitMemoryContext buildContext(App app, ChatSession session, Permit permit,
                                            List<ChatTurn> turns, Instant createdAt) {
        ChatTurn grantingTurn = turns.stream()
                .filter(turn -> turn.getId().equals(permit.getGrantingTurnId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("granting turn 없음"));

        ChatTurn startTurn = findStartTurn(turns, permit, grantingTurn);

        List<ChatTurn> contextTurns = selectContextTurns(
                turns, startTurn.getTurnIndex(), grantingTurn.getTurnIndex());

        String retrievalText =
                buildRetrievalTextFromTurns(app.getAppName(), contextTurns);

        return new PermitMemoryContext(
                permit.getId(),
                session.getDeviceId(),
                app.getId(),
                session.getId(),
                startTurn.getId(),
                permit.getGrantingTurnId(),
                retrievalText,
                permit.getGrantedSec(),
                permit.getCloseReason(),
                createdAt
        );
    }

    public void create(PermitMemoryContext context) {
        float[] embedding = embeddingModel.embed(context.retrievalText());
        permitMemoryTransactionService.save(context, embedding);
    }
}
