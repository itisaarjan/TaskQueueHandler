package com.example.QueueService;

import com.example.QueueService.Config.TaskDBClient;
import com.example.shared.Task;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class QueueService {
    private static final int MAX_ATTEMPTS = 5;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper mapper;
    private final TaskDBClient taskDBClient;
    private final String queueName;
    private final String processingQueueName;
    private final String failedQueueName;

    public QueueService(
            final StringRedisTemplate redisTemplate,
            final ObjectMapper mapper,
            final TaskDBClient taskDBClient,
            @Value("${redis.queue.name:task-queue}") final String queueName,
            @Value("${redis.processing.queue.name:redis-processing-queue}") final String processingQueueName,
            @Value("${redis.failed.queue.name:failed-queue}") final String failedQueueName
    ) {
        this.redisTemplate = redisTemplate;
        this.mapper = mapper;
        this.taskDBClient = taskDBClient;
        this.queueName = queueName;
        this.processingQueueName = processingQueueName;
        this.failedQueueName = failedQueueName;
    }

    public void enqueueTask(final Task task) throws Exception {
        final String json = mapper.writeValueAsString(task);
        final ListOperations<String, String> ops = redisTemplate.opsForList();
        ops.leftPush(queueName, json);
        taskDBClient.updateTaskStatus(task.getId(), "queued", null);
    }

    public Task viewTopItem() throws Exception {
        final String json = redisTemplate.opsForList().index(queueName, -1);
        if (json == null) return null;
        return mapper.readValue(json, Task.class);
    }

    public Task dequeueTask(final String taskId) throws Exception {
        final String json = redisTemplate.opsForList().rightPopAndLeftPush(queueName, processingQueueName);
        if (json == null) return null;
        final Task task = mapper.readValue(json, Task.class);
        task.setStartedAt(Instant.now());
        taskDBClient.updateTaskStatus(task.getId(), "processing", null);
        return task;
    }

    public void markTaskAsCompleted(final Task task) throws Exception {
        final String json = mapper.writeValueAsString(task);
        redisTemplate.opsForList().remove(processingQueueName, 1, json);
        taskDBClient.markTaskCompleted(task.getId(), task.getResultUrl());
    }

    @Scheduled(fixedDelay = 60000)
    public void recoverStuckTasks() throws Exception {
        final long VISIBILITY_MS = 5 * 60_000;
        final List<String> items = redisTemplate.opsForList().range(processingQueueName, 0, -1);
        if (items == null) return;
        for (final String json : items) {
            final Task task = mapper.readValue(json, Task.class);
            if (task.getStartedAt() == null) continue;
            final long ageMs = Instant.now().toEpochMilli() - task.getStartedAt().toEpochMilli();
            if (ageMs < VISIBILITY_MS) continue;
            if (task.getAttempts() >= MAX_ATTEMPTS) {
                redisTemplate.opsForList().leftPush(failedQueueName, json);
                redisTemplate.opsForList().remove(processingQueueName, 1, json);
                taskDBClient.markTaskFailed(task.getId(), "max retries reached");
                continue;
            }
            task.setAttempts(task.getAttempts() + 1);
            task.setStartedAt(null);
            final String updated = mapper.writeValueAsString(task);
            redisTemplate.opsForList().remove(processingQueueName, 1, json);
            redisTemplate.opsForList().leftPush(queueName, updated);
            taskDBClient.updateTaskStatus(task.getId(), "queued", null);
        }
    }
}
