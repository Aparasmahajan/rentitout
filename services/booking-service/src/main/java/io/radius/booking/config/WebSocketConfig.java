package io.radius.booking.config;

import io.radius.booking.repo.RequestRepository;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * STOMP over WebSocket for the chat thread. The token arrives in the CONNECT
 * frame, not in a query string, so it never lands in an access log.
 *
 * A valid token only proves who is on the socket. Which threads they may hear
 * is decided on every SUBSCRIBE: the requester and the owner of that request,
 * nobody else. Without that check any signed-in member who learned a request
 * id could read two strangers' conversation.
 *
 * The simple in-memory broker is right for a POC. Two replicas of this service
 * would not see each other's subscribers — that is the point at which you swap
 * in the Redis or RabbitMQ relay, and nothing else changes.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    static final String THREAD_PREFIX = "/topic/threads/";

    private final JwtService jwt;
    private final RequestRepository requests;

    public WebSocketConfig(JwtService jwt, RequestRepository requests) {
        this.jwt = jwt;
        this.requests = requests;
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
                if (accessor != null) inspect(accessor);
                return message;
            }
        });
    }

    /**
     * An allow-list, not a deny-list. The socket is listen-only: chat is posted
     * through {@code POST /api/threads/{id}} so it is stored and attributed by
     * {@code ChatService}. A client SEND to {@code /topic/...} would otherwise go
     * straight to the simple broker and reach both parties as a message from
     * anyone, even from a socket that never sent CONNECT (seen 15 October 2026).
     */
    void inspect(StompHeaderAccessor accessor) {
        StompCommand command = accessor.getCommand();
        if (command == null) return; // heart-beat

        switch (command) {
            // STOMP is the 1.2 spelling of CONNECT; both must carry the token.
            case CONNECT, STOMP -> {
                String header = first(accessor.getNativeHeader("Authorization"));
                AuthUser user = header != null && header.startsWith("Bearer ")
                        ? jwt.verifyAccess(header.substring(7))
                        : null;
                if (user == null) {
                    throw new IllegalArgumentException("a socket needs a valid token");
                }
                // Set on CONNECT, Spring keeps it on the session for every later frame.
                accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, List.of()));
            }
            case SUBSCRIBE -> authorizeSubscribe(accessor.getUser(), accessor.getDestination());
            case UNSUBSCRIBE, DISCONNECT -> { }
            default -> throw new AccessDeniedException("this socket only listens; post through the API");
        }
    }

    private void authorizeSubscribe(Principal principal, String destination) {
        AuthUser user = userOf(principal);
        if (user == null) {
            throw new AccessDeniedException("subscribe needs a signed-in socket");
        }
        UUID requestId = threadOf(destination);
        if (requestId == null) {
            // The only topics this service publishes are chat threads.
            throw new AccessDeniedException("no such topic");
        }
        if (!requests.isParty(requestId, user.id())) {
            throw new AccessDeniedException("not your conversation");
        }
    }

    /** The request id in {@code /topic/threads/{id}}, or null for anything else. */
    static UUID threadOf(String destination) {
        if (destination == null || !destination.startsWith(THREAD_PREFIX)) return null;
        try {
            return UUID.fromString(destination.substring(THREAD_PREFIX.length()));
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }

    private static AuthUser userOf(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof AuthUser user) {
            return user;
        }
        return null;
    }

    private static String first(List<String> values) {
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}
