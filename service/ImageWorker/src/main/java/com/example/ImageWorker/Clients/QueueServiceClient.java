package com.example.ImageWorker.Clients;

import com.example.shared.Task;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "queue-service")
public interface QueueServiceClient {
    @PostMapping("/dequeue")
    Task getFrontTask();

    @PostMapping("/dequeue")
    Task popFrontTask(@RequestParam("id") final String taskid);
}
