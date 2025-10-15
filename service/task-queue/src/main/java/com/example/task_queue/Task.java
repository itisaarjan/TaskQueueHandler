package com.example.task_queue;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class Task {
    private String id;
    private String Type;
    private Map<String, Object> payload;
    private Instant createdAt;

    public Task(String type,
                Map<String, Object> payload) {
        this.id = UUID.randomUUID().toString();
        this.Type = type;
        this.payload = payload;
        this.createdAt = Instant.now();
    }
    public String getId() {
        return id;
    }

    public String getType() {
        return Type;
    }
    public Map<String, Object> getPayload() {
        return payload;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
}