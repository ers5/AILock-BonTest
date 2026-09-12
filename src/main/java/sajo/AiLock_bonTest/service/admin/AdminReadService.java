package sajo.AiLock_bonTest.service.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sajo.AiLock_bonTest.domain.entity.*;
import sajo.AiLock_bonTest.domain.enums.PermitStatus;
import sajo.AiLock_bonTest.domain.enums.SessionStatus;
import sajo.AiLock_bonTest.dto.admin.AdminDataResponse;
import sajo.AiLock_bonTest.repository.*;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminReadService {

    private final DeviceRepository deviceRepository;
    private final AppRepository appRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatTurnRepository chatTurnRepository;
    private final PermitRepository permitRepository;
    private final PermitMemoryRepository permitMemoryRepository;

    @Transactional(readOnly = true)
    public AdminDataResponse data() {
        return loadData(Instant.now());
    }

    private AdminDataResponse loadData(Instant now) {
        List<Device> devices = deviceRepository.findAll();
        List<ChatSession> sessions = chatSessionRepository.findAll();
        List<ChatTurn> turns = chatTurnRepository.findAll();
        List<Permit> permits = permitRepository.findAll();
        List<PermitMemoryRepository.AdminView> memories =
                permitMemoryRepository.findAllForAdmin();

        Map<Long, App> appsById = appRepository.findAll().stream()
                .collect(Collectors.toMap(App::getId, Function.identity()));

        Map<UUID, List<ChatSession>> sessionsByDevice =
                group(sessions, ChatSession::getDeviceId);

        Map<Long, List<ChatTurn>> turnsBySession =
                group(turns, ChatTurn::getSessionId);

        Map<Long, List<Permit>> permitsBySession =
                group(permits, Permit::getSessionId);

        Map<Long, List<PermitMemoryRepository.AdminView>> memoriesBySession =
                group(memories, PermitMemoryRepository.AdminView::getSessionId);

        List<AdminDataResponse.DeviceData> deviceData = new ArrayList<>();

        for (Device device : devices) {
            List<ChatSession> deviceSessions = new ArrayList<>(
                    sessionsByDevice.getOrDefault(device.getId(), List.of())
            );

            deviceSessions.sort(
                    Comparator.comparing(
                                    (ChatSession session) ->
                                            session.getStatus() != SessionStatus.ACTIVE
                            )
                            .thenComparing(
                                    ChatSession::getOpenedAt,
                                    Comparator.nullsLast(Comparator.reverseOrder())
                            )
            );

            List<AdminDataResponse.SessionData> sessionData = new ArrayList<>();
            Instant lastActivityAt = null;

            for (ChatSession session : deviceSessions) {
                App app = appsById.get(session.getAppId());

                List<ChatTurn> sessionTurns = new ArrayList<>(
                        turnsBySession.getOrDefault(session.getId(), List.of())
                );
                sessionTurns.sort(Comparator.comparingInt(ChatTurn::getTurnIndex));

                List<Permit> sessionPermits = new ArrayList<>(
                        permitsBySession.getOrDefault(session.getId(), List.of())
                );
                sessionPermits.sort(Comparator.comparing(Permit::getId));

                List<PermitMemoryRepository.AdminView> sessionMemories =
                        new ArrayList<>(
                                memoriesBySession.getOrDefault(
                                        session.getId(),
                                        List.of()
                                )
                        );
                sessionMemories.sort(
                        Comparator.comparing(
                                PermitMemoryRepository.AdminView::getCreatedAt
                        )
                );

                List<AdminDataResponse.TurnData> turnData =
                        sessionTurns.stream()
                                .map(turn -> new AdminDataResponse.TurnData(
                                        turn.getId(),
                                        turn.getTurnIndex(),
                                        turn.getMessageIntent(),
                                        turn.getMessageTone(),
                                        turn.getUserInput(),
                                        turn.getAiAction(),
                                        turn.getAiReason(),
                                        turn.getAiMessage(),
                                        turn.getProposedSec(),
                                        turn.getModelName(),
                                        turn.getCreatedAt(),
                                        turn.isEmbedding(),
                                        turn.getFailedStandards() == null
                                                ? List.of()
                                                : List.copyOf(
                                                        turn.getFailedStandards()
                                                )
                                ))
                                .toList();

                List<AdminDataResponse.PermitData> permitData =
                        sessionPermits.stream()
                                .map(permit ->
                                        new AdminDataResponse.PermitData(
                                                permit.getId(),
                                                permit.getStartTurnId(),
                                                permit.getGrantingTurnId(),
                                                permit.getGrantedSec(),
                                                permit.getIssuedAt(),
                                                permit.getExpiresAt(),
                                                permit.getStatus(),
                                                permit.getCloseReason(),
                                                permit.getClosedAt()
                                        )
                                )
                                .toList();

                List<AdminDataResponse.PermitMemoryData> memoryData =
                        sessionMemories.stream()
                                .map(memory ->
                                        new AdminDataResponse.PermitMemoryData(
                                                memory.getMemoryId(),
                                                memory.getPermitId(),
                                                memory.getStartTurnId(),
                                                memory.getGrantingTurnId(),
                                                memory.getRetrievalText(),
                                                memory.getGrantedSec(),
                                                memory.getCloseReason(),
                                                memory.getCreatedAt()
                                        )
                                )
                                .toList();

                sessionData.add(new AdminDataResponse.SessionData(
                        session.getId(),
                        session.getDeviceId(),
                        session.getAppId(),
                        app == null ? null : app.getAppName(),
                        app == null ? null : app.getPackageName(),
                        session.getStatus(),
                        session.getUnlockCode(),
                        session.getTurnCount(),
                        session.getOpenedAt(),
                        session.getSessionExpiresAt(),
                        session.getClosedAt(),
                        session.getHardRejectUntil(),
                        session.isHardRejectActive(now),
                        turnData,
                        permitData,
                        memoryData
                ));

                lastActivityAt = latest(lastActivityAt, session.getOpenedAt());
                lastActivityAt = latest(lastActivityAt, session.getClosedAt());

                for (ChatTurn turn : sessionTurns) {
                    lastActivityAt = latest(
                            lastActivityAt,
                            turn.getCreatedAt()
                    );
                }

                for (Permit permit : sessionPermits) {
                    lastActivityAt = latest(
                            lastActivityAt,
                            permit.getIssuedAt()
                    );
                    lastActivityAt = latest(
                            lastActivityAt,
                            permit.getClosedAt()
                    );
                }
            }

            long activeSessionCount = deviceSessions.stream()
                    .filter(session ->
                            session.getStatus() == SessionStatus.ACTIVE
                    )
                    .count();

            deviceData.add(new AdminDataResponse.DeviceData(
                    device.getId(),
                    device.getNickName(),
                    device.getTotalSessionCount(),
                    activeSessionCount,
                    lastActivityAt,
                    List.copyOf(sessionData)
            ));
        }

        deviceData.sort(
                Comparator.comparing(
                        AdminDataResponse.DeviceData::lastActivityAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                )
        );

        AdminDataResponse.Summary summary =
                new AdminDataResponse.Summary(
                        devices.size(),
                        sessions.stream()
                                .filter(session ->
                                        session.getStatus()
                                                == SessionStatus.ACTIVE
                                )
                                .count(),
                        permits.stream()
                                .filter(permit ->
                                        permit.getStatus()
                                                == PermitStatus.ACTIVE
                                )
                                .count(),
                        sessions.stream()
                                .filter(session ->
                                        session.isHardRejectActive(now)
                                )
                                .count(),
                        turns.size(),
                        memories.size()
                );

        return new AdminDataResponse(
                now,
                summary,
                List.copyOf(deviceData)
        );
    }

    private Instant latest(Instant current, Instant candidate) {
        if (candidate == null) return current;
        if (current == null || candidate.isAfter(current)) return candidate;
        return current;
    }

    private <K, T> Map<K, List<T>> group(
            List<T> values,
            Function<T, K> classifier
    ) {
        return values.stream()
                .collect(Collectors.groupingBy(classifier));
    }
}