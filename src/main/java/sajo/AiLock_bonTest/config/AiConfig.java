package sajo.AiLock_bonTest.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;
@Configuration
public class AiConfig {

    @Bean
    public OpenAIClient openAiClient(@Value("${spring.ai.openai.api-key}") String apiKey) {
        return OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }

    @Bean
    public ChatClient guardChatClient(ChatClient.Builder builder) {
        return builder.clone()
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("gpt-5.4-mini")
                        .reasoningEffort("low"))
                .build();
    }

    @Bean
    public ChatClient deciderChatClient(ChatClient.Builder builder) {
        return builder.clone()
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("gpt-5.4-mini")
                        .reasoningEffort("medium"))
                .build();
    }
}
