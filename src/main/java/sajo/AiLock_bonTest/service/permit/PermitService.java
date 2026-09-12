package sajo.AiLock_bonTest.service.permit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sajo.AiLock_bonTest.domain.entity.App;
import sajo.AiLock_bonTest.domain.entity.ChatSession;
import sajo.AiLock_bonTest.domain.entity.ChatTurn;
import sajo.AiLock_bonTest.domain.entity.Permit;
import sajo.AiLock_bonTest.domain.enums.PermitStatus;
import sajo.AiLock_bonTest.domain.enums.SessionStatus;
import sajo.AiLock_bonTest.dto.permit.*;
import sajo.AiLock_bonTest.global.exception.PermitNotFoundException;
import sajo.AiLock_bonTest.global.exception.SessionNotFoundException;
import sajo.AiLock_bonTest.repository.AppRepository;
import sajo.AiLock_bonTest.repository.ChatSessionRepository;
import sajo.AiLock_bonTest.repository.ChatTurnRepository;
import sajo.AiLock_bonTest.repository.PermitRepository;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PermitService {

    private final ChatSessionRepository chatSessionRepository;
    private final PermitRepository permitRepository;
    private final AppRepository appRepository;
    private final ChatTurnRepository chatTurnRepository;
    private final PermitMemoryService permitMemoryService;
    private final PermitCloseTransactionService permitCloseTransactionService;

    @Transactional
    public ActivateResponse activate(ActivateRequest request) {
        App app = appRepository.findByDeviceIdAndPackageName(request.deviceId(), request.packageName())
                .orElseThrow(() -> new SessionNotFoundException("활성 세션 없음"));
        ChatSession session = chatSessionRepository
                .findByDeviceIdAndAppIdAndStatus(request.deviceId(), app.getId(), SessionStatus.ACTIVE)
                .orElseThrow(() -> new SessionNotFoundException("활성 세션 없음"));

        Permit permit = permitRepository.findBySessionIdAndStatus(session.getId(), PermitStatus.OFFERED)
                .orElseThrow(() -> new PermitNotFoundException("활성화할 제안 permit 없음"));

        ChatTurn turn = chatTurnRepository.findById(permit.getGrantingTurnId())
                .orElseThrow(() -> new IllegalStateException("granting turn 없음"));

        int updated = permitRepository.activateIfUnchanged(permit.getId(), PermitStatus.OFFERED, permit.getGrantedSec(), permit.getGrantingTurnId(), PermitStatus.ACTIVE, request.startedAt().plusSeconds(permit.getGrantedSec()));

        if (updated == 1) {
            log.debug("임베딩 활성화 message: {}", turn.getUserInput());
            turn.activateEmbedding();
        }

        return new ActivateResponse(updated == 1);
    }

    public CloseResponse close(CloseRequest request) {
        Optional<PermitMemoryContext> context =
                permitCloseTransactionService.close(request);

        context.ifPresent(memoryContext -> {
            try {
                permitMemoryService.create(memoryContext);
            } catch (Exception e) {
                log.warn(
                        "permit memory 생성 실패: permitId={}",
                        memoryContext.permitId(),
                        e
                );
            }
        });

        return new CloseResponse(true);
    }
}
