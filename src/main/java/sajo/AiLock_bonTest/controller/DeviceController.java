package sajo.AiLock_bonTest.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import sajo.AiLock_bonTest.domain.entity.Device;
import sajo.AiLock_bonTest.dto.device.DeviceRegisterRequest;
import sajo.AiLock_bonTest.dto.device.DeviceRegisterResponse;
import sajo.AiLock_bonTest.service.device.DeviceService;

@RestController
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @PostMapping("/deviceRegister")
    public DeviceRegisterResponse deviceRegister(
            @Valid @RequestBody DeviceRegisterRequest request) {
        Device device = deviceService.register(request);
        return new DeviceRegisterResponse(device.getId());
    }
}
