package com.example.QueueService.Config;

import com.example.shared.Task;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "task-db-service")
public interface TaskDBClient {

    @PostMapping("/tasks")
    Task createTask(@RequestBody Task task);

    @GetMapping("/tasks/{id}")
    Task getTaskById(@PathVariable("id") String id);

    @GetMapping("/tasks")
    List<Task> getAllTasks();

    @PutMapping("/tasks/{id}/status")
    void updateTaskStatus(
            @PathVariable("id") String id,
            @RequestParam("status") String status,
            @RequestParam(value = "resultUrl", required = false) String resultUrl
    );

    @PutMapping("/tasks/{id}/processing")
    void markTaskProcessing(@PathVariable("id") String id);

    @PutMapping("/tasks/{id}/complete")
    void markTaskCompleted(
            @PathVariable("id") String id,
            @RequestParam(value = "resultUrl", required = false) String resultUrl
    );

    @PutMapping("/tasks/{id}/failed")
    void markTaskFailed(
            @PathVariable("id") String id,
            @RequestParam(value = "reason", required = false) String reason
    );

    @DeleteMapping("/tasks/{id}")
    void deleteTask(@PathVariable("id") String id);
}
