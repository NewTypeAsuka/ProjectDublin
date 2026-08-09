package me.newtypeasuka.projectdublin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import me.newtypeasuka.projectdublin.domain.ChatMessage;

import java.time.LocalDateTime;
import java.util.List;

public final class ChatApiDto {

    private ChatApiDto() {
    }

    public record SendMessageRequest(
            @NotBlank
            @Pattern(
                    regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"
            )
            String clientMessageId,
            @NotBlank
            @Size(max = ChatMessage.MAX_CONTENT_LENGTH)
            String content
    ) {
    }

    public record MessageResponse(
            Long id,
            String clientMessageId,
            Long senderId,
            String senderNickname,
            boolean senderAdmin,
            String content,
            LocalDateTime createdAt
    ) {
    }

    public record MessageHistoryResponse(
            List<MessageResponse> messages,
            Long currentUserId,
            boolean currentUserAdmin,
            Long nextBeforeId,
            boolean hasMore
    ) {
    }

    public record ChatEvent(
            String type,
            MessageResponse message,
            Long messageId
    ) {

        public static ChatEvent created(MessageResponse message) {
            return new ChatEvent("MESSAGE_CREATED", message, null);
        }

        public static ChatEvent deleted(long messageId) {
            return new ChatEvent("MESSAGE_DELETED", null, messageId);
        }
    }

    public record ChatErrorResponse(String message) {
    }
}
