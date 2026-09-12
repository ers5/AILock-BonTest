package sajo.AiLock_bonTest.decider.llm.router;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.models.ResponseFormatJsonSchema;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionTokenLogprob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import sajo.AiLock_bonTest.decider.MessageRouter;
import sajo.AiLock_bonTest.decider.prompt.RouterPrompt;
import sajo.AiLock_bonTest.domain.enums.MessageIntent;
import sajo.AiLock_bonTest.dto.ai.RouterRaw;
import sajo.AiLock_bonTest.dto.ai.RouterResult;
import sajo.AiLock_bonTest.dto.chat.ChatTurnSnapshot;
import sajo.AiLock_bonTest.global.exception.AiResponseException;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@Profile("llm")
@RequiredArgsConstructor
public class LlmRouter implements MessageRouter {

    private static final String MODEL = "gpt-4.1-mini";
    private static final int TOP_LOGPROBS = 3;

    private final OpenAIClient openAiClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public RouterResult route(String appName, String userInput, List<ChatTurnSnapshot> chatTurns) {
        ChatCompletionCreateParams.Builder base = ChatCompletionCreateParams.builder()
                .model(MODEL)
                .temperature(0.0)
                .logprobs(true)
                .topLogprobs(TOP_LOGPROBS)
                .responseFormat(routerSchema())
                .addSystemMessage(RouterPrompt.routerPrompt)
                .addSystemMessage("현재 분류 대상 앱: " + appName);

        for (ChatTurnSnapshot turn : chatTurns) {
            base.addUserMessage(turn.userInput());
            if (turn.aiMessage() != null) {base.addAssistantMessage(turn.aiMessage());}
        }
        base.addUserMessage(userInput);

        ChatCompletion completion;
        try {
            completion = openAiClient.chat().completions().create(base.build());
        } catch (RuntimeException e) {
            log.error("AI 라우터 호출 실패", e);
            throw new AiResponseException("AI 라우터 응답 처리 실패");
        }

        var choice = completion.choices()
                .stream()
                .findFirst()
                .orElseThrow(() -> new AiResponseException("AI 라우터 choices 누락"));

        String json = choice.message().content().orElseThrow(() -> new AiResponseException("AI 라우터 응답 처리 실패"));

        RouterRaw raw;
        try {raw = objectMapper.readValue(json, RouterRaw.class);}
        catch (Exception e) {throw new AiResponseException("AI 라우터 응답 처리 실패");}

        double intentMargin;

        try {
            List<ChatCompletionTokenLogprob> tokens = choice.logprobs().flatMap(lp -> lp.content()).orElseThrow(() -> new AiResponseException("AI 라우터 logprobs 누락"));
            RouterLogprobExtractor.IntentConfidence conf = RouterLogprobExtractor.extractIntent(tokens);
            logScores(raw, conf);
            intentMargin = conf.margin();
        } catch (AiResponseException e) {
            log.warn("라우터 confidence 추출 실패, raw intent 사용: intent={}", raw.intent(), e);
            intentMargin = 0.0;
        }
        MessageIntent intent = RouterCodeMapper.toIntent(raw.intent());

        if (intentMargin <= 2.2
                && (intent == MessageIntent.UNLOCK_REQUEST
                || intent == MessageIntent.SMALL_TALK)) {
            intent = MessageIntent.AMBIGUOUS;
        }

        return new RouterResult(
                intent,
                RouterCodeMapper.toTone(raw.tone()),
                intentMargin
        );
    }

    private ResponseFormatJsonSchema routerSchema() {
        var schema = ResponseFormatJsonSchema.JsonSchema.Schema.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                        "intent", Map.of("type", "string", "enum", List.of("U", "W", "S")),
                        "tone", Map.of("type", "string", "enum", List.of("A", "N"))
                )))
                .putAdditionalProperty("required", JsonValue.from(List.of("intent", "tone")))
                .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                .build();

        return ResponseFormatJsonSchema.builder()
                .jsonSchema(ResponseFormatJsonSchema.JsonSchema.builder()
                        .name("router_result")
                        .strict(true)
                        .schema(schema)
                        .build())
                .build();
    }

    private void logScores(RouterRaw raw, RouterLogprobExtractor.IntentConfidence conf) {
        if (!log.isDebugEnabled()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        conf.logprobs().forEach((code, lp) ->
                sb.append(code)
                        .append("=")
                        .append(String.format("%.2f%%", Math.exp(lp) * 100))
                        .append(" "));
        log.debug("\n라우터 동작\nRouter intent={} tone={} | dist=[{}] margin={}",
                raw.intent(), raw.tone(), sb.toString().strip(), String.format("%.4f", conf.margin()));
    }
}