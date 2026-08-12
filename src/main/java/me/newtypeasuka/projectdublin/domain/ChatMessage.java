package me.newtypeasuka.projectdublin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Table(
        name = "chat_messages",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_chat_messages_sender_client",
                columnNames = {"sender_id", "client_message_id"}
        ),
        indexes = {
                @Index(
                        name = "idx_chat_messages_sender_id",
                        columnList = "sender_id"
                ),
                @Index(
                        name = "idx_chat_messages_created_at",
                        columnList = "created_at"
                )
        }
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
public class ChatMessage {

    public static final int MAX_CONTENT_LENGTH = 300;
    public static final int CLIENT_MESSAGE_ID_LENGTH = 36;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false, updatable = false)
    private User sender;

    // WebSocket 재전송 시 같은 사용자의 메시지가 중복 저장되는 것을 방지
    @Column(
            name = "client_message_id",
            nullable = false,
            updatable = false,
            length = CLIENT_MESSAGE_ID_LENGTH
    )
    private String clientMessageId;

    @Column(
            name = "content",
            nullable = false,
            updatable = false,
            length = MAX_CONTENT_LENGTH
    )
    private String content;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 공개 채팅방에 수정할 수 없는 새 메시지를 생성
    public ChatMessage(User sender, String clientMessageId, String content) {
        this.sender = sender;
        this.clientMessageId = clientMessageId;
        this.content = content;
    }
}
