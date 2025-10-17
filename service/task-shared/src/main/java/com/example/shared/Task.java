package com.example.shared;

import java.time.Instant;
import java.util.Map;

public class Task {
    private String id;
    private String type;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private int attempts;
    private String status;
    private String resultUrl;
    private Map<String, Object> payload;

    public Task() {}

    public Task(String type, Map<String, Object> payload) {
        this.type = type;
        this.payload = payload;
        this.createdAt = Instant.now();
        this.status = "queued";
        this.attempts = 0;
        this.startedAt = null;
        this.completedAt = null;
    }

    public String getId() { return id; }
    public void setId(final String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(final String type) { this.type = type; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(final Instant createdAt) { this.createdAt = createdAt; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(final Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(final Instant completedAt) { this.completedAt = completedAt; }

    public int getAttempts() { return attempts; }
    public void setAttempts(final int attempts) { this.attempts = attempts; }

    public String getStatus() { return status; }
    public void setStatus(final String status) { this.status = status; }

    public String getResultUrl() { return resultUrl; }
    public void setResultUrl(final String resultUrl) { this.resultUrl = resultUrl; }

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(final Map<String, Object> payload) { this.payload = payload; }

    @Override
    public String toString() {
        return "Task{" +
                "id='" + id + '\'' +
                ", type='" + type + '\'' +
                ", status='" + status + '\'' +
                ", attempts=" + attempts +
                ", createdAt=" + createdAt +
                ", startedAt=" + startedAt +
                ", completedAt=" + completedAt +
                ", resultUrl='" + resultUrl + '\'' +
                '}';
    }
}
