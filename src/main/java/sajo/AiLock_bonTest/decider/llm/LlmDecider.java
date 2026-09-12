package sajo.AiLock_bonTest.decider.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import sajo.AiLock_bonTest.decider.AiDecider;
import sajo.AiLock_bonTest.decider.llm.guard.SmallTalkPermissionGuard;
import sajo.AiLock_bonTest.decider.prompt.AiDeciderPrompt;
import sajo.AiLock_bonTest.domain.enums.ActionStandard;
import sajo.AiLock_bonTest.domain.enums.AiAction;
import sajo.AiLock_bonTest.domain.enums.MessageIntent;
import sajo.AiLock_bonTest.dto.ai.AiDecideResponse;
import sajo.AiLock_bonTest.dto.chat.ChatTurnSnapshot;
import sajo.AiLock_bonTest.dto.ai.CriteriaJudgeResponse;
import sajo.AiLock_bonTest.dto.ai.RouterResult;
import sajo.AiLock_bonTest.global.exception.AiResponseException;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Profile("llm")
@Slf4j

public class LlmDecider implements AiDecider {

    private static final int OFFER_SCORE_THRESHOLD = 8;
    private static final int SHORT_REQUEST_THRESHOLD = 4;
    private static final int SHORT_REQUEST_MAX_MINUTES = 5;
    private final SmallTalkPermissionGuard smallTalkPermissionGuard;

    private final ChatClient chatClient;
    public LlmDecider(@Qualifier("deciderChatClient") ChatClient chatClient,SmallTalkPermissionGuard smallTalkPermissionGuard){
        this.chatClient=chatClient;
        this.smallTalkPermissionGuard = smallTalkPermissionGuard;
    }
    private static final Pattern MINUTE_PATTERN = Pattern.compile("(?<![\\d.])(\\d+)\\s*분");

    @Override
    public AiDecideResponse decide(String appName, boolean hardRejectActive, String userInput, RouterResult routed,
                                   boolean isNearDub, int ragScore, String ragContext,
                                   List<ChatTurnSnapshot> turns,String nearDubTexts) {
        if (routed.userIntent() == MessageIntent.AMBIGUOUS) return decideClarify(appName, userInput, turns, routed.intentMargin());
        if (usesScoredUnlock(hardRejectActive, routed, isNearDub)) return decideScoredUnlock(appName, userInput, ragScore, ragContext, turns);
        String systemPrompt = selectPrompt(hardRejectActive, routed, isNearDub);
        systemPrompt = runtimeContext(appName,ZonedDateTime.now(ZoneId.of("Asia/Seoul")))+ systemPrompt;
        if (nearDubTexts != null) {systemPrompt += "\n\n" + nearDubTexts;}
        else if (ragContext != null) {systemPrompt += "\n\n" + ragContext;}
        if (routed.userIntent() == MessageIntent.SMALL_TALK) {
            return decideSmallTalkWithGuard(
                    appName,
                    userInput,
                    turns,
                    systemPrompt
            );
        }
        List<Message> msgs = buildMessages(systemPrompt, turns, userInput);
        logMessages("LLM 최종 프롬프트", msgs);
        AiDecideResponse generated = callAiDecider(msgs);
        AiAction expectedAction = routed.userIntent() == MessageIntent.WHY ? AiAction.EXPLAIN : AiAction.DENY;
        return normalizeResponse(generated, expectedAction, List.of());
    }

    private AiDecideResponse decideClarify(String appName, String userInput, List<ChatTurnSnapshot> turns, Double intentMargin) {
        String reason = "라우터 intent 불확실: margin=" + intentMargin;

        try {
            String systemPrompt = runtimeContext(appName, ZonedDateTime.now(ZoneId.of("Asia/Seoul"))) + AiDeciderPrompt.CLARIFY;
            List<Message> msgs = buildMessages(systemPrompt, turns, userInput);
            logMessages("LLM CLARIFY 프롬프트", msgs);

            AiDecideResponse generated = callAiDecider(msgs);
            if (generated == null || generated.aiMessage() == null || generated.aiMessage().isBlank()) throw new AiResponseException("AI 되묻기 응답 처리 실패");

            AiDecideResponse normalized = normalizeResponse(generated, AiAction.CLARIFY, List.of());
            return new AiDecideResponse(AiAction.CLARIFY, normalized.aiMessage(), reason, null, List.of());
        } catch (AiResponseException e) {
            log.warn("CLARIFY 생성 실패, 고정 문구 사용", e);
            String fallback = "그 말은 지금 %s을 사용하게 해달라는 요청이야, 아니면 그냥 하는 말이야?".formatted(appName);
            return new AiDecideResponse(AiAction.CLARIFY, fallback, reason, null, List.of());
        }
    }

    private AiDecideResponse decideSmallTalkWithGuard(String appName, String userInput,
                                                      List<ChatTurnSnapshot> turns, String systemPrompt) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                List<Message> msgs = buildMessages(systemPrompt, turns, userInput);
                logMessages("LLM SMALL_TALK 프롬프트", msgs);

                AiDecideResponse generated = callAiDecider(msgs);
                AiDecideResponse decision = normalizeResponse(generated, AiAction.DENY, List.of());

                SmallTalkPermissionGuard.GuardResult guardResult =
                        smallTalkPermissionGuard.check(
                                appName,
                                userInput,
                                decision.aiMessage()
                        );

                if (guardResult.verdict() == SmallTalkPermissionGuard.Verdict.SAFE) {return decision;}
            } catch (AiResponseException e) {log.warn("SMALL_TALK 응답 생성 실패: attempt={}", attempt + 1, e);}
        }

        throw new AiResponseException("서버가 지금 문제가 있어. 잠시 후 다시 시도해줘.");
    }


    private boolean usesScoredUnlock(boolean hardRejectActive, RouterResult routed, boolean isNearDub) {
        return routed.userIntent() == MessageIntent.UNLOCK_REQUEST
                && !hardRejectActive
                && !isNearDub;
    }

    private AiDecideResponse decideScoredUnlock(String appName, String userInput, int ragScore, String ragContext,
                                                List<ChatTurnSnapshot> turns) {
        CriteriaJudgeResponse criteria = judgeCriteria(appName, userInput, turns);
        int baseScore = calculateBaseScore(criteria);
        int finalScore = baseScore + ragScore;
        OptionalInt requestedMinutes = maxRequestedMinutes(userInput);
        boolean shortRequest = requestedMinutes.isPresent()
                && requestedMinutes.getAsInt() >= 1
                && requestedMinutes.getAsInt() <= SHORT_REQUEST_MAX_MINUTES;
        int previousShortRequests = shortRequest ? countPreviousShortRequests(turns) : 0;
        int offerThreshold = shortRequest ? Math.min(OFFER_SCORE_THRESHOLD, SHORT_REQUEST_THRESHOLD + previousShortRequests) : OFFER_SCORE_THRESHOLD;

        AiAction finalAction = finalScore >= offerThreshold ? AiAction.OFFER : AiAction.DENY;
        List<ActionStandard> failedStandards = failedStandards(criteria);
        String prompt = finalAction == AiAction.OFFER ? AiDeciderPrompt.UNLOCK_SCORED_OFFER : AiDeciderPrompt.UNLOCK_SCORED_DENY;
        String systemPrompt = runtimeContext(appName,ZonedDateTime.now(ZoneId.of("Asia/Seoul")))
                + prompt
                + "\n\n"
                + buildScoreContext(
                criteria,
                finalAction,
                failedStandards
        );
        if (finalAction == AiAction.DENY && ragContext != null) {systemPrompt += "\n\n" + ragContext;}
        List<Message> msgs = buildMessages(systemPrompt, turns, userInput);
        logMessages("LLM 점수 기반 최종 프롬프트", msgs);
        AiDecideResponse generated = callAiDecider(msgs);
        return normalizeResponse(
                generated,
                finalAction,
                failedStandards
        );
    }

    private CriteriaJudgeResponse judgeCriteria(String appName, String userInput, List<ChatTurnSnapshot> turns) {
        String systemPrompt = runtimeContext(appName,ZonedDateTime.now(ZoneId.of("Asia/Seoul"))) + AiDeciderPrompt.UNLOCK_CRITERIA;
        List<Message> msgs = buildMessages(systemPrompt, turns, userInput);
        logMessages("LLM 기준 판단 프롬프트", msgs);
        try {
            return chatClient.prompt()
                    .messages(msgs)
                    .call()
                    .entity(CriteriaJudgeResponse.class);
        } catch (RuntimeException e) {
            throw new AiResponseException("AI 기준 판단 응답 처리 실패");
        }
    }

    private List<Message> buildMessages(String systemPrompt, List<ChatTurnSnapshot> turns, String userInput) {
        List<Message> msgs = new ArrayList<>();
        msgs.add(new SystemMessage(systemPrompt));
        for (ChatTurnSnapshot t : turns) {
            msgs.add(new UserMessage(t.userInput()));
            if (t.aiMessage() != null) {
                msgs.add(new AssistantMessage(t.aiMessage()));
            }
        }
        msgs.add(new UserMessage(userInput));
        return msgs;
    }

    private void logMessages(String title, List<Message> msgs) {
        if (log.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder("\n===== " + title + " =====\n");
            for (Message m : msgs) {
                sb.append("[").append(m.getMessageType()).append("] ")
                        .append(m.getText()).append("\n");
            }
            sb.append("============================");
            log.debug(sb.toString());
        }
    }

    private AiDecideResponse callAiDecider(List<Message> msgs) {
        try {
            ResponseEntity<ChatResponse, AiDecideResponse> result =
                    chatClient.prompt()
                            .messages(msgs)
                            .call()
                            .responseEntity(AiDecideResponse.class);

            Usage usage = result.response().getMetadata().getUsage();

            log.info(
                    "LLM 최종 판단 토큰: input={}, output={}, total={}",
                    usage.getPromptTokens(),
                    usage.getCompletionTokens(),
                    usage.getTotalTokens()
            );

            return result.entity();
        } catch (RuntimeException e) {
            throw new AiResponseException("AI 최종 판단 응답 처리 실패");
        }
    }

    private AiDecideResponse normalizeResponse(AiDecideResponse response, AiAction expectedAction,
                                               List<ActionStandard> failedStandards) {
        if (response == null) {
            throw new AiResponseException("AI 최종 판단 응답 처리 실패");
        }
        AiAction action = expectedAction;
        Integer proposedSec = null;
        if (expectedAction == AiAction.OFFER) {
            if (response.proposedSec() == null) {
                throw new AiResponseException("AI 최종 판단 응답 처리 실패");
            }
            if (response.proposedSec() <= 0) {
                action = AiAction.DENY;
            } else {
                proposedSec = response.proposedSec();
            }
        }
        return new AiDecideResponse(
                action,
                response.aiMessage().replace(".",""),
                response.reason(),
                proposedSec,
                failedStandards
        );
    }

    private int calculateBaseScore(CriteriaJudgeResponse criteria) {
        int score = 0;
        if (criteria.necessity()) score += 4;
        if (criteria.specificity()) score += 2;
        if (criteria.timeSuitability()) score += 2;
        if (criteria.feasibility()) score += 2;
        else if (!criteria.feasibility()) score-=10;

        return score;
    }

    private List<ActionStandard> failedStandards(CriteriaJudgeResponse criteria) {
        List<ActionStandard> failed = new ArrayList<>();
        if (!criteria.necessity()) failed.add(ActionStandard.필요성);
        if (!criteria.specificity()) failed.add(ActionStandard.구체성);
        if (!criteria.feasibility()) failed.add(ActionStandard.실행가능성);
        if (!criteria.timeSuitability()) failed.add(ActionStandard.시간적정성);
        return failed;
    }

    private String buildScoreContext(CriteriaJudgeResponse criteria,AiAction finalAction, List<ActionStandard> failedStandards) {
        return """
            [서버 계산 결과]
            necessity=%s
            specificity=%s
            feasibility=%s
            timeSuitability=%s
            criteriaReason=%s
            finalAiAction=%s
            failedStandards=%s
            """.formatted(
                criteria.necessity(),
                criteria.specificity(),
                criteria.feasibility(),
                criteria.timeSuitability(),
                criteria.reason(),
                finalAction,
                failedStandards);
    }

    private String selectPrompt(boolean hardRejectActive, RouterResult routed, boolean isNearDub) {
        if (routed.userIntent() == MessageIntent.UNLOCK_REQUEST) {
            if (hardRejectActive)return AiDeciderPrompt.UNLOCK_REJECT;
            if (isNearDub)return AiDeciderPrompt.UNLOCK_NEAR_DUP;
        }
        if(routed.userIntent()==MessageIntent.WHY) return AiDeciderPrompt.WHY;
        return AiDeciderPrompt.SMALL_TALK;
    }

    private OptionalInt maxRequestedMinutes(String input) {
        if (input == null) return OptionalInt.empty();
        Matcher matcher = MINUTE_PATTERN.matcher(input);
        int maxMinutes = 0;
        boolean found = false;
        while (matcher.find()) {
            try {
                int minutes = Integer.parseInt(matcher.group(1));
                maxMinutes = Math.max(maxMinutes, minutes);
                found = true;
            } catch (NumberFormatException e) {
                return OptionalInt.of(Integer.MAX_VALUE);
            }
        }
        return found
                ? OptionalInt.of(maxMinutes)
                : OptionalInt.empty();
    }

    private int countPreviousShortRequests(List<ChatTurnSnapshot> turns) {
        int count = 0;
        for (ChatTurnSnapshot turn : turns) {
            if (turn.messageIntent() != MessageIntent.UNLOCK_REQUEST) {
                continue;
            }
            OptionalInt minutes = maxRequestedMinutes(turn.userInput());
            if (minutes.isPresent()
                    && minutes.getAsInt() >= 1
                    && minutes.getAsInt() <= SHORT_REQUEST_MAX_MINUTES) {
                count++;
            }
        }
        return count;
    }

    private String runtimeContext(String appName, ZonedDateTime now) {
        return """
        [현재 시각: %s]
        [사용자가 잠근 앱: %s]
        """.formatted(now, appName);
    }

    @Override
    public String modelName() {
        return "gpt-5.4-mini";
    }
}
