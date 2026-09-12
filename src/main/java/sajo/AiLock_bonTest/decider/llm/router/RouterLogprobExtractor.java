package sajo.AiLock_bonTest.decider.llm.router;

import com.openai.models.chat.completions.ChatCompletionTokenLogprob;
import sajo.AiLock_bonTest.dto.ai.RouterRaw.IntentCode;
import sajo.AiLock_bonTest.global.exception.AiResponseException;

import java.util.*;

import java.util.*;

public final class RouterLogprobExtractor {

    private RouterLogprobExtractor() {}

    public static IntentConfidence extractIntent(List<ChatCompletionTokenLogprob> tokens) {
        ChatCompletionTokenLogprob valueToken = findIntentValueToken(tokens);

        Map<IntentCode, Double> byCode = new EnumMap<>(IntentCode.class);
        for (var top : valueToken.topLogprobs()) {
            IntentCode code = toCode(top.token());
            if (code != null) {
                byCode.merge(code, top.logprob(), Math::max);
            }
        }

        List<Map.Entry<IntentCode, Double>> ranked = byCode.entrySet().stream()
                .sorted(Map.Entry.<IntentCode, Double>comparingByValue().reversed())
                .toList();

        if (ranked.size() < 2) {
            throw new AiResponseException("AI 라우터 margin 계산 실패");
        }

        double margin = ranked.get(0).getValue() - ranked.get(1).getValue();

        return new IntentConfidence(margin, Map.copyOf(byCode));
    }

    private static ChatCompletionTokenLogprob findIntentValueToken(List<ChatCompletionTokenLogprob> tokens) {
        for (var token : tokens) {
            if (toCode(token.token()) != null) {
                return token;
            }
        }
        throw new AiResponseException("AI 라우터 분류 토큰 누락");
    }

    private static IntentCode toCode(String raw) {
        if (raw == null) return null;
        String s = raw.strip()
                .replace("\"", "")
                .replace("{", "").replace("}", "")
                .replace(":", "").replace(",", "")
                .toUpperCase(Locale.ROOT);
        return switch (s) {
            case "U" -> IntentCode.U;
            case "W" -> IntentCode.W;
            case "S" -> IntentCode.S;
            default -> null;
        };
    }

    public record IntentConfidence(
            double margin,
            Map<IntentCode, Double> logprobs
    ) {}
}