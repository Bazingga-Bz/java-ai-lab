package com.suyou.ailab.service;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.stereotype.Service;

@Service
public class ChatMemoryService {

    private final ChatMemory chatMemory = new InMemoryChatMemory();

    public ChatMemory getMemory() {
        return chatMemory;
    }
}
