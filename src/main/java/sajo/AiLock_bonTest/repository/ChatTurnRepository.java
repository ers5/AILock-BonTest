package sajo.AiLock_bonTest.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sajo.AiLock_bonTest.domain.entity.ChatTurn;

import java.util.List;

public interface ChatTurnRepository extends JpaRepository<ChatTurn, Long> {

    List<ChatTurn> findBySessionIdOrderByTurnIndexAsc(Long sessionId);

    long countBySessionId(Long sessionId);

}
