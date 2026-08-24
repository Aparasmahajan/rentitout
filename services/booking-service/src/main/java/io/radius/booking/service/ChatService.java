package io.radius.booking.service;

import io.radius.booking.api.Dtos;
import io.radius.booking.domain.BookingRequest;
import io.radius.booking.domain.Message;
import io.radius.booking.repo.MessageRepository;
import io.radius.common.events.RadiusEvents;
import io.radius.common.events.Topics;
import io.radius.common.outbox.OutboxService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ChatService {

    private final MessageRepository messages;
    private final RequestService requests;
    private final SimpMessagingTemplate ws;
    private final OutboxService outbox;

    public ChatService(MessageRepository messages, RequestService requests, SimpMessagingTemplate ws,
                       OutboxService outbox) {
        this.messages = messages;
        this.requests = requests;
        this.ws = ws;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public List<Dtos.MessageDto> history(UUID requestId, UUID callerId) {
        requests.load(requestId, callerId);      // throws 404 unless the caller is a party to it
        return messages.findByRequestIdOrderBySentAtAsc(requestId).stream()
                .map(ChatService::toDto).toList();
    }

    @Transactional
    public Dtos.MessageDto post(UUID requestId, UUID senderId, String body) {
        BookingRequest request = requests.load(requestId, senderId);
        Message saved = messages.save(new Message(requestId, senderId, body.trim()));
        Dtos.MessageDto dto = toDto(saved);

        // Push to the socket only once the row is committed — a subscriber that
        // sees a message the database does not have is a bug that is very hard
        // to reproduce later.
        afterCommit(() -> ws.convertAndSend("/topic/threads/" + requestId, dto));

        UUID recipient = request.counterpartOf(senderId);
        outbox.publish(Topics.REQUEST, requestId, new RadiusEvents.MessagePosted(
                saved.getId(), requestId, senderId, recipient, preview(body), Instant.now()));
        outbox.publish(Topics.NOTIFICATION, recipient, new RadiusEvents.NotificationRequested(
                recipient, "push", "message", "New message", preview(body),
                "/requests/" + requestId, Instant.now()));

        return dto;
    }

    @Transactional
    public long markRead(UUID requestId, UUID readerId) {
        requests.load(requestId, readerId);
        return messages.markThreadRead(requestId, readerId);
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { action.run(); }
            });
        } else {
            action.run();
        }
    }

    private static String preview(String body) {
        String trimmed = body.trim();
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 77) + "...";
    }

    private static Dtos.MessageDto toDto(Message m) {
        return new Dtos.MessageDto(m.getId(), m.getRequestId(), m.getSenderId(), m.getBody(),
                m.getSentAt(), m.getReadAt());
    }
}
