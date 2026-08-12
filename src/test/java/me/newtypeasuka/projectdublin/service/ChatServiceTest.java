package me.newtypeasuka.projectdublin.service;

import me.newtypeasuka.projectdublin.domain.ChatMessage;
import me.newtypeasuka.projectdublin.domain.User;
import me.newtypeasuka.projectdublin.dto.ChatApiDto.MessageHistoryResponse;
import me.newtypeasuka.projectdublin.dto.ChatApiDto.MessageResponse;
import me.newtypeasuka.projectdublin.dto.ChatApiDto.SendMessageRequest;
import me.newtypeasuka.projectdublin.repository.ChatRepository;
import me.newtypeasuka.projectdublin.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@SpringBootTest
class ChatServiceTest {

    @Autowired
    ChatService chatService;

    @Autowired
    ChatRepository chatRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    User admin;
    User member;
    User other;
    LocalDateTime fixedNow;

    @BeforeEach
    void setUp() {
        ZoneId chatZoneId = ZoneId.of("Asia/Seoul");
        fixedNow = LocalDateTime.of(2026, 8, 12, 12, 0);
        chatService = new ChatService(
                chatRepository,
                userRepository,
                Clock.fixed(fixedNow.atZone(chatZoneId).toInstant(), chatZoneId)
        );
        admin = saveUser("chat-admin@example.com", "채팅관리자", 1);
        member = saveUser("chat-member@example.com", "채팅회원", 2);
        other = saveUser("chat-other@example.com", "다른회원", 2);
    }

    @DisplayName("채팅 메시지를 평문으로 저장하고 같은 요청을 다시 보내도 중복 생성하지 않는다")
    @Test
    void createMessageIdempotently() {
        String clientMessageId = UUID.randomUUID().toString();
        SendMessageRequest request = request(clientMessageId, "  안녕하세요 !*$ 😀  ");

        MessageResponse first = chatService.createMessage(request, member.getEmail());
        MessageResponse duplicate = chatService.createMessage(request, member.getEmail());

        assertThat(duplicate.id()).isEqualTo(first.id());
        assertThat(chatRepository.count()).isEqualTo(1);
        assertThat(first.content()).isEqualTo("안녕하세요 !*$ 😀");
        assertThat(first.senderId()).isEqualTo(member.getId());
        assertThat(first.senderNickname()).isEqualTo(member.getNickname());
        assertThat(first.senderAdmin()).isFalse();
        assertThat(first.createdAt()).isNotNull();
        assertThat(first.expiresAtEpochMillis()).isEqualTo(
                first.createdAt()
                        .atZone(ZoneId.of("Asia/Seoul"))
                        .plusHours(ChatService.MESSAGE_RETENTION_HOURS)
                        .toInstant()
                        .toEpochMilli()
        );
    }

    @DisplayName("최신 메시지를 30개씩 조회하고 각 묶음은 오래된 순서로 반환한다")
    @Test
    void getMessageHistoryOldestFirst() {
        for (int index = 1; index <= 35; index++) {
            chatService.createMessage(
                    request(UUID.randomUUID().toString(), "메시지 " + index),
                    member.getEmail()
            );
        }
        MessageResponse expiredLatest = createMessage(other, "만료된 최신 ID 메시지");
        updateCreatedAt(expiredLatest.id(), fixedNow.minusHours(49));

        MessageHistoryResponse latest = chatService.getMessages(
                null,
                ChatService.DEFAULT_HISTORY_SIZE,
                other.getEmail()
        );
        MessageHistoryResponse previous = chatService.getMessages(
                latest.nextBeforeId(),
                ChatService.DEFAULT_HISTORY_SIZE,
                other.getEmail()
        );

        assertThat(latest.messages()).hasSize(30);
        assertThat(latest.messages().get(0).content()).isEqualTo("메시지 6");
        assertThat(latest.messages().get(29).content()).isEqualTo("메시지 35");
        assertThat(latest.hasMore()).isTrue();
        assertThat(latest.nextBeforeId()).isEqualTo(latest.messages().get(0).id());
        assertThat(latest.currentUserId()).isEqualTo(other.getId());
        assertThat(latest.currentUserAdmin()).isFalse();

        assertThat(previous.messages()).extracting(MessageResponse::content)
                .containsExactly(
                        "메시지 1",
                        "메시지 2",
                        "메시지 3",
                        "메시지 4",
                        "메시지 5"
                );
        assertThat(previous.hasMore()).isFalse();
        assertThat(previous.nextBeforeId()).isNull();
    }

    @DisplayName("최근 48시간 이내의 채팅 메시지만 이력에 표시한다")
    @Test
    void showOnlyMessagesWithinRetentionPeriod() {
        MessageResponse recent = createMessage(member, "47시간 전 메시지");
        MessageResponse expired = createMessage(member, "49시간 전 메시지");
        updateCreatedAt(recent.id(), fixedNow.minusHours(47));
        updateCreatedAt(expired.id(), fixedNow.minusHours(49));

        MessageHistoryResponse history = chatService.getMessages(
                null,
                ChatService.DEFAULT_HISTORY_SIZE,
                member.getEmail()
        );

        assertThat(history.messages()).extracting(MessageResponse::id)
                .containsExactly(recent.id());
        assertThat(history.hasMore()).isFalse();
        assertThat(chatRepository.existsById(expired.id())).isTrue();
    }

    @DisplayName("매시간 정리 작업은 48시간이 지난 메시지만 실제 삭제한다")
    @Test
    void deleteOnlyExpiredMessages() {
        MessageResponse recent = createMessage(member, "보존할 메시지");
        MessageResponse expired = createMessage(other, "삭제할 메시지");
        updateCreatedAt(recent.id(), fixedNow.minusHours(48));
        updateCreatedAt(expired.id(), fixedNow.minusHours(48).minusSeconds(1));

        chatService.deleteExpiredMessages();
        chatService.deleteExpiredMessages(); // 여러 인스턴스의 반복 실행과 같은 멱등성 확인

        assertThat(chatRepository.existsById(recent.id())).isTrue();
        assertThat(chatRepository.existsById(expired.id())).isFalse();
    }

    @DisplayName("채팅 메시지는 공백일 수 없고 최대 300자까지만 저장한다")
    @Test
    void validateMessageContent() {
        MessageResponse maximum = chatService.createMessage(
                request(UUID.randomUUID().toString(), "가".repeat(300)),
                member.getEmail()
        );

        assertThat(maximum.content()).hasSize(ChatMessage.MAX_CONTENT_LENGTH);
        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> chatService.createMessage(
                        request(UUID.randomUUID().toString(), "가".repeat(301)),
                        member.getEmail()
                )
        );
        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> chatService.createMessage(
                        request(UUID.randomUUID().toString(), "  \n\t  "),
                        member.getEmail()
                )
        );
        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> chatService.createMessage(
                        request("잘못된-id", "메시지"),
                        member.getEmail()
                )
        );
    }

    @DisplayName("작성자와 관리자는 메시지를 삭제하고 다른 사용자는 삭제할 수 없다")
    @Test
    void authorizeMessageDeletion() {
        MessageResponse ownerMessage = createMessage(member, "작성자 메시지");

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> chatService.deleteMessage(ownerMessage.id(), other.getEmail())
        );
        assertThat(chatRepository.existsById(ownerMessage.id())).isTrue();

        chatService.deleteMessage(ownerMessage.id(), member.getEmail());
        assertThat(chatRepository.existsById(ownerMessage.id())).isFalse();

        MessageResponse adminTarget = createMessage(other, "관리자 삭제 대상");
        chatService.deleteMessage(adminTarget.id(), admin.getEmail());
        assertThat(chatRepository.existsById(adminTarget.id())).isFalse();
    }

    @DisplayName("존재하지 않는 사용자와 메시지는 인증 오류와 404로 구분한다")
    @Test
    void rejectUnknownUserAndMessage() {
        assertStatus(
                HttpStatus.UNAUTHORIZED,
                () -> chatService.getMessages(null, 30, "missing@example.com")
        );
        assertStatus(
                HttpStatus.NOT_FOUND,
                () -> chatService.deleteMessage(Long.MAX_VALUE, member.getEmail())
        );
    }

    private MessageResponse createMessage(User sender, String content) {
        return chatService.createMessage(
                request(UUID.randomUUID().toString(), content),
                sender.getEmail()
        );
    }

    private SendMessageRequest request(String clientMessageId, String content) {
        return new SendMessageRequest(clientMessageId, content);
    }

    private void updateCreatedAt(long messageId, LocalDateTime createdAt) {
        chatRepository.flush();
        jdbcTemplate.update(
                "update chat_messages set created_at = ? where id = ?",
                Timestamp.valueOf(createdAt),
                messageId
        );
    }

    private User saveUser(String email, String nickname, int role) {
        return userRepository.save(User.builder()
                .email(email)
                .name(nickname)
                .nickname(nickname)
                .role(role)
                .build());
    }

    private void assertStatus(HttpStatus expectedStatus, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(expectedStatus)
                );
    }
}
