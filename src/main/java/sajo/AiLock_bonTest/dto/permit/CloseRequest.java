package sajo.AiLock_bonTest.dto.permit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CloseRequest(
        @NotNull UUID deviceId,
        @NotBlank String packageName,
        @NotNull Instant closedAt
) {
}
