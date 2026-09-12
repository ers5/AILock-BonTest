package sajo.AiLock_bonTest.service.scheduler;

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
import sajo.AiLock_bonTest.domain.enums.SessionStatus;
import sajo.AiLock_bonTest.dto.permit.PermitMemoryContext;
import sajo.AiLock_bonTest.repository.AppRepository;
import sajo.AiLock_bonTest.repository.ChatSessionRepository;
import sajo.AiLock_bonTest.repository.ChatTurnRepository;
import sajo.AiLock_bonTest.repository.PermitRepository;
import sajo.AiLock_bonTest.service.permit.PermitMemoryService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionPermitExpiryTransactionService {

    private final ChatSessionRepository chatSessionRepository;
    private final PermitRepository permitRepository;
    private final AppRepository appRepository;
    private final ChatTurnRepository chatTurnRepository;
    private final PermitMemoryService permitMemoryService;

    @Transactional
    public List<PermitMemoryContext> expire(Instant now) {
        expireSessions(now);
        expirePermits(now);

        permitRepository.flush();
        return findPendingMemoryContexts();
    }

    private void expireSessions(Instant now) {
        List<ChatSession> expired = chatSessionRepository.findByStatusAndSessionExpiresAtBefore(SessionStatus.ACTIVE, now);

        if (expired.isEmpty()) return;

        List<Long> sessionIds = expired.stream()
                .map(ChatSession::getId)
                .toList();

        chatSessionRepository.endByIdsIfStatus(sessionIds, SessionStatus.ACTIVE, SessionStatus.ENDED, now);
        permitRepository.closeBySessionIdsIfStatusIn(sessionIds, List.of(PermitStatus.OFFERED, PermitStatus.ACTIVE), PermitStatus.CLOSED, PermitCloseReason.SESSION_ENDED, now);

        log.info("세션 만료 정리: {}건", expired.size());
    }

    private void expirePermits(Instant now) {
        int updated = permitRepository.closeExpiredIfStatus(PermitStatus.ACTIVE, PermitStatus.CLOSED, PermitCloseReason.EXPIRED_TIMEOUT, now);
        if (updated > 0) {log.info("permit 만료 정리: {}건", updated);}
    }

    private List<PermitMemoryContext> findPendingMemoryContexts() {
        List<Permit> permits = permitRepository.findMemoryPending(PermitStatus.CLOSED, PageRequest.of(0, 50));
        List<PermitMemoryContext> contexts = new ArrayList<>();

        for (Permit permit : permits) {
            try {
                ChatSession session = chatSessionRepository
                        .findById(permit.getSessionId())
                        .orElseThrow();
                App app = appRepository
                        .findById(session.getAppId())
                        .orElseThrow();
                List<ChatTurn> turns = chatTurnRepository.findBySessionIdOrderByTurnIndexAsc(session.getId());

                contexts.add(permitMemoryService.buildContext(
                        app,
                        session,
                        permit,
                        turns,
                        permit.getClosedAt()
                ));
            } catch (RuntimeException e) {log.warn("permit memory 복구 context 생성 실패: permitId={}", permit.getId(), e);}}
        return contexts;
    }
}