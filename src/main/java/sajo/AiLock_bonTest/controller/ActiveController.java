package sajo.AiLock_bonTest.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import sajo.AiLock_bonTest.dto.permit.ActivateRequest;
import sajo.AiLock_bonTest.dto.permit.ActivateResponse;
import sajo.AiLock_bonTest.service.permit.PermitService;

@RestController
@RequiredArgsConstructor
public class ActiveController {

    private final PermitService permitService;

    @PostMapping("/permits/activate")
    public ActivateResponse activate(
            @Valid @RequestBody ActivateRequest request) {
        return permitService.activate(request);
    }
}
