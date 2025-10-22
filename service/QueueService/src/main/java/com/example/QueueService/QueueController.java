package com.example.QueueService;

import com.example.shared.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/queue")
public class QueueController {

    private final QueueService queueService;

    public QueueController(final QueueService queueService) {
        this.queueService = queueService;
        log.info("QueueController initialized");
    }

    @PostMapping("/enqueue")
    public ResponseEntity<String> enqueue(@RequestBody final Task task) {
        if (task == null) {
            log.warn("Received null task in enqueue request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Task cannot be null");
        }

        log.info("Received enqueue request for task ID={}, type={}, status={}",
                task.getId(), task.getType(), task.getStatus());

        try {
            queueService.enqueueTask(task);
            log.info("Successfully enqueued task ID={}", task.getId());
            return ResponseEntity.ok(task.toString());
        } catch (Exception e) {
            log.error("Failed to enqueue task ID={}: {}", task.getId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to enqueue task: " + e.getMessage());
        }
    }

    @GetMapping("/dequeue")
    public ResponseEntity<?> viewTopItem() {
        log.info("Received request to view top item in queue");
        try {
            final Task task = queueService.viewTopItem();
            if (task == null) {
                log.debug("Queue is empty");
                return ResponseEntity.status(HttpStatus.NO_CONTENT)
                        .body("Queue is empty");
            }
            log.info("Top item retrieved successfully: ID={}, type={}", task.getId(), task.getType());
            return ResponseEntity.ok(task);
        } catch (Exception e) {
            log.error("Error while viewing top item: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/dequeue")
    public ResponseEntity<?> dequeue(@RequestParam("id") final String taskId) {
        log.info("Received dequeue request for task ID={}", taskId);
        try {
            final Task task = queueService.dequeueTask(taskId);
            if (task == null) {
                log.warn("Task ID={} not found or already dequeued", taskId);
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body("Task not found");
            }
            log.info("Successfully dequeued task ID={}", task.getId());
            return ResponseEntity.ok(task);
        } catch (Exception e) {
            log.error("Error while dequeuing task ID={}: {}", taskId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/markTaskAsCompleted")
    public ResponseEntity<?> markTaskAsCompleted(@RequestBody final Task task) {
        if (task == null) {
            log.warn("Received null task in markTaskAsCompleted request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Task cannot be null");
        }

        log.info("Received mark-as-completed request for task ID={}", task.getId());

        try {
            queueService.markTaskAsCompleted(task);
            log.info("Successfully marked task ID={} as completed", task.getId());
            return ResponseEntity.ok(task.toString());
        } catch (Exception e) {
            log.error("Error while marking task ID={} as completed: {}", task.getId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }
}
