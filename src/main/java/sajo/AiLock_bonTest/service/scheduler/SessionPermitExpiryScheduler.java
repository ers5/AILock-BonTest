package sajo.AiLock_bonTest.service.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import sajo.AiLock_bonTest.dto.permit.PermitMemoryContext;
import sajo.AiLock_bonTest.service.permit.PermitMemoryService;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionPermitExpiryScheduler {

    private final SessionPermitExpiryTransactionService expiryTransactionService;
    private final PermitMemoryService permitMemoryService;

    @Scheduled(fixedDelay = 60000)
    public void poll() {
        Instant now = Instant.now();

        List<PermitMemoryContext> contexts =
                expiryTransactionService.expire(now);

        for (PermitMemoryContext context : contexts) {
            try {permitMemoryService.create(context);} catch (Exception e) {
                log.warn(
                        "permit memory 생성 실패: permitId={}",
                        context.permitId(),
                        e
                );
            }
        }
    }
}