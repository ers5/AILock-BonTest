package sajo.AiLock_bonTest.service.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import sajo.AiLock_bonTest.config.FeatureProperties;
import sajo.AiLock_bonTest.decider.AiDecider;
import sajo.AiLock_bonTest.decider.MessageRouter;
import sajo.AiLock_bonTest.domain.enums.MessageIntent;
import sajo.AiLock_bonTest.domain.enums.MessageTone;
import sajo.AiLock_bonTest.dto.ai.AiDecideResponse;
import sajo.AiLock_bonTest.dto.ai.RouterResult;
import sajo.AiLock_bonTest.dto.chat.*;
import sajo.AiLock_bonTest.dto.permit.SimilarRequest;
import sajo.AiLock_bonTest.repository.EmbeddingRepository;
import sajo.AiLock_bonTest.service.permit.PermitMemoryService;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiDecideService {

    private static final double NEAR_DUP_THRESHOLD = 0.15;
    private static final int NEAR_DUP_WINDOW_DAYS = 7;
    private static final int NEAR_DUP_COUNT=4;
    private static final int RAG_TOP_N = 5;
    private static final double RAG_DISTANCE_THRESHOLD = 0.35;
    private static final ZoneId DECISION_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalTime DECISION_DAY_START = LocalTime.of(6, 0);

    private final EmbeddingRepository embeddingRepository;
    private final EmbeddingModel embeddingModel;
    private final MessageRouter router;
    private final AiDecider decider;
    private final PermitMemoryService permitMemoryService;
    private final ChatTransactionService chatTransactionService;
    private final FeatureProperties featureProperties;

    public ChatResponse chat(ChatRequest request) {
        if (isUnlockCode(request.userInput())) {
            Optional<ChatResponse> response = chatTransactionService.tryEndSession(request);
            if (response.isPresent()) {return response.get();}
        }

        ChatContext context = chatTransactionService.load(request);
        Instant now = Instant.now();

        Instant decisionCutoff = mostRecentDecisionCutoff(now);

        List<ChatTurnSnapshot> decisionTurns = context.turns().stream()
                .filter(turn -> !turn.createdAt().isBefore(decisionCutoff))
                .toList();

        RouterResult routed = router.route(context.appName(),request.userInput(), decisionTurns);

        boolean abuseHardRejectEnabled = featureProperties.isAbuseHardRejectEnabled();
        boolean abuseTriggered = abuseHardRejectEnabled && routed.userTone() == MessageTone.ABUSE;
        boolean hardRejectForDecision = abuseHardRejectEnabled && (context.hardRejectActive() || routed.userTone() == MessageTone.ABUSE);

        String nearDupContext = null;
        float[] embedding = null;
        boolean isNearDup = false;
        String ragContext=null;
        int ragScore = 0;

        if (routed.userIntent() == MessageIntent.UNLOCK_REQUEST && !hardRejectForDecision) {
            embedding = tryEmbed(request.userInput(), "사용자 입력");
            if (embedding != null) {
                Instant since = now.minus(NEAR_DUP_WINDOW_DAYS, ChronoUnit.DAYS);
                List<String> dups = embeddingRepository.findNearDupInputs(
                                context.deviceId(),
                                embedding,
                                since,
                                NEAR_DUP_THRESHOLD
                        );
                isNearDup = dups.size() >= NEAR_DUP_COUNT;
                if (isNearDup) {nearDupContext = buildNearDupContext(dups);}
                else {
                    String ragQueryText = permitMemoryService.buildCurrentRetrievalText(
                                    context.appName(),
                                    context.turns(),
                                    request.userInput(),
                                    context.currentRequestBoundaryTurnIndex());

                    float[] ragEmbedding = tryEmbed(ragQueryText, "RAG 검색");
                    if (ragEmbedding != null) {
                        List<SimilarRequest> similar =
                                embeddingRepository.searchSimilarWithOutcome(
                                        context.deviceId(),
                                        context.appId(),
                                        ragEmbedding,
                                        RAG_DISTANCE_THRESHOLD,
                                        RAG_TOP_N
                                );

                        ragContext = buildRagContext(similar);
                        ragScore = calculateRagScore(similar);
                    }
                }
            }
        }

        AiDecideResponse decision = decider.decide(
                context.appName(), hardRejectForDecision, request.userInput(), routed,
                isNearDup, ragScore, ragContext, decisionTurns,nearDupContext);

        log.debug("AI 판단이유\n{}",decision.reason());

        ChatSaveCommand command = new ChatSaveCommand(
                context.sessionId(),
                context.turnCount(),
                request.userInput(),
                routed,
                abuseTriggered,
                decision,
                embedding,
                decider.modelName(),
                now
        );

        return chatTransactionService.save(command);
    }

    private String buildRagContext(List<SimilarRequest> similar) {
        if (similar.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("[이 사용자의 과거 유사 요청과 이행 결과]\n\n");
        for (int i = 0; i < similar.size(); i++) {
            SimilarRequest s = similar.get(i);
            String outcome = switch (s.closeReason()) {
                case "CLOSED_EARLY"    -> "약속보다 일찍 종료(지킴)";
                case "EXPIRED_TIMEOUT" -> "시간 다 씀(어김)";
                default                -> s.closeReason();
            };
            sb.append("[유사 요청 ").append(i + 1).append("]\n")
                    .append(s.retrievalText()).append("\n")
                    .append("결과: ").append(s.grantedSec()).append("초 허용, ")
                    .append(outcome).append("\n\n");
        }
        return sb.toString();
    }

    private int calculateRagScore(List<SimilarRequest> similar) {
        int keptCount = 0;
        int evaluatedCount = 0;
        for (SimilarRequest s : similar) {
            if ("CLOSED_EARLY".equals(s.closeReason())) {
                keptCount++;
                evaluatedCount++;
            } else if ("EXPIRED_TIMEOUT".equals(s.closeReason())) {
                evaluatedCount++;
            }
        }

        if (evaluatedCount < RAG_TOP_N) return 0;
        return keptCount >= 3 ? 2 : -2;
    }

    private String buildNearDupContext(List<String> dups) {
        StringBuilder sb = new StringBuilder("[이 사용자가 최근 일주일 사이 반복한 유사 요청들]\n");
        for (String input : dups) {sb.append("- \"").append(input).append("\"\n");}
        sb.append("(총 ").append(dups.size()).append("회)");
        return sb.toString();
    }

    private boolean isUnlockCode(String input) {
        if (input == null || input.length() != 4) return false;

        return input.chars()
                .allMatch(value -> value >= '0' && value <= '9');
    }

    private Instant mostRecentDecisionCutoff(Instant now) {
        ZonedDateTime zonedNow = now.atZone(DECISION_ZONE);
        ZonedDateTime todaySix = zonedNow.toLocalDate()
                .atTime(DECISION_DAY_START)
                .atZone(DECISION_ZONE);

        return zonedNow.isBefore(todaySix)
                ? todaySix.minusDays(1).toInstant()
                : todaySix.toInstant();
    }

    private float[] tryEmbed(String text, String purpose) {
        try {return embeddingModel.embed(text);}
        catch (RuntimeException e) {
            log.warn("{} 임베딩 실패, 임베딩 없이 판단 계속", purpose, e);
            return null;
        }
    }
}