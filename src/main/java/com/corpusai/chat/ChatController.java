package com.corpusai.chat;

import com.corpusai.chat.dto.ChatChunkResponse;
import com.corpusai.chat.dto.ChatRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/{subjectId}/message")
    public SseEmitter message(@PathVariable String subjectId,
                              @Valid @RequestBody ChatRequest request) {
        var emitter = new SseEmitter(300_000L);

        chatService.process(subjectId, request.sessionId(), request.message())
                .onPartialResponse(token -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("token")
                                .data(new ChatChunkResponse(token)));
                    } catch (IOException ex) {
                        emitter.completeWithError(ex);
                    }
                })
                .onCompleteResponse(response -> emitter.complete())
                .onError(emitter::completeWithError)
                .start();

        return emitter;
    }

}
