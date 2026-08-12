package me.newtypeasuka.projectdublin.controller;

import me.newtypeasuka.projectdublin.domain.User;
import me.newtypeasuka.projectdublin.dto.ChatApiDto.MessageResponse;
import me.newtypeasuka.projectdublin.dto.ChatApiDto.SendMessageRequest;
import me.newtypeasuka.projectdublin.repository.ChatRepository;
import me.newtypeasuka.projectdublin.repository.UserRepository;
import me.newtypeasuka.projectdublin.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@Transactional
@SpringBootTest
@AutoConfigureMockMvc
class ChatApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ChatApiController chatApiController;

    @Autowired
    ChatService chatService;

    @Autowired
    ChatRepository chatRepository;

    @Autowired
    UserRepository userRepository;

    User admin;
    User member;
    User other;

    @BeforeEach
    void setUp() {
        admin = saveUser("chat-api-admin@example.com", "API관리자", 1);
        member = saveUser("chat-api-member@example.com", "API회원", 2);
        other = saveUser("chat-api-other@example.com", "API다른회원", 2);
    }

    @DisplayName("로그인 사용자는 채팅 화면과 최신 메시지 이력을 조회한다")
    @Test
    void renderChatPageAndGetMessages() throws Exception {
        MessageResponse message = createMessage(member, "API 채팅 메시지");

        mockMvc.perform(get("/menu/chat").with(loginUser(member)))
                .andExpect(status().isOk())
                .andExpect(view().name("chat"))
                .andExpect(content().string(containsString(
                        "class=\"chat-toolbar__title-row\""
                )))
                .andExpect(content().string(containsString("id=\"chat-connection\"")))
                .andExpect(content().string(not(containsString(
                        "하나의 공개 채팅방에서 실시간으로 대화합니다"
                ))))
                .andExpect(content().string(containsString("id=\"chat-message-list\"")))
                .andExpect(content().string(containsString("id=\"chat-form\"")))
                .andExpect(content().string(containsString("@stomp/stompjs@7.2.0")))
                .andExpect(content().string(containsString("src=\"/js/chat.js\"")));

        mockMvc.perform(get("/api/menu/chat/messages")
                        .with(loginUser(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].id").value(message.id()))
                .andExpect(jsonPath("$.messages[0].senderNickname")
                        .value(member.getNickname()))
                .andExpect(jsonPath("$.messages[0].expiresAtEpochMillis").isNumber())
                .andExpect(jsonPath("$.currentUserId").value(member.getId()))
                .andExpect(jsonPath("$.currentUserAdmin").value(false));
    }

    @DisplayName("STOMP 메시지 처리 메서드는 세션 사용자를 작성자로 저장한다")
    @Test
    void createMessageFromAuthenticatedWebSocketPrincipal() {
        String clientMessageId = UUID.randomUUID().toString();

        chatApiController.createMessage(
                new SendMessageRequest(clientMessageId, "WebSocket 메시지"),
                member::getEmail
        );

        assertThat(chatRepository.findBySenderIdAndClientMessageId(
                member.getId(),
                clientMessageId
        )).get().satisfies(message -> {
            assertThat(message.getContent()).isEqualTo("WebSocket 메시지");
            assertThat(message.getSender().getId()).isEqualTo(member.getId());
        });
    }

    @DisplayName("메시지 작성자는 CSRF 토큰으로 자신의 메시지를 삭제한다")
    @Test
    void deleteOwnMessageWithCsrf() throws Exception {
        MessageResponse message = createMessage(member, "삭제할 메시지");

        mockMvc.perform(delete("/api/menu/chat/messages/{messageId}", message.id())
                        .with(loginUser(member))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("MESSAGE_DELETED"))
                .andExpect(jsonPath("$.messageId").value(message.id()));

        assertThat(chatRepository.existsById(message.id())).isFalse();
    }

    @DisplayName("CSRF 토큰이 없거나 다른 사용자의 삭제 요청은 거절한다")
    @Test
    void rejectInvalidDeleteRequest() throws Exception {
        MessageResponse message = createMessage(member, "보호할 메시지");

        mockMvc.perform(delete("/api/menu/chat/messages/{messageId}", message.id())
                        .with(loginUser(member)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/menu/chat/messages/{messageId}", message.id())
                        .with(loginUser(other))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(chatRepository.existsById(message.id())).isTrue();
    }

    @DisplayName("관리자는 다른 사용자의 채팅 메시지도 삭제한다")
    @Test
    void allowAdminToDeleteAnyMessage() throws Exception {
        MessageResponse message = createMessage(member, "관리자 삭제 메시지");

        mockMvc.perform(delete("/api/menu/chat/messages/{messageId}", message.id())
                        .with(loginUser(admin))
                        .with(csrf()))
                .andExpect(status().isOk());

        assertThat(chatRepository.existsById(message.id())).isFalse();
    }

    @DisplayName("로그인하지 않은 사용자는 채팅 화면과 API에 접근할 수 없다")
    @Test
    void rejectUnauthenticatedChatAccess() throws Exception {
        mockMvc.perform(get("/api/menu/chat/messages"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/menu/chat"))
                .andExpect(status().is3xxRedirection());
    }

    private MessageResponse createMessage(User sender, String content) {
        return chatService.createMessage(
                new SendMessageRequest(UUID.randomUUID().toString(), content),
                sender.getEmail()
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

    private RequestPostProcessor loginUser(User user) {
        DefaultOAuth2User oAuth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("email", user.getEmail(), "name", user.getName()),
                "email"
        );
        return oauth2Login().oauth2User(oAuth2User);
    }
}
