package sajo.AiLock_bonTest.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import sajo.AiLock_bonTest.domain.entity.ChatSession;
import sajo.AiLock_bonTest.domain.enums.SessionStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class ChatSessionRepositoryConcurrencyTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:14-alpine");

    @Autowired
    ChatSessionRepository repository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void 동일한_기대값으로_동시에_갱신하면_하나만_성공한다()
            throws Exception {

        Instant now = Instant.now();

        ChatSession session = repository.saveAndFlush(
                ChatSession.open(
                        UUID.randomUUID(),
                        1L,
                        now,
                        now.plusSeconds(3600)
                )
        );

        int requestCount = 20;
        ExecutorService executor =
                Executors.newFixedThreadPool(requestCount);

        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < requestCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();

                    TransactionTemplate tx =
                            new TransactionTemplate(transactionManager);

                    return tx.execute(status ->
                            repository.increaseTurnCountIfExpected(
                                    session.getId(),
                                    SessionStatus.ACTIVE,
                                    0
                            )
                    );
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int updatedRows = 0;

            for (Future<Integer> future : futures) {
                updatedRows += future.get(10, TimeUnit.SECONDS);
            }

            ChatSession result = repository
                    .findById(session.getId())
                    .orElseThrow();

            assertThat(updatedRows).isEqualTo(1);
            assertThat(result.getTurnCount()).isEqualTo(1);

        } finally {
            executor.shutdownNow();
        }
    }
}