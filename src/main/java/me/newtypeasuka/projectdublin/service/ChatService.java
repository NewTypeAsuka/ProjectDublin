package me.newtypeasuka.projectdublin.service;

import me.newtypeasuka.projectdublin.domain.ChatMessage;
import me.newtypeasuka.projectdublin.domain.User;
import me.newtypeasuka.projectdublin.dto.ChatApiDto.MessageHistoryResponse;
import me.newtypeasuka.projectdublin.dto.ChatApiDto.MessageResponse;
import me.newtypeasuka.projectdublin.dto.ChatApiDto.SendMessageRequest;
import me.newtypeasuka.projectdublin.repository.ChatRepository;
import me.newtypeasuka.projectdublin.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ChatService {

    public static final int DEFAULT_HISTORY_SIZE = 30;
    public static final int MAX_HISTORY_SIZE = 50;
    public static final int MESSAGE_RETENTION_HOURS = 48;

    private static final ZoneId CHAT_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final ChatRepository chatRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Autowired
    public ChatService(ChatRepository chatRepository, UserRepository userRepository) {
        this(chatRepository, userRepository, Clock.system(CHAT_ZONE_ID));
    }

    // 테스트에서 48시간 경계를 고정할 수 있도록 시계를 주입
    ChatService(ChatRepository chatRepository,
                UserRepository userRepository,
                Clock clock) {
        this.chatRepository = chatRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    // 최신 채팅 메시지를 오래된 순서로 변환하여 조회
    @Transactional(readOnly = true)
    public MessageHistoryResponse getMessages(Long beforeId, int requestedSize, String email) {
        User currentUser = findUser(email);
        int size = normalizeHistorySize(requestedSize);
        LocalDateTime retentionCutoff = getRetentionCutoff();
        PageRequest pageRequest = PageRequest.of(0, size + 1);
        List<ChatMessage> queriedMessages = beforeId == null
                ? chatRepository.findLatest(retentionCutoff, pageRequest)
                : chatRepository.findBeforeId(
                        beforeId,
                        retentionCutoff,
                        pageRequest
                );

        boolean hasMore = queriedMessages.size() > size;
        List<ChatMessage> page = new ArrayList<>(
                queriedMessages.subList(0, Math.min(size, queriedMessages.size()))
        );
        Collections.reverse(page);

        List<MessageResponse> messages = page.stream()
                .map(this::toResponse)
                .toList();
        Long nextBeforeId = hasMore && !messages.isEmpty()
                ? messages.get(0).id()
                : null;

        return new MessageHistoryResponse(
                messages,
                currentUser.getId(),
                currentUser.isAdmin(),
                nextBeforeId,
                hasMore
        );
    }

    // 로그인한 사용자의 WebSocket 메시지를 검증하고 저장
    @Transactional
    public MessageResponse createMessage(SendMessageRequest request, String email) {
        User sender = userRepository.findByEmailForChatMessage(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        String clientMessageId = normalizeClientMessageId(request.clientMessageId());
        String content = normalizeContent(request.content());

        return chatRepository.findBySenderIdAndClientMessageId(
                        sender.getId(),
                        clientMessageId
                )
                .map(this::toResponse)
                .orElseGet(() -> toResponse(chatRepository.save(
                        new ChatMessage(sender, clientMessageId, content)
                )));
    }

    // 메시지 작성자 또는 관리자만 채팅 메시지를 실제 삭제
    @Transactional
    public long deleteMessage(long messageId, String email) {
        User currentUser = findUser(email);
        ChatMessage message = chatRepository.findByIdForUpdate(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        boolean owner = message.getSender().getId().equals(currentUser.getId());
        if (!owner && !currentUser.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        chatRepository.delete(message);
        return messageId;
    }

    // 매시간 최근 48시간의 보존 기간을 넘긴 채팅 메시지를 실제 삭제
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    @Transactional
    public void deleteExpiredMessages() {
        chatRepository.deleteExpiredMessages(getRetentionCutoff());
    }

    private MessageResponse toResponse(ChatMessage message) {
        User sender = message.getSender();
        return new MessageResponse(
                message.getId(),
                message.getClientMessageId(),
                sender.getId(),
                sender.getNickname(),
                sender.isAdmin(),
                message.getContent(),
                message.getCreatedAt(),
                message.getCreatedAt()
                        .atZone(CHAT_ZONE_ID)
                        .plusHours(MESSAGE_RETENTION_HOURS)
                        .toInstant()
                        .toEpochMilli()
        );
    }

    private int normalizeHistorySize(int requestedSize) {
        if (requestedSize < 1) {
            return DEFAULT_HISTORY_SIZE;
        }
        return Math.min(requestedSize, MAX_HISTORY_SIZE);
    }

    private LocalDateTime getRetentionCutoff() {
        return LocalDateTime.now(clock).minusHours(MESSAGE_RETENTION_HOURS);
    }

    private String normalizeClientMessageId(String clientMessageId) {
        if (clientMessageId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "메시지 식별자가 올바르지 않습니다."
            );
        }

        try {
            return UUID.fromString(clientMessageId.strip())
                    .toString()
                    .toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "메시지 식별자가 올바르지 않습니다."
            );
        }
    }

    private String normalizeContent(String content) {
        if (content == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "채팅 메시지를 입력해주세요."
            );
        }

        String normalized = content.strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (normalized.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "채팅 메시지를 입력해주세요."
            );
        }
        if (length > ChatMessage.MAX_CONTENT_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "채팅 메시지는 300자 이하로 작성해주세요."
            );
        }
        return normalized;
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
