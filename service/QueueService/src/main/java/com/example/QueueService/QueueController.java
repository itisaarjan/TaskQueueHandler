package com.example.QueueService;

import com.example.shared.Task;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/queue")
public class QueueController {
    private final QueueService queueService;

    public QueueController(final QueueService queueService){
        this.queueService = queueService;
    }

    @PostMapping("/enqueue")
    public ResponseEntity<String> enqueue(@RequestBody final Task task){
        System.out.println("=== QUEUE CONTROLLER: Enqueue request ===");
        System.out.println("QueueController: Received enqueue request for task ID: " + task.getId());
        System.out.println("QueueController: Task type: " + task.getType());
        System.out.println("QueueController: Task status: " + task.getStatus());
        
        try{
            System.out.println("QueueController: Calling queueService.enqueueTask()...");
            queueService.enqueueTask(task);
            System.out.println("QueueController: Task enqueued successfully");
        }catch (Exception e){
            System.err.println("QueueController ERROR: Failed to enqueue task: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }

        System.out.println("QueueController: Returning success response");
        return ResponseEntity.status(HttpStatus.OK).body(task.toString());
    }

    @GetMapping("/dequeue")
    public ResponseEntity<?> dequeue() {
        System.out.println("=== QUEUE CONTROLLER: View top item request ===");
        try {
            System.out.println("QueueController: Calling queueService.viewTopItem()...");
            Task task = queueService.viewTopItem();
            if (task == null) {
                System.out.println("QueueController: Queue is empty");
                return ResponseEntity.status(HttpStatus.NO_CONTENT)
                        .body("Queue is empty");
            }
            System.out.println("QueueController: Found task at top: " + task.getId());
            return ResponseEntity.ok(task);
        } catch (Exception e) {
            System.err.println("QueueController ERROR: Error while viewing top item: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/dequeue")
    public ResponseEntity<?> dequeue(@RequestParam("id") final String taskId){
        System.out.println("=== QUEUE CONTROLLER: Dequeue request ===");
        System.out.println("QueueController: Received dequeue request for task ID: " + taskId);
        try{
            System.out.println("QueueController: Calling queueService.dequeueTask()...");
            final Task task = queueService.dequeueTask(taskId);
            if(task == null){
                System.out.println("QueueController: Task not found or already dequeued");
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
            }
            System.out.println("QueueController: Task dequeued successfully: " + task.getId());
            return ResponseEntity.ok(task);
        } catch (Exception e) {
            System.err.println("QueueController ERROR: Error while dequeing task: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/markTaskAsCompleted")
    public ResponseEntity<?> markTaskAsCompleted(@RequestBody final Task task){
        System.out.println("=== QUEUE CONTROLLER: Mark task as completed request ===");
        System.out.println("QueueController: Received mark completed request for task ID: " + (task != null ? task.getId() : "null"));
        try{
            if(task == null){
                System.out.println("QueueController: Task is null, returning NO_CONTENT");
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
            }
            System.out.println("QueueController: Calling queueService.markTaskAsCompleted()...");
            queueService.markTaskAsCompleted(task);
            System.out.println("QueueController: Task marked as completed successfully");
            return ResponseEntity.status(HttpStatus.OK).body(task.toString());
        } catch (Exception e) {
            System.err.println("QueueController ERROR: Error while marking task as completed: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }
}
