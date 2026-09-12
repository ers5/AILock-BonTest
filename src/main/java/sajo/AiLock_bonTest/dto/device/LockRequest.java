package sajo.AiLock_bonTest.dto.device;

import java.time.Instant;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record LockRequest(
        @NotNull UUID deviceId,
        @NotBlank String packageName,
        @NotBlank String appName,
        @Positive int lockSecond,
        @NotNull Instant lockedAt
) {}
