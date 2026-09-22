package com.suyou.ailab.controller;

import com.suyou.ailab.service.ChatMemoryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatClient chatClient;
    private final ChatMemoryService memoryService;

    public ChatController(ChatClient.Builder builder, ChatMemoryService memoryService) {
        this.chatClient = builder.build();
        this.memoryService = memoryService;
    }

    /**
     * 同步接口（保留，方便调试）
     */
    @GetMapping("/chat")
    public String chat(@RequestParam(defaultValue = "你好") String message) {
        try {
            return chatClient.prompt()
                    .user(message)
                    .call()
                    .content();
        } catch (Exception e) {
            return "❌ " + e.getMessage();
        }
    }

    /**
     * SSE 流式接口 + 对话记忆
     * 请求示例: POST /api/chat/stream
     * Body: {"message": "你好", "sessionId": "user-001"}
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestBody ChatRequest request) {
        ChatMemory memory = memoryService.getMemory();

        return chatClient.prompt()
                .user(request.message())
                .advisors(a -> a.param("chat_memory_conversation_id", request.sessionId()))
                .stream()
                .content();
    }

    record ChatRequest(String message, String sessionId) {}
}