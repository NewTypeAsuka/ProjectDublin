package me.newtypeasuka.projectdublin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
@EnableWebSocketSecurity
public class ChatWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final long HEARTBEAT_INTERVAL_MILLIS = 20_000L;

    // 채팅 발행 경로와 구독 경로를 분리해 클라이언트의 임의 브로커 발행을 방지
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{
                        HEARTBEAT_INTERVAL_MILLIS,
                        HEARTBEAT_INTERVAL_MILLIS
                })
                .setTaskScheduler(chatHeartbeatTaskScheduler());
    }

    // 로그인 세션을 이어받는 동일 출처 STOMP WebSocket 연결점
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat");
    }

    // 최대 300자 메시지보다 충분한 범위 안에서 비정상적으로 큰 프레임을 제한
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(4 * 1024);
        registry.setSendBufferSizeLimit(64 * 1024);
        registry.setSendTimeLimit(10_000);
    }

    // 유휴 연결이 끊기지 않도록 STOMP heartbeat를 전송
    @Bean
    public TaskScheduler chatHeartbeatTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("chat-heartbeat-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    // WebSocket heartbeat와 분리된 스레드에서 채팅 정리 작업 실행
    @Bean(name = "taskScheduler")
    public TaskScheduler chatCleanupTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("chat-cleanup-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    // 연결과 허용된 발행·구독만 인증 사용자에게 열고 나머지 메시지 경로는 거절
    @Bean
    public AuthorizationManager<Message<?>> messageAuthorizationManager(
            MessageMatcherDelegatingAuthorizationManager.Builder messages
    ) {
        messages
                .nullDestMatcher().authenticated()
                .simpSubscribeDestMatchers(
                        "/topic/chat",
                        "/user/queue/chat/errors"
                ).authenticated()
                .simpTypeMatchers(SimpMessageType.SUBSCRIBE).denyAll()
                .simpDestMatchers("/app/chat/messages").authenticated()
                .simpTypeMatchers(SimpMessageType.MESSAGE).denyAll()
                .anyMessage().denyAll();
        return messages.build();
    }
}
