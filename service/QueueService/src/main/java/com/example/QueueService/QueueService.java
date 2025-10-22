package com.example.QueueService;

import com.example.QueueService.Config.TaskDBClient;
import com.example.shared.Task;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
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

        log.info("QueueService initialized with queues: main='{}', processing='{}', failed='{}'",
                queueName, processingQueueName, failedQueueName);
    }

    public void enqueueTask(final Task task) throws Exception {
        log.info("Enqueuing task {} (type: {}) into queue '{}'", task.getId(), task.getType(), queueName);

        try {
            final String json = mapper.writeValueAsString(task);
            redisTemplate.opsForList().leftPush(queueName, json);
            log.debug("Task {} serialized and pushed to Redis queue", task.getId());

            taskDBClient.updateTaskStatus(task.getId(), "queued", null);
            log.info("Task {} marked as 'queued' in database", task.getId());
        } catch (Exception e) {
            log.error("Failed to enqueue task {}: {}", task.getId(), e.getMessage(), e);
            throw e;
        }
    }

    public Task viewTopItem() throws Exception {
        log.debug("Viewing top item from queue '{}'", queueName);
        try {
            final String json = redisTemplate.opsForList().index(queueName, 0);
            if (json == null) {
                log.debug("Queue '{}' is empty", queueName);
                return null;
            }

            final Task task = mapper.readValue(json, Task.class);
            log.info("Peeked top task: id={}, type={}", task.getId(), task.getType());
            return task;
        } catch (Exception e) {
            log.error("Failed to view top item from queue '{}': {}", queueName, e.getMessage(), e);
            throw e;
        }
    }

    public Task dequeueTask(final String taskId) throws Exception {
        log.info("Dequeuing task {} from '{}'", taskId, queueName);

        try {
            final List<String> allTasks = redisTemplate.opsForList().range(queueName, 0, -1);
            if (allTasks == null || allTasks.isEmpty()) {
                log.debug("Queue '{}' is empty", queueName);
                return null;
            }

            String taskJson = null;
            for (String json : allTasks) {
                try {
                    final Task task = mapper.readValue(json, Task.class);
                    if (taskId.equals(task.getId())) {
                        taskJson = json;
                        log.debug("Found task {} in queue", taskId);
                        break;
                    }
                } catch (Exception ex) {
                    log.warn("Failed to parse JSON while scanning queue: {}", ex.getMessage());
                }
            }

            if (taskJson == null) {
                log.warn("Task {} not found in queue '{}'", taskId, queueName);
                return null;
            }

            redisTemplate.opsForList().remove(queueName, 1, taskJson);
            redisTemplate.opsForList().leftPush(processingQueueName, taskJson);
            log.info("Moved task {} from '{}' to '{}'", taskId, queueName, processingQueueName);

            final Task task = mapper.readValue(taskJson, Task.class);
            task.setStartedAt(Instant.now());
            taskDBClient.updateTaskStatus(task.getId(), "processing", null);
            log.info("Task {} marked as 'processing'", task.getId());

            return task;
        } catch (Exception e) {
            log.error("Failed to dequeue task {}: {}", taskId, e.getMessage(), e);
            throw e;
        }
    }

    public void markTaskAsCompleted(final Task task) throws Exception {
        log.info("Marking task {} as completed (result key: {})", task.getId(), task.getResultUrl());
        try {
            final String json = mapper.writeValueAsString(task);
            redisTemplate.opsForList().remove(processingQueueName, 1, json);
            taskDBClient.markTaskCompleted(task.getId(), task.getResultUrl());
            log.info("Task {} removed from processing queue and marked completed", task.getId());
        } catch (Exception e) {
            log.error("Failed to mark task {} as completed: {}", task.getId(), e.getMessage(), e);
            throw e;
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void recoverStuckTasks() throws Exception {
        final long VISIBILITY_MS = 5 * 60_000;
        final List<String> items = redisTemplate.opsForList().range(processingQueueName, 0, -1);
        if (items == null || items.isEmpty()) {
            log.debug("No items found in processing queue for recovery");
            return;
        }

        log.info("Recovering stuck tasks from '{}'", processingQueueName);
        for (final String json : items) {
            final Task task = mapper.readValue(json, Task.class);
            if (task.getStartedAt() == null) continue;

            final long ageMs = Instant.now().toEpochMilli() - task.getStartedAt().toEpochMilli();
            if (ageMs < VISIBILITY_MS) continue;

            if (task.getAttempts() >= MAX_ATTEMPTS) {
                redisTemplate.opsForList().leftPush(failedQueueName, json);
                redisTemplate.opsForList().remove(processingQueueName, 1, json);
                taskDBClient.markTaskFailed(task.getId(), "max retries reached");
                log.warn("Task {} moved to failed queue after {} attempts", task.getId(), task.getAttempts());
                continue;
            }

            task.setAttempts(task.getAttempts() + 1);
            task.setStartedAt(null);
            final String updated = mapper.writeValueAsString(task);
            redisTemplate.opsForList().remove(processingQueueName, 1, json);
            redisTemplate.opsForList().leftPush(queueName, updated);
            taskDBClient.updateTaskStatus(task.getId(), "queued", null);
            log.info("Recovered stuck task {} (retry #{})", task.getId(), task.getAttempts());
        }
    }
}
