package sajo.AiLock_bonTest.domain.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import sajo.AiLock_bonTest.domain.enums.ActionStandard;
import sajo.AiLock_bonTest.domain.enums.AiAction;
import sajo.AiLock_bonTest.domain.enums.MessageIntent;
import sajo.AiLock_bonTest.domain.enums.MessageTone;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "chat_turns",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_turn_session_index",
        columnNames = {"session_id", "turn_index"}
    ),
    indexes = @Index(name = "idx_turn_device_created", columnList = "device_id, created_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatTurn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "device_id", nullable = false, columnDefinition = "uuid")
    private UUID deviceId;

    @Column(name = "turn_index", nullable = false)
    private int turnIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_intent", nullable = false)
    private MessageIntent messageIntent;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_tone", nullable = false)
    private MessageTone messageTone;

    @Column(name = "user_input", nullable = false, columnDefinition = "text")
    private String userInput;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_action", nullable = false)
    private AiAction aiAction;

    @Column(name = "ai_reason", columnDefinition = "text")
    private String aiReason;

    @Column(name="ai_message",columnDefinition = "text")
    private String aiMessage;

    @Column(name = "proposed_sec")
    private Integer proposedSec;

    @Column(name = "model_name")
    private String modelName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name="is_embedding")
    private boolean isEmbedding=false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "failed_standards", columnDefinition = "jsonb")
    private List<ActionStandard> failedStandards;

    private ChatTurn(Long sessionId, UUID deviceId, int turnIndex,
                     MessageIntent messageIntent, MessageTone messageTone, String userInput,
                     AiAction aiAction, String aiReason,String aiMessage, Integer proposedSec,
                     String modelName, Instant createdAt, List<ActionStandard> failedStandards) {
        this.sessionId = sessionId;
        this.deviceId = deviceId;
        this.turnIndex = turnIndex;
        this.messageIntent = messageIntent;
        this.messageTone = messageTone;
        this.userInput = userInput;
        this.aiAction = aiAction;
        this.aiReason = aiReason;
        this.aiMessage = aiMessage;
        this.proposedSec = proposedSec;
        this.modelName = modelName;
        this.createdAt = createdAt;
        this.failedStandards = failedStandards;
    }

    public void activateEmbedding(){this.isEmbedding=true;}

    public static ChatTurn record(Long sessionId, UUID deviceId, int turnIndex,
                                  MessageIntent messageIntent, MessageTone messageTone, String userInput,
                                  AiAction aiAction, String aiReason,String aiMessage, Integer proposedSec,
                                  String modelName, Instant createdAt, List<ActionStandard> failedStandards) {
        return new ChatTurn(sessionId, deviceId, turnIndex,
                messageIntent, messageTone, userInput, aiAction, aiReason,aiMessage, proposedSec,
                modelName, createdAt, failedStandards);
    }
}
