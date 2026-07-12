package com.corpusai.chat;

import com.corpusai.auth.AuthenticatedUser;
import com.corpusai.chat.dto.ChatChunkResponse;
import com.corpusai.chat.dto.ChatDoneResponse;
import com.corpusai.chat.dto.ChatMessageResponse;
import com.corpusai.chat.dto.ChatSessionResponse;
import com.corpusai.chat.dto.CreateChatSessionRequest;
import com.corpusai.chat.dto.SendMessageRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chats")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatSessionResponse createSession(@Valid @RequestBody CreateChatSessionRequest request,
                                             @AuthenticationPrincipal AuthenticatedUser principal) {
        ChatSession session = chatService.createSession(principal, request.subjectId(), request.lang(), request.provider());
        return toResponse(session);
    }

    @GetMapping
    public List<ChatSessionResponse> listSessions(@RequestParam String subjectId,
                                                  @AuthenticationPrincipal AuthenticatedUser principal) {
        return chatService.listSessions(principal, subjectId).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{sessionId}/messages")
    public List<ChatMessageResponse> getMessages(@PathVariable UUID sessionId,
                                                 @AuthenticationPrincipal AuthenticatedUser principal) {
        return chatService.getTranscript(principal, sessionId).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSession(@PathVariable UUID sessionId, @AuthenticationPrincipal AuthenticatedUser principal) {
        chatService.deleteSession(principal, sessionId);
    }

    @PostMapping("/{sessionId}/messages")
    public SseEmitter sendMessage(@PathVariable UUID sessionId,
                                  @Valid @RequestBody SendMessageRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser principal) {
        var emitter = new SseEmitter(300_000L);

        var tokenStream = chatService.process(principal, sessionId, request.message());
        Instant startedAt = Instant.now();

        tokenStream.onPartialResponse(token -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("token")
                                .data(new ChatChunkResponse(token)));
                    } catch (IOException ex) {
                        emitter.completeWithError(ex);
                    }
                })
                .onCompleteResponse(response -> {
                    try {
                        long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
                        UUID messageId = chatService.latestMessageId(sessionId);
                        var usage = response.tokenUsage();
                        emitter.send(SseEmitter.event()
                                .name("done")
                                .data(new ChatDoneResponse(messageId,
                                        usage != null ? usage.inputTokenCount() : null,
                                        usage != null ? usage.outputTokenCount() : null,
                                        latencyMs)));
                        emitter.complete();
                    } catch (IOException ex) {
                        emitter.completeWithError(ex);
                    }
                })
                .onError(emitter::completeWithError)
                .start();

        return emitter;
    }

    private ChatSessionResponse toResponse(ChatSession session) {
        return new ChatSessionResponse(session.getId(), session.getTitle(), session.getSubjectId(),
                session.getLang(), session.getProvider(), session.getCreatedAt());
    }

    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        return new ChatMessageResponse(message.getId(), message.getRole(), message.getContent(), message.getCreatedAt());
    }

}
