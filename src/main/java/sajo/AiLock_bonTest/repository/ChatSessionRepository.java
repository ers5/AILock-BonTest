package sajo.AiLock_bonTest.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sajo.AiLock_bonTest.domain.entity.ChatSession;
import sajo.AiLock_bonTest.domain.enums.SessionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    Optional<ChatSession> findByDeviceIdAndAppIdAndStatus(UUID deviceId, Long appId, SessionStatus status);
    List<ChatSession> findByStatusAndSessionExpiresAtBefore(SessionStatus status, Instant now);
    Optional<ChatSession> findTopByDeviceIdAndAppIdAndStatusOrderByIdDesc(UUID deviceId, Long appId, SessionStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
    update ChatSession s
       set s.turnCount = s.turnCount + 1
     where s.id = :sessionId
       and s.status = :status
       and s.turnCount = :expectedTurnCount
    """)
    int increaseTurnCountIfExpected(@Param("sessionId") Long sessionId,
                                    @Param("status") SessionStatus status,
                                    @Param("expectedTurnCount") int expectedTurnCount);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ChatSession s set s.status = :newStatus, s.closedAt = :closedAt where s.id in :sessionIds and s.status = :expectedStatus")
    int endByIdsIfStatus(@Param("sessionIds") List<Long> sessionIds, @Param("expectedStatus") SessionStatus expectedStatus, @Param("newStatus") SessionStatus newStatus, @Param("closedAt") Instant closedAt);

}
