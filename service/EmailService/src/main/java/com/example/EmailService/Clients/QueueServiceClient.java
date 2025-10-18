package com.example.EmailService.Clients;

import com.example.shared.Task;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "queue-service")
public interface QueueServiceClient {
    @GetMapping("/queue/dequeue")
    Task getFrontTask();

    @PostMapping("/queue/dequeue")
    Task popFrontTask(@RequestParam("id") String taskId);

    @PostMapping("/queue/markTaskAsCompleted")
    void markTaskAsCompleted(@RequestBody Task task);
}

