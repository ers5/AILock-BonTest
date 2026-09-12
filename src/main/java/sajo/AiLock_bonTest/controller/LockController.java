package sajo.AiLock_bonTest.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import sajo.AiLock_bonTest.dto.device.LockRequest;
import sajo.AiLock_bonTest.dto.device.LockResponse;
import sajo.AiLock_bonTest.service.device.LockService;

@RestController
@RequiredArgsConstructor
public class LockController {

    private final LockService lockService;

    @PostMapping("/lockApp")
    public LockResponse lockRequest(
            @Valid @RequestBody LockRequest request) {
        return lockService.lock(request);
    }
}
