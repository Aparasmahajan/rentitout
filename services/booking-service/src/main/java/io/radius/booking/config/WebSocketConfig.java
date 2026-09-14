package io.radius.booking.config;

import io.radius.common.security.AuthUser;
import io.radius.common.security.JwtService;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

/**
 * STOMP over WebSocket for the chat thread. The token arrives in the CONNECT
 * frame, not in a query string, so it never lands in an access log.
 *
 * The simple in-memory broker is right for a POC. Two replicas of this service
 * would not see each other's subscribers — that is the point at which you swap
 * in the Redis or RabbitMQ relay, and nothing else changes.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwt;

    public WebSocketConfig(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Native WebSocket only: both clients use @stomp/stompjs, and SockJS
        // fallbacks would need sticky sessions at the gateway.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*", "exp://*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String header = first(accessor.getNativeHeader("Authorization"));
                    AuthUser user = header != null && header.startsWith("Bearer ")
                            ? jwt.verifyAccess(header.substring(7))
                            : null;
                    if (user == null) {
                        throw new IllegalArgumentException("a socket needs a valid token");
                    }
                    accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, List.of()));
                }
                return message;
            }
        });
    }

    private static String first(List<String> values) {
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}
