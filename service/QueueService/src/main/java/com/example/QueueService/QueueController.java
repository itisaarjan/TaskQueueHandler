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
        try{
            queueService.enqueueTask(task);
        }catch (Exception e){
            System.err.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }

        return ResponseEntity.status(HttpStatus.OK).body(task.toString());
    }

    @GetMapping("/dequeue")
    public ResponseEntity<?> dequeue() {
        try {
            Task task = queueService.viewTopItem();
            if (task == null) {
                return ResponseEntity.status(HttpStatus.NO_CONTENT)
                        .body("Queue is empty");
            }
            return ResponseEntity.ok(task);
        } catch (Exception e) {
            System.err.println("Error while viewing top item: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/dequeue")
    public ResponseEntity<?> dequeue(@RequestParam("id") final String taskId){
        try{
            final Task task = queueService.dequeueTask(taskId);
            if(task == null){
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
            }
            return ResponseEntity.ok(task);
        } catch (Exception e) {
            System.err.println("Error while dequeing task: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/markTaskAsCompleted")
    public ResponseEntity<?> markTaskAsCompleted(@RequestBody final Task task){
        try{
            if(task == null){
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
            }
            queueService.markTaskAsCompleted(task);
            return ResponseEntity.status(HttpStatus.OK).body(task.toString());
        } catch (Exception e) {
            System.err.println("Error while marking task as completed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }
}
