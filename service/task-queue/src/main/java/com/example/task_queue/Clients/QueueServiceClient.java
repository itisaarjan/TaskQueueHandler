package com.example.task_queue.Clients;

import com.example.shared.Task;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "queue-service")
public interface QueueServiceClient {
    @PostMapping("/enqueue")
    void enqueueTask(@RequestBody Task task);
}
