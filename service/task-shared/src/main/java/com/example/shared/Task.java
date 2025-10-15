package com.example.shared;

import java.time.Instant;
import java.util.Map;

public class Task {
    private String id;
    private String type;
    private Instant createdAt;
    private Map<String, Object> payload;

    public Task() {}

    public Task(String type, Map<String, Object> payload) {
        this.type = type;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
}
