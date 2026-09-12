package sajo.AiLock_bonTest.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import sajo.AiLock_bonTest.dto.permit.CloseRequest;
import sajo.AiLock_bonTest.dto.permit.CloseResponse;
import sajo.AiLock_bonTest.service.permit.PermitService;

@RequiredArgsConstructor
@RestController
public class CloseController {

    private  final PermitService permitService;

    @PostMapping("/permits/close")
    public CloseResponse close(
            @Valid @RequestBody CloseRequest request) {
        return permitService.close(request);
    }
}
