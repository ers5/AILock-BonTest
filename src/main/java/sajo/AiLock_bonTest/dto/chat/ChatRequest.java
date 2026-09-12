package sajo.AiLock_bonTest.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ChatRequest(
        @NotNull UUID deviceId,
        @NotBlank String packageName,
        @NotBlank String userInput
) {
}
