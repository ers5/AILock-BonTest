package sajo.AiLock_bonTest.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import sajo.AiLock_bonTest.dto.chat.ChatRequest;
import sajo.AiLock_bonTest.dto.chat.ChatResponse;
import sajo.AiLock_bonTest.service.chat.AiDecideService;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final AiDecideService aiDecideService;

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return aiDecideService.chat(request);
    }
}
