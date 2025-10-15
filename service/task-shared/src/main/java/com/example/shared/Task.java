package com.example.shared;

import java.time.Instant;
import java.util.Map;

public class Task {
    private String id;
    private String type;
    private Instant createdAt;
    private Map<String, Object> payload;
    private int attempts;
    private Instant startedAt;

    public Task() {}

    public Task(String type, Map<String, Object> payload) {
        this.type = type;
        this.payload = payload;
        this.createdAt = Instant.now();
        this.attempts = 0;
        this.startedAt = null;
    }

    public String getId() { return id; }
    public void setId(final String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(final String type) { this.type = type; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(final Instant createdAt) { this.createdAt = createdAt; }

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(final Map<String, Object> payload) { this.payload = payload; }

    public int getAttempts() { return attempts; }
    public void setAttempts(final int attempts) { this.attempts = attempts; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(final Instant startedAt) { this.startedAt = startedAt; }
}
