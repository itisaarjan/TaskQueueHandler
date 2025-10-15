package com.example.QueueService;

import com.example.shared.Task;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class QueueService {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper mapper;
    private final String queueName;
    public QueueService(
            StringRedisTemplate redisTemplate,
            @Value("${redis.queue.name:task-queue}") String queueName,
            ObjectMapper mapper
    ){
        this.redisTemplate = redisTemplate;
        this.queueName = queueName;
        this.mapper = mapper;
    }

    public void enqueueTask(final Task task) throws Exception {
        final String json = mapper.writeValueAsString(task);
        final ListOperations<String, String> listOperations = redisTemplate.opsForList();
        listOperations.leftPush(queueName, json);
        System.out.println("Enqueued task -> " + json);
    }

    public Task dequeueTask(String taskId) throws Exception {
        final ListOperations<String, String> listOperations = redisTemplate.opsForList();
        final String json = listOperations.rightPop(queueName);
        if(json == null){
            return null;
        }
        return mapper.readValue(json, Task.class);
    }
}