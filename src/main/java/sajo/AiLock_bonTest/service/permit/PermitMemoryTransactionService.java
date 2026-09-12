package sajo.AiLock_bonTest.service.permit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sajo.AiLock_bonTest.domain.entity.PermitMemory;
import sajo.AiLock_bonTest.dto.permit.PermitMemoryContext;
import sajo.AiLock_bonTest.repository.EmbeddingRepository;
import sajo.AiLock_bonTest.repository.PermitMemoryRepository;

@Service
@RequiredArgsConstructor
public class PermitMemoryTransactionService {

    private final PermitMemoryRepository permitMemoryRepository;
    private final EmbeddingRepository embeddingRepository;

    @Transactional
    public void save(PermitMemoryContext context, float[] embedding) {
        if (permitMemoryRepository.existsByPermitId(context.permitId())) {
            return;
        }

        PermitMemory memory = PermitMemory.record(
                context.permitId(),
                context.deviceId(),
                context.appId(),
                context.sessionId(),
                context.startTurnId(),
                context.grantingTurnId(),
                context.retrievalText(),
                context.grantedSec(),
                context.closeReason(),
                context.createdAt()
        );

        permitMemoryRepository.saveAndFlush(memory);
        embeddingRepository.savePermitMemoryEmbedding(memory.getId(), embedding);
    }
}