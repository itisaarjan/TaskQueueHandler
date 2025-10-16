package com.example.QueueService;

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
    private final String queueName;
    private final String processingQueueName;
    private final String failedQueueName;

    public QueueService(
            StringRedisTemplate redisTemplate,
            @Value("${redis.queue.name:task-queue}") String queueName,
            @Value("${redis.processing.queue.name:redis-processing-queue}") String processingQueueName,
            @Value("${redis.processing.queue.name:failed-queue}") String failedQueueName,
            ObjectMapper mapper
    ){
        this.redisTemplate = redisTemplate;
        this.queueName = queueName;
        this.mapper = mapper;
        this.processingQueueName = processingQueueName;
        this.failedQueueName = failedQueueName;
    }

    public void enqueueTask(final Task task) throws Exception {
        final String json = mapper.writeValueAsString(task);
        final ListOperations<String, String> listOperations = redisTemplate.opsForList();
        listOperations.leftPush(queueName, json);
        System.out.println("Enqueued task -> " + json);
    }

    public Task viewTopItem() throws Exception{
        final String json = redisTemplate.opsForList().index(queueName, -1);
        final Task task = mapper.readValue(json, Task.class);
        return task;
    }

    public Task dequeueTask(String taskId) throws Exception {
        final String json = redisTemplate.opsForList().rightPopAndLeftPush(queueName,processingQueueName);
        if(json == null){
            return null;
        }
        return mapper.readValue(json, Task.class);
    }

    public void markTaskAsCompleted(final Task task) throws Exception {
        final String json = mapper.writeValueAsString(task);
        redisTemplate.opsForList().remove(queueName,1,json);
        System.out.println("Marked task as completed -> " + json);
    }

    @Scheduled(fixedDelay = 60000)
    public void recoverStuckTasks() throws Exception {
        System.out.println("Recovering stuck tasks");
        final long VISIBILITY_MS = 5 * 60_000;
        final List<String> items = redisTemplate.opsForList().range(processingQueueName, 0, -1);

        for (String json : items) {
            final Task task = mapper.readValue(json, Task.class);
            if (task.getStartedAt() == null) continue;

            long ageMs = Instant.now().toEpochMilli() - task.getStartedAt().toEpochMilli();
            if (ageMs < VISIBILITY_MS) continue;

            if (task.getAttempts() >= MAX_ATTEMPTS) {
                redisTemplate.opsForList().leftPush(failedQueueName, json);
                redisTemplate.opsForList().remove(processingQueueName, 1, json);
                continue;
            }

            task.setAttempts(task.getAttempts() + 1);
            task.setStartedAt(null);
            final String updated = mapper.writeValueAsString(task);

            redisTemplate.opsForList().remove(processingQueueName, 1, json);
            redisTemplate.opsForList().leftPush(queueName, updated);
        }
    }
}