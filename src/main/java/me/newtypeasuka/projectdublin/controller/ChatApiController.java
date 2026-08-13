package me.newtypeasuka.projectdublin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.newtypeasuka.projectdublin.dto.ChatApiDto.ChatErrorResponse;
import me.newtypeasuka.projectdublin.dto.ChatApiDto.ChatEvent;
import me.newtypeasuka.projectdublin.dto.ChatApiDto.MessageHistoryResponse;
import me.newtypeasuka.projectdublin.dto.ChatApiDto.MessageResponse;
import me.newtypeasuka.projectdublin.dto.ChatApiDto.SendMessageRequest;
import me.newtypeasuka.projectdublin.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/menu/chat/messages")
public class ChatApiController {

    private static final String CHAT_TOPIC = "/topic/chat";

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    // 공개 채팅방의 최신 메시지 또는 이전 메시지 이력 조회 API
    @GetMapping
    public ResponseEntity<MessageHistoryResponse> getMessages(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "30") int size,
            Principal principal
    ) {
        return ResponseEntity.ok(chatService.getMessages(
                beforeId,
                size,
                principal.getName()
        ));
    }

    // 작성자 또는 관리자의 채팅 메시지 삭제 API
    @DeleteMapping("/{messageId}")
    public ResponseEntity<ChatEvent> deleteMessage(
            @PathVariable long messageId,
            Principal principal
    ) {
        long deletedMessageId = chatService.deleteMessage(
                messageId,
                principal.getName()
        );
        ChatEvent event = ChatEvent.deleted(deletedMessageId);
        messagingTemplate.convertAndSend(CHAT_TOPIC, event);
        return ResponseEntity.ok(event);
    }

    // 인증된 STOMP 사용자의 메시지를 저장한 뒤 전체 채팅 구독자에게 발행
    @MessageMapping("/chat/messages")
    public void createMessage(
            @Valid @Payload SendMessageRequest request,
            Principal principal
    ) {
        MessageResponse response = chatService.createMessage(
                request,
                principal.getName()
        );
        messagingTemplate.convertAndSend(CHAT_TOPIC, ChatEvent.created(response));
    }

    // WebSocket 처리 오류를 현재 사용자에게만 안전한 문구로 전달
    @MessageExceptionHandler
    @SendToUser(value = "/queue/chat/errors", broadcast = false)
    public ChatErrorResponse handleMessageException(Exception exception) {
        if (exception instanceof ResponseStatusException responseStatusException
                && responseStatusException.getReason() != null) {
            return new ChatErrorResponse(responseStatusException.getReason());
        }
        if (exception instanceof BindException) {
            return new ChatErrorResponse("채팅 메시지는 1자 이상 300자 이하로 입력해주세요.");
        }
        return new ChatErrorResponse("채팅 메시지를 전송하지 못했습니다.");
    }
}
