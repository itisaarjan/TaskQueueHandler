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
        System.out.println("=== QUEUE SERVICE: Enqueue task ===");
        System.out.println("QueueService: Enqueuing task ID: " + task.getId());
        System.out.println("QueueService: Task type: " + task.getType());
        System.out.println("QueueService: Queue name: " + queueName);
        
        try {
            System.out.println("QueueService: Converting task to JSON...");
            final String json = mapper.writeValueAsString(task);
            System.out.println("QueueService: Task JSON: " + json);
            
            System.out.println("QueueService: Getting Redis list operations...");
            final ListOperations<String, String> ops = redisTemplate.opsForList();
            
            System.out.println("QueueService: Pushing task to Redis queue...");
            ops.leftPush(queueName, json);
            System.out.println("QueueService: Task pushed to Redis successfully");
            
            System.out.println("QueueService: Updating task status in database...");
            taskDBClient.updateTaskStatus(task.getId(), "queued", null);
            System.out.println("QueueService: Task status updated to 'queued'");
            
            System.out.println("QueueService: Enqueue completed successfully");
        } catch (Exception e) {
            System.err.println("QueueService ERROR: Failed to enqueue task: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public Task viewTopItem() throws Exception {
        System.out.println("=== QUEUE SERVICE: View top item ===");
        System.out.println("QueueService: Getting top item from queue: " + queueName);
        
        try {
            final String json = redisTemplate.opsForList().index(queueName, 0);
            if (json == null) {
                System.out.println("QueueService: Queue is empty");
                return null;
            }
            System.out.println("QueueService: Found task JSON: " + json);
            
            final Task task = mapper.readValue(json, Task.class);
            System.out.println("QueueService: Parsed task ID: " + task.getId());
            return task;
        } catch (Exception e) {
            System.err.println("QueueService ERROR: Failed to view top item: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public Task dequeueTask(final String taskId) throws Exception {
        System.out.println("=== QUEUE SERVICE: Dequeue task ===");
        System.out.println("QueueService: Dequeuing task ID: " + taskId);
        System.out.println("QueueService: Moving from queue: " + queueName + " to processing: " + processingQueueName);
        
        try {
            System.out.println("QueueService: Getting all tasks from queue to find matching task...");
            final List<String> allTasks = redisTemplate.opsForList().range(queueName, 0, -1);
            if (allTasks == null || allTasks.isEmpty()) {
                System.out.println("QueueService: No tasks found in queue");
                return null;
            }
            
            System.out.println("QueueService: Found " + allTasks.size() + " tasks in queue");
            
            // Find the task with matching ID
            String taskJson = null;
            for (int i = 0; i < allTasks.size(); i++) {
                final String json = allTasks.get(i);
                try {
                    final Task task = mapper.readValue(json, Task.class);
                    if (taskId.equals(task.getId())) {
                        taskJson = json;
                        System.out.println("QueueService: Found matching task at index " + i);
                        break;
                    }
                } catch (Exception e) {
                    System.err.println("QueueService WARNING: Failed to parse task JSON: " + json);
                }
            }
            
            if (taskJson == null) {
                System.out.println("QueueService: Task with ID " + taskId + " not found in queue");
                return null;
            }
            
            System.out.println("QueueService: Removing task from queue...");
            redisTemplate.opsForList().remove(queueName, 1, taskJson);
            
            System.out.println("QueueService: Adding task to processing queue...");
            redisTemplate.opsForList().leftPush(processingQueueName, taskJson);
            
            final Task task = mapper.readValue(taskJson, Task.class);
            System.out.println("QueueService: Parsed task ID: " + task.getId());
            
            System.out.println("QueueService: Setting task start time...");
            task.setStartedAt(Instant.now());
            
            System.out.println("QueueService: Updating task status to 'processing'...");
            taskDBClient.updateTaskStatus(task.getId(), "processing", null);
            System.out.println("QueueService: Task status updated to 'processing'");
            
            System.out.println("QueueService: Dequeue completed successfully");
            return task;
        } catch (Exception e) {
            System.err.println("QueueService ERROR: Failed to dequeue task: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void markTaskAsCompleted(final Task task) throws Exception {
        System.out.println("=== QUEUE SERVICE: Mark task as completed ===");
        System.out.println("QueueService: Marking task as completed ID: " + task.getId());
        System.out.println("QueueService: Result URL: " + task.getResultUrl());
        
        try {
            System.out.println("QueueService: Converting task to JSON...");
            final String json = mapper.writeValueAsString(task);
            
            System.out.println("QueueService: Removing task from processing queue...");
            redisTemplate.opsForList().remove(processingQueueName, 1, json);
            System.out.println("QueueService: Task removed from processing queue");
            
            System.out.println("QueueService: Marking task as completed in database...");
            taskDBClient.markTaskCompleted(task.getId(), task.getResultUrl());
            System.out.println("QueueService: Task marked as completed in database");
            
            System.out.println("QueueService: Mark completed successfully");
        } catch (Exception e) {
            System.err.println("QueueService ERROR: Failed to mark task as completed: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
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
