package sajo.AiLock_bonTest.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sajo.AiLock_bonTest.domain.entity.Permit;
import sajo.AiLock_bonTest.domain.enums.PermitCloseReason;
import sajo.AiLock_bonTest.domain.enums.PermitStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PermitRepository extends JpaRepository<Permit, Long> {


    Optional<Permit> findBySessionIdAndStatus(Long sessionId, PermitStatus status);
    List<Permit> findBySessionId(Long sessionId);
    List<Permit> findByStatusAndExpiresAtBefore(PermitStatus status, Instant now);
    List<Permit> findBySessionIdInAndStatus(List<Long> sessionIds, PermitStatus status);

    @Query("""
    select p
    from Permit p
    where p.status = :status
      and p.expiresAt is not null
      and p.closedAt is not null
      and not exists (
          select pm.id
          from PermitMemory pm
          where pm.permitId = p.id
      )
    order by p.closedAt asc
""")
    List<Permit> findMemoryPending(
            @Param("status") PermitStatus status,
            Pageable pageable
    );

    @Query("""
    select p
    from Permit p
    where p.status = :status
      and p.sessionId in (
          select s.id
          from ChatSession s
          where s.deviceId = :deviceId
            and s.appId = :appId
      )
    order by p.id desc
""")
    List<Permit> findLatestByDeviceAndAppAndStatus(
            @Param("deviceId") UUID deviceId,
            @Param("appId") Long appId,
            @Param("status") PermitStatus status,
            Pageable pageable
    );

    @Modifying(flushAutomatically = true)
    @Query("update Permit p set p.grantedSec = :newGrantedSec, p.grantingTurnId = :newGrantingTurnId where p.id = :id and p.status = :expectedStatus and p.grantedSec = :expectedGrantedSec and p.grantingTurnId = :expectedGrantingTurnId")
    int reofferIfUnchanged(@Param("id") Long id, @Param("expectedStatus") PermitStatus expectedStatus, @Param("expectedGrantedSec") int expectedGrantedSec, @Param("expectedGrantingTurnId") Long expectedGrantingTurnId, @Param("newGrantedSec") int newGrantedSec, @Param("newGrantingTurnId") Long newGrantingTurnId);

    @Modifying(flushAutomatically = true)
    @Query("update Permit p set p.status = :newStatus, p.expiresAt = :expiresAt where p.id = :id and p.status = :expectedStatus and p.grantedSec = :expectedGrantedSec and p.grantingTurnId = :expectedGrantingTurnId")
    int activateIfUnchanged(@Param("id") Long id, @Param("expectedStatus") PermitStatus expectedStatus, @Param("expectedGrantedSec") int expectedGrantedSec, @Param("expectedGrantingTurnId") Long expectedGrantingTurnId, @Param("newStatus") PermitStatus newStatus, @Param("expiresAt") Instant expiresAt);

    @Modifying(flushAutomatically = true)
    @Query("update Permit p set p.status = :newStatus, p.closeReason = :closeReason, p.closedAt = :closedAt where p.id = :id and p.status = :expectedStatus")
    int closeIfStatus(@Param("id") Long id, @Param("expectedStatus") PermitStatus expectedStatus, @Param("newStatus") PermitStatus newStatus, @Param("closeReason") PermitCloseReason closeReason, @Param("closedAt") Instant closedAt);

    @Modifying(flushAutomatically = true)
    @Query("update Permit p set p.status = :newStatus, p.closeReason = :closeReason, p.closedAt = :closedAt where p.sessionId in :sessionIds and p.status in :expectedStatuses")
    int closeBySessionIdsIfStatusIn(@Param("sessionIds") List<Long> sessionIds, @Param("expectedStatuses") List<PermitStatus> expectedStatuses, @Param("newStatus") PermitStatus newStatus, @Param("closeReason") PermitCloseReason closeReason, @Param("closedAt") Instant closedAt);
    @Modifying(flushAutomatically = true)
    @Query("update Permit p set p.status = :newStatus, p.closeReason = :closeReason, p.closedAt = :now where p.status = :expectedStatus and p.expiresAt < :now")    int closeExpiredIfStatus(@Param("expectedStatus") PermitStatus expectedStatus, @Param("newStatus") PermitStatus newStatus, @Param("closeReason") PermitCloseReason closeReason, @Param("now") Instant now);

}
