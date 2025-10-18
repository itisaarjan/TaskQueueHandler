package com.example.TaskDBService;

import com.example.shared.Task;
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
    public ResponseEntity<Task> createTask(@RequestBody final Task task) {
        System.out.println("=== TASK DB CONTROLLER: Create task ===");
        System.out.println("TaskDBController: Creating task with ID: " + task.getId());
        System.out.println("TaskDBController: Task type: " + task.getType());
        System.out.println("TaskDBController: Task status: " + task.getStatus());
        
        try {
            System.out.println("TaskDBController: Converting Task to TaskDB...");
            TaskDB taskDB = new TaskDB(task);
            System.out.println("TaskDBController: Calling taskDBService.saveTask()...");
            TaskDB saved = taskDBService.saveTask(taskDB);
            System.out.println("TaskDBController: Task saved successfully with ID: " + saved.getId());
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            System.err.println("TaskDBController ERROR: Failed to create task: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Task> getTaskById(@PathVariable final String id) {
        System.out.println("=== TASK DB CONTROLLER: Get task by ID ===");
        System.out.println("TaskDBController: Getting task with ID: " + id);
        
        try {
            System.out.println("TaskDBController: Calling taskDBService.findById()...");
            Optional<TaskDB> task = taskDBService.findById(id);
            if (task.isPresent()) {
                System.out.println("TaskDBController: Task found: " + task.get().getId());
                return ResponseEntity.ok(task.get());
            } else {
                System.out.println("TaskDBController: Task not found");
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            System.err.println("TaskDBController ERROR: Failed to get task: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @GetMapping
    public ResponseEntity<List<TaskDB>> getAllTasks() {
        System.out.println("=== TASK DB CONTROLLER: Get all tasks ===");
        
        try {
            System.out.println("TaskDBController: Calling taskDBService.findAll()...");
            List<TaskDB> tasks = taskDBService.findAll();
            System.out.println("TaskDBController: Found " + tasks.size() + " tasks");
            return ResponseEntity.ok(tasks);
        } catch (Exception e) {
            System.err.println("TaskDBController ERROR: Failed to get all tasks: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Void> updateTaskStatus(
            @PathVariable final String id,
            @RequestParam final String status,
            @RequestParam(required = false) final String resultUrl
    ) {
        System.out.println("=== TASK DB CONTROLLER: Update task status ===");
        System.out.println("TaskDBController: Updating task ID: " + id + " to status: " + status);
        System.out.println("TaskDBController: Result URL: " + resultUrl);
        
        try {
            System.out.println("TaskDBController: Calling taskDBService.updateStatus()...");
            taskDBService.updateStatus(id, status, resultUrl);
            System.out.println("TaskDBController: Task status updated successfully");
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            System.err.println("TaskDBController ERROR: Failed to update task status: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @PutMapping("/{id}/processing")
    public ResponseEntity<Void> markTaskProcessing(@PathVariable final String id) {
        System.out.println("=== TASK DB CONTROLLER: Mark task as processing ===");
        System.out.println("TaskDBController: Marking task ID: " + id + " as processing");
        
        try {
            System.out.println("TaskDBController: Calling taskDBService.updateStatus()...");
            taskDBService.updateStatus(id, "processing", null);
            System.out.println("TaskDBController: Task marked as processing successfully");
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            System.err.println("TaskDBController ERROR: Failed to mark task as processing: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<Void> markTaskCompleted(
            @PathVariable final String id,
            @RequestParam(required = false) final String resultUrl
    ) {
        System.out.println("=== TASK DB CONTROLLER: Mark task as completed ===");
        System.out.println("TaskDBController: Marking task ID: " + id + " as completed");
        System.out.println("TaskDBController: Result URL: " + resultUrl);
        
        try {
            System.out.println("TaskDBController: Calling taskDBService.markTaskCompleted()...");
            taskDBService.markTaskCompleted(id, resultUrl);
            System.out.println("TaskDBController: Task marked as completed successfully");
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            System.err.println("TaskDBController ERROR: Failed to mark task as completed: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @PutMapping("/{id}/failed")
    public ResponseEntity<Void> markTaskFailed(
            @PathVariable final String id,
            @RequestParam(required = false) final String reason
    ) {
        System.out.println("=== TASK DB CONTROLLER: Mark task as failed ===");
        System.out.println("TaskDBController: Marking task ID: " + id + " as failed");
        System.out.println("TaskDBController: Reason: " + reason);
        
        try {
            String message = (reason != null) ? "failed: " + reason : "failed";
            System.out.println("TaskDBController: Calling taskDBService.updateStatus()...");
            taskDBService.updateStatus(id, message, null);
            System.out.println("TaskDBController: Task marked as failed successfully");
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            System.err.println("TaskDBController ERROR: Failed to mark task as failed: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable final String id) {
        taskDBService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }
}
