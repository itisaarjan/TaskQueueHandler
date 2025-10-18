package com.example.TaskDBService;

import com.example.TaskDBService.Utils.JsonMapConverter;
import com.example.shared.Task;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "tasks")
public class TaskDB extends Task {

    @Id
    @Column(length = 100, nullable = false, unique = true)
    private String id;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant startedAt;
    private Instant completedAt;

    private int attempts;

    @Column(length = 50)
    private String status;

    @Column(length = 512)
    private String resultUrl;

    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "jsonb", nullable = true)
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> payload;

    public TaskDB() {}

    public TaskDB(Task task) {
        this.id = task.getId();
        this.type = task.getType();
        this.createdAt = task.getCreatedAt() != null ? task.getCreatedAt() : Instant.now();
        this.startedAt = task.getStartedAt();
        this.completedAt = task.getCompletedAt();
        this.attempts = task.getAttempts();
        this.status = task.getStatus();
        this.resultUrl = task.getResultUrl();
        this.payload = task.getPayload();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(final String id) {
        this.id = id;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public void setType(final String type) {
        this.type = type;
    }

    @Override
    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public void setCreatedAt(final Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public Instant getStartedAt() {
        return startedAt;
    }

    @Override
    public void setStartedAt(final Instant startedAt) {
        this.startedAt = startedAt;
    }

    @Override
    public Instant getCompletedAt() {
        return completedAt;
    }

    @Override
    public void setCompletedAt(final Instant completedAt) {
        this.completedAt = completedAt;
    }

    @Override
    public int getAttempts() {
        return attempts;
    }

    @Override
    public void setAttempts(final int attempts) {
        this.attempts = attempts;
    }

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public void setStatus(final String status) {
        this.status = status;
    }

    @Override
    public String getResultUrl() {
        return resultUrl;
    }

    @Override
    public void setResultUrl(final String resultUrl) {
        this.resultUrl = resultUrl;
    }

    @Override
    public Map<String, Object> getPayload() {
        return payload;
    }

    @Override
    public void setPayload(final Map<String, Object> payload) {
        this.payload = payload;
    }
}
