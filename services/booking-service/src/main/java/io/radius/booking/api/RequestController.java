package io.radius.booking.api;

import io.radius.booking.service.ChatService;
import io.radius.booking.service.RequestService;
import io.radius.common.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@Tag(name = "Requests", description = "SENT → ACCEPTED → IN_PROGRESS → COMPLETED, and the exits")
public class RequestController {

    private final RequestService requests;
    private final ChatService chat;

    public RequestController(RequestService requests, ChatService chat) {
        this.requests = requests;
        this.chat = chat;
    }

    @PostMapping("/api/requests/quote")
    @Operation(summary = "Price a booking before sending it — the client never does the arithmetic")
    public Dtos.Breakdown quote(@Valid @RequestBody Dtos.QuoteRequest req) {
        return requests.quote(req);
    }

    @PostMapping("/api/requests")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ask for a listing on a day")
    public Dtos.RequestResponse create(AuthUser me, @Valid @RequestBody Dtos.CreateRequest req) {
        return requests.create(me.id(), req);
    }

    @GetMapping("/api/requests")
    @Operation(summary = "Incoming (direction=in) or outgoing (direction=out) requests")
    public List<Dtos.RequestResponse> list(AuthUser me,
                                           @RequestParam(defaultValue = "out") String direction) {
        return requests.list(me.id(), direction);
    }

    @GetMapping("/api/requests/{id}")
    public Dtos.RequestResponse get(AuthUser me, @PathVariable UUID id) {
        return requests.get(id, me.id());
    }

    @PostMapping("/api/requests/{id}/accept")
    @Operation(summary = "Owner accepts — this blocks the days on the listing calendar")
    public Dtos.RequestResponse accept(AuthUser me, @PathVariable UUID id) {
        return requests.accept(id, me.id());
    }

    @PostMapping("/api/requests/{id}/decline")
    public Dtos.RequestResponse decline(AuthUser me, @PathVariable UUID id,
                                        @Valid @RequestBody(required = false) Dtos.DeclineRequest body) {
        return requests.decline(id, me.id(), body == null ? null : body.reason());
    }

    @PostMapping("/api/requests/{id}/start")
    @Operation(summary = "The handover happened")
    public Dtos.RequestResponse start(AuthUser me, @PathVariable UUID id) {
        return requests.start(id, me.id());
    }

    @PostMapping("/api/requests/{id}/complete")
    public Dtos.RequestResponse complete(AuthUser me, @PathVariable UUID id) {
        return requests.complete(id, me.id());
    }

    @PostMapping("/api/requests/{id}/cancel")
    public Dtos.RequestResponse cancel(AuthUser me, @PathVariable UUID id) {
        return requests.cancel(id, me.id());
    }

    // ---- the thread --------------------------------------------------------

    @GetMapping("/api/threads/{requestId}")
    @Operation(summary = "Message history — the socket carries new ones")
    public List<Dtos.MessageDto> history(AuthUser me, @PathVariable UUID requestId) {
        return chat.history(requestId, me.id());
    }

    @PostMapping("/api/threads/{requestId}")
    @Operation(summary = "Post a message; it lands on /topic/threads/{requestId} for anyone listening")
    public Dtos.MessageDto post(AuthUser me, @PathVariable UUID requestId,
                                @Valid @RequestBody Dtos.PostMessageRequest body) {
        return chat.post(requestId, me.id(), body.body());
    }

    @PostMapping("/api/threads/{requestId}/read")
    public Map<String, Long> markRead(AuthUser me, @PathVariable UUID requestId) {
        return Map.of("marked", chat.markRead(requestId, me.id()));
    }
}
