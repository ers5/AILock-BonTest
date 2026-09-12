package sajo.AiLock_bonTest.service.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sajo.AiLock_bonTest.domain.entity.App;
import sajo.AiLock_bonTest.domain.entity.ChatSession;
import sajo.AiLock_bonTest.domain.entity.ChatTurn;
import sajo.AiLock_bonTest.domain.entity.Permit;
import sajo.AiLock_bonTest.domain.enums.AiAction;
import sajo.AiLock_bonTest.domain.enums.PermitCloseReason;
import sajo.AiLock_bonTest.domain.enums.PermitStatus;
import sajo.AiLock_bonTest.domain.enums.SessionStatus;
import sajo.AiLock_bonTest.dto.chat.ChatContext;
import sajo.AiLock_bonTest.dto.chat.ChatRequest;
import sajo.AiLock_bonTest.dto.chat.ChatResponse;
import sajo.AiLock_bonTest.dto.chat.ChatSaveCommand;
import sajo.AiLock_bonTest.dto.chat.ChatTurnSnapshot;
import sajo.AiLock_bonTest.global.exception.ChatConflictException;
import sajo.AiLock_bonTest.global.exception.ChatNotAllowedException;
import sajo.AiLock_bonTest.global.exception.SessionNotFoundException;
import sajo.AiLock_bonTest.repository.AppRepository;
import sajo.AiLock_bonTest.repository.ChatSessionRepository;
import sajo.AiLock_bonTest.repository.ChatTurnRepository;
import sajo.AiLock_bonTest.repository.EmbeddingRepository;
import sajo.AiLock_bonTest.repository.PermitRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChatTransactionService {

    private static final long HARD_REJECT_DURATION_SECONDS = 60 * 5;

    private final AppRepository appRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatTurnRepository chatTurnRepository;
    private final PermitRepository permitRepository;
    private final EmbeddingRepository embeddingRepository;


    @Transactional(readOnly = true)
    public ChatContext load(ChatRequest request) {
        App app = appRepository
                .findByDeviceIdAndPackageName(
                        request.deviceId(),
                        request.packageName()
                )
                .orElseThrow(() -> new SessionNotFoundException("활성 세션 없음"));

        ChatSession session = chatSessionRepository
                .findByDeviceIdAndAppIdAndStatus(
                        request.deviceId(),
                        app.getId(),
                        SessionStatus.ACTIVE
                )
                .orElseThrow(() -> new SessionNotFoundException("활성 세션 없음"));

        List<ChatTurn> turns = chatTurnRepository.findBySessionIdOrderByTurnIndexAsc(session.getId());

        List<Permit> permits = permitRepository.findBySessionId(session.getId());

        boolean activePermitExists = permits.stream()
                .anyMatch(permit ->
                        permit.getStatus() == PermitStatus.ACTIVE
                );

        if (activePermitExists) {
            throw new ChatNotAllowedException("이미 허락된 세션에선 채팅하면 안됨");
        }

        Instant now = Instant.now();
        boolean hardRejectActive = session.isHardRejectActive(now);

        List<ChatTurnSnapshot> turnSnapshots = turns.stream()
                .map(ChatTurnSnapshot::from)
                .toList();

        int boundaryTurnIndex = currentRequestBoundaryTurnIndex(permits, turns);

        return new ChatContext(
                app.getId(),
                app.getAppName(),
                session.getId(),
                session.getDeviceId(),
                session.getTurnCount(),
                hardRejectActive,
                boundaryTurnIndex,
                turnSnapshots
        );
    }

    @Transactional
    public ChatResponse save(ChatSaveCommand command) {
        int updated = chatSessionRepository.increaseTurnCountIfExpected(
                command.sessionId(),
                SessionStatus.ACTIVE,
                command.expectedTurnCount()
        );

        if (updated == 0) {throw new ChatConflictException("AI 판단 중 대화 내용이 변경됨");}

        ChatSession session = chatSessionRepository
                .findById(command.sessionId())
                .orElseThrow(() -> new SessionNotFoundException("활성 세션 없음"));

        List<Permit> permits = permitRepository.findBySessionId(session.getId());

        boolean activePermitExists = permits.stream().anyMatch(permit -> permit.getStatus() == PermitStatus.ACTIVE);

        if (activePermitExists) {throw new ChatNotAllowedException("이미 허락된 세션에선 채팅하면 안됨");}
        Instant saveNow = Instant.now();
        session.releaseHardRejectIfExpired(saveNow);
        if (command.abuseTriggered()) {session.activateHardReject(saveNow, HARD_REJECT_DURATION_SECONDS);}

        List<ChatTurn> previousTurns = chatTurnRepository.findBySessionIdOrderByTurnIndexAsc(session.getId());

        ChatTurn turn = ChatTurn.record(
                session.getId(),
                session.getDeviceId(),
                command.expectedTurnCount(),
                command.routed().userIntent(),
                command.routed().userTone(),
                command.userInput(),
                command.decision().aiAction(),
                command.decision().reason(),
                command.decision().aiMessage(),
                command.decision().proposedSec(),
                command.modelName(),
                command.createdAt(),
                command.decision().failedStandards()
        );

        chatTurnRepository.saveAndFlush(turn);

        if (command.embedding() != null && command.decision().aiAction() == AiAction.OFFER) {
            embeddingRepository.saveTurnEmbedding(turn.getId(), command.embedding());
        }

        if (command.decision().aiAction() == AiAction.OFFER) {
            Optional<Permit> offered = permits.stream()
                    .filter(permit -> permit.getStatus() == PermitStatus.OFFERED)
                    .findFirst();

            if (offered.isPresent()) {
                Permit permit = offered.get();
                int permitUpdated = permitRepository.reofferIfUnchanged(permit.getId(), PermitStatus.OFFERED, permit.getGrantedSec(), permit.getGrantingTurnId(), command.decision().proposedSec(), turn.getId());
                if (permitUpdated == 0) {throw new ChatConflictException("permit 제안 상태가 변경됨");}
            } else {
                Long startTurnId = findCurrentRequestStartTurnId(permits, previousTurns, turn);

                permitRepository.save(
                        Permit.offer(
                                session.getId(),
                                startTurnId,
                                turn.getId(),
                                command.decision().proposedSec(),
                                saveNow
                        )
                );
            }
        }

        return new ChatResponse(
                command.decision().aiMessage(),
                command.decision().proposedSec(),
                command.decision().aiAction()
        );
    }

    private int currentRequestBoundaryTurnIndex(
            List<Permit> permits,
            List<ChatTurn> turns
    ) {
        Optional<Permit> offered = permits.stream()
                .filter(permit -> permit.getStatus() == PermitStatus.OFFERED)
                .findFirst();

        if (offered.isPresent() && offered.get().getStartTurnId() != null) {
            Long startTurnId = offered.get().getStartTurnId();
            return turns.stream()
                    .filter(turn -> startTurnId.equals(turn.getId()))
                    .findFirst()
                    .map(turn -> turn.getTurnIndex() - 1)
                    .orElse(-1);
        }

        return latestPermitGrantingTurnIndex(permits, turns);
    }

    private Long findCurrentRequestStartTurnId(List<Permit> permits, List<ChatTurn> previousTurns, ChatTurn currentTurn
    ) {
        int boundaryTurnIndex = latestPermitGrantingTurnIndex(permits, previousTurns);

        return previousTurns.stream()
                .filter(turn ->
                        turn.getTurnIndex() > boundaryTurnIndex)
                .filter(turn ->
                        turn.getMessageIntent()
                                == sajo.AiLock_bonTest.domain.enums.MessageIntent.UNLOCK_REQUEST)
                .findFirst()
                .map(ChatTurn::getId)
                .orElse(currentTurn.getId());
    }

    private int latestPermitGrantingTurnIndex(List<Permit> permits, List<ChatTurn> turns) {
        int latest = -1;

        for (Permit permit : permits) {
            Long grantingTurnId = permit.getGrantingTurnId();
            if (grantingTurnId == null) {continue;}

            for (ChatTurn turn : turns) {
                if (grantingTurnId.equals(turn.getId())) {
                    latest = Math.max(latest, turn.getTurnIndex());
                    break;
                }
            }
        }

        return latest;
    }

    @Transactional
    public Optional<ChatResponse> tryEndSession(ChatRequest request) {
        App app = appRepository
                .findByDeviceIdAndPackageName(request.deviceId(), request.packageName())
                .orElse(null);

        if (app == null) return Optional.empty();

        Optional<ChatSession> active =
                chatSessionRepository.findByDeviceIdAndAppIdAndStatus(request.deviceId(), app.getId(), SessionStatus.ACTIVE);

        if (active.isPresent()) {
            ChatSession session = active.get();
            if (session.getUnlockCode() == null || !session.getUnlockCode().equals(request.userInput())) {return Optional.empty();}
            Instant now = Instant.now();
            List<Long> sessionIds = List.of(session.getId());
            chatSessionRepository.endByIdsIfStatus(sessionIds, SessionStatus.ACTIVE, SessionStatus.ENDED, now);
            permitRepository.closeBySessionIdsIfStatusIn(sessionIds, List.of(PermitStatus.OFFERED, PermitStatus.ACTIVE), PermitStatus.CLOSED, PermitCloseReason.SESSION_ENDED, now);
            return Optional.of(sessionEndResponse());
        }

        return chatSessionRepository
                .findTopByDeviceIdAndAppIdAndStatusOrderByIdDesc(
                        request.deviceId(),
                        app.getId(),
                        SessionStatus.ENDED)
                .filter(session ->
                        session.getUnlockCode() != null
                                && session.getUnlockCode()
                                .equals(request.userInput()))
                .map(session -> sessionEndResponse());
    }

    private ChatResponse sessionEndResponse() {
        return new ChatResponse(
                "잠금이 해제되었습니다.",
                null,
                AiAction.SESSION_END
        );
    }
}