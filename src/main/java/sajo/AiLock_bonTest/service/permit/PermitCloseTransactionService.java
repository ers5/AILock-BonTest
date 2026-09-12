package sajo.AiLock_bonTest.service.permit;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sajo.AiLock_bonTest.domain.entity.App;
import sajo.AiLock_bonTest.domain.entity.ChatSession;
import sajo.AiLock_bonTest.domain.entity.ChatTurn;
import sajo.AiLock_bonTest.domain.entity.Permit;
import sajo.AiLock_bonTest.domain.enums.PermitCloseReason;
import sajo.AiLock_bonTest.domain.enums.PermitStatus;
import sajo.AiLock_bonTest.dto.permit.CloseRequest;
import sajo.AiLock_bonTest.dto.permit.PermitMemoryContext;
import sajo.AiLock_bonTest.global.exception.SessionNotFoundException;
import sajo.AiLock_bonTest.repository.AppRepository;
import sajo.AiLock_bonTest.repository.ChatSessionRepository;
import sajo.AiLock_bonTest.repository.ChatTurnRepository;
import sajo.AiLock_bonTest.repository.PermitMemoryRepository;
import sajo.AiLock_bonTest.repository.PermitRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PermitCloseTransactionService {

    private final AppRepository appRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatTurnRepository chatTurnRepository;
    private final PermitRepository permitRepository;
    private final PermitMemoryRepository permitMemoryRepository;
    private final PermitMemoryService permitMemoryService;
    private final EntityManager entityManager;

    @Transactional
    public Optional<PermitMemoryContext> close(CloseRequest request) {
        App app = appRepository
                .findByDeviceIdAndPackageName(request.deviceId(), request.packageName())
                .orElseThrow(() -> new SessionNotFoundException("활성 세션 없음"));

        List<Permit> activePermits =
                permitRepository.findLatestByDeviceAndAppAndStatus(
                        request.deviceId(),
                        app.getId(),
                        PermitStatus.ACTIVE,
                        PageRequest.of(0, 1)
                );

        if (activePermits.isEmpty()) {return Optional.empty();}

        Permit permit = activePermits.getFirst();

        ChatSession session = chatSessionRepository
                .findById(permit.getSessionId())
                .orElseThrow(() -> new IllegalStateException("permit session 없음"));

        PermitCloseReason closeReason = request.closedAt().isBefore(permit.getExpiresAt()) ? PermitCloseReason.CLOSED_EARLY : PermitCloseReason.EXPIRED_TIMEOUT;
        int updated = permitRepository.closeIfStatus(permit.getId(), PermitStatus.ACTIVE, PermitStatus.CLOSED, closeReason, request.closedAt());
        if (updated == 0) {return Optional.empty();}

        entityManager.refresh(permit);

        try {
            if (permitMemoryRepository.existsByPermitId(permit.getId())) {return Optional.empty();}

            List<ChatTurn> turns =
                    chatTurnRepository.findBySessionIdOrderByTurnIndexAsc(session.getId());

            PermitMemoryContext context = permitMemoryService.buildContext(
                    app,
                    session,
                    permit,
                    turns,
                    request.closedAt()
            );

            return Optional.of(context);
        } catch (RuntimeException e) {
            log.warn("permit memory context 생성 실패: permitId={}", permit.getId(), e);
            return Optional.empty();
        }
    }
}