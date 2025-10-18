package com.example.TaskDBService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/tasks")
public class TaskDBController {

    private final TaskDBService taskDBService;

    public TaskDBController(TaskDBService taskDBService) {
        this.taskDBService = taskDBService;
    }

    @PostMapping
    public ResponseEntity<TaskDB> createTask(@RequestBody final TaskDB taskDB) {
        TaskDB savedTask = taskDBService.saveTask(taskDB);
        return ResponseEntity.ok(savedTask);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskDB> getTaskById(@PathVariable final String id) {
        Optional<TaskDB> task = taskDBService.findById(id);
        return task.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<TaskDB>> getAllTasks() {
        List<TaskDB> tasks = taskDBService.findAll();
        return ResponseEntity.ok(tasks);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Void> updateTaskStatus(
            @PathVariable final String id,
            @RequestParam final String status,
            @RequestParam(required = false) final String resultUrl
    ) {
        taskDBService.updateStatus(id, status, resultUrl);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/processing")
    public ResponseEntity<Void> markTaskProcessing(@PathVariable final String id) {
        taskDBService.updateStatus(id, "processing", null);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<Void> markTaskCompleted(
            @PathVariable final String id,
            @RequestParam(required = false) final String resultUrl
    ) {
        taskDBService.markTaskCompleted(id, resultUrl);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/failed")
    public ResponseEntity<Void> markTaskFailed(
            @PathVariable final String id,
            @RequestParam(required = false) final String reason
    ) {
        String message = (reason != null) ? "failed: " + reason : "failed";
        taskDBService.updateStatus(id, message, null);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable final String id) {
        taskDBService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }
}
