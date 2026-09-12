package sajo.AiLock_bonTest.dto.device;

import jakarta.validation.constraints.NotBlank;

public record DeviceRegisterRequest(
        @NotBlank String nickName
) {
}
