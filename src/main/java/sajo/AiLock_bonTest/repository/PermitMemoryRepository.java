package sajo.AiLock_bonTest.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import sajo.AiLock_bonTest.domain.entity.PermitMemory;
import sajo.AiLock_bonTest.domain.enums.PermitCloseReason;

import java.time.Instant;
import java.util.List;

public interface PermitMemoryRepository extends JpaRepository<PermitMemory, Long> {

    boolean existsByPermitId(Long permitId);

    @Query("""
        select m.id as memoryId,
               m.permitId as permitId,
               m.sessionId as sessionId,
               m.startTurnId as startTurnId,
               m.grantingTurnId as grantingTurnId,
               m.retrievalText as retrievalText,
               m.grantedSec as grantedSec,
               m.closeReason as closeReason,
               m.createdAt as createdAt
          from PermitMemory m
    """)
    List<AdminView> findAllForAdmin();

    interface AdminView {
        Long getMemoryId();
        Long getPermitId();
        Long getSessionId();
        Long getStartTurnId();
        Long getGrantingTurnId();
        String getRetrievalText();
        int getGrantedSec();
        PermitCloseReason getCloseReason();
        Instant getCreatedAt();
    }
}
