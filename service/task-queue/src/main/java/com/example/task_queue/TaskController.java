package com.example.task_queue;

import com.example.shared.Task;
import com.example.task_queue.Clients.QueueServiceClient;
import com.example.task_queue.Clients.TaskDBClient;
import com.example.task_queue.S3Service.S3Service;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;


@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final S3Service s3Service;
    private final QueueServiceClient queueServiceClient;
    private final TaskDBClient taskDBClient;

    public TaskController(
            final S3Service s3Service,
            final QueueServiceClient queueServiceClient,
            final TaskDBClient taskDBClient
    ) {
        this.s3Service = s3Service;
        this.queueServiceClient = queueServiceClient;
        this.taskDBClient = taskDBClient;
    }

    @GetMapping("/")
    public String index() {
        return "Task Controller is active";
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("type") String type,
            @RequestParam("content") MultipartFile file,
            @RequestParam(value = "grayscale", required = false) final Boolean grayscale,
            @RequestParam(value = "invert", required = false) final Boolean invert,
            @RequestParam(value = "blur", required = false) final Boolean blur,
            @RequestParam(value = "resize", required = false) final Boolean resize,
            @RequestParam(value = "watermark", required = false) final Boolean watermark
    ) throws Exception {

        System.out.println("=== TASK CONTROLLER: Starting upload process ===");
        System.out.println("TaskController: Received upload request - type: " + type + ", file: " + file.getOriginalFilename());
        
        Path tempFile;
        String s3Url;

        try {
            System.out.println("TaskController: Creating temporary file...");
            tempFile = Files.createTempFile("upload-", "-" + file.getOriginalFilename());
            file.transferTo(tempFile.toFile());
            System.out.println("TaskController: Temporary file created successfully: " + tempFile.toString());
        } catch (Exception e) {
            System.err.println("TaskController ERROR: File upload failed during temp storage: " + e.getMessage());
            e.printStackTrace();
            throw new Exception("File upload failed during temp storage.");
        }

        try {
            System.out.println("TaskController: Uploading file to S3...");
            s3Url = s3Service.uploadFile(tempFile, file.getOriginalFilename());
            System.out.println("TaskController: S3 upload successful, URL: " + s3Url);
        } catch (Exception e) {
            System.err.println("TaskController ERROR: S3 upload failed: " + e.getMessage());
            e.printStackTrace();
            throw new Exception("S3 upload failed.");
        }

        try {
            System.out.println("TaskController: Cleaning up temporary file...");
            Files.deleteIfExists(tempFile);
            System.out.println("TaskController: Temporary file cleanup completed");
        } catch (Exception e) {
            System.err.println("TaskController WARNING: Failed to delete temp file: " + e.getMessage());
        }

        System.out.println("TaskController: Building task options...");
        Map<String, Object> options = new HashMap<>();
        if (grayscale != null) options.put("grayscale", grayscale);
        if (invert != null) options.put("invert", invert);
        if (blur != null) options.put("blur", blur);
        if (resize != null) options.put("resize", resize);
        if (watermark != null) options.put("watermark", watermark);
        System.out.println("TaskController: Options: " + options);

        Map<String, Object> payload = Map.of(
                "s3Url", s3Url,
                "fileName", file.getOriginalFilename(),
                "options", options
        );

        Task task = new Task(type, payload);
        task.setId(java.util.UUID.randomUUID().toString());
        task.setStatus("queued");
        
        System.out.println("TaskController: Created task with ID: " + task.getId());
        System.out.println("TaskController: Task payload: " + payload);

        try {
            System.out.println("TaskController: Saving task to database...");
            taskDBClient.createTask(task);
            System.out.println("TaskController: Task saved to database successfully");
        } catch (Exception e) {
            System.err.println("TaskController ERROR: TaskDBClient error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            throw new Exception("Failed to save task to DB.");
        }

        try {
            System.out.println("TaskController: Enqueuing task to queue service...");
            queueServiceClient.enqueueTask(task);
            System.out.println("TaskController: Task enqueued successfully");
        } catch (FeignException e) {
            System.err.println("TaskController ERROR: Failed to enqueue task: " + e.contentUTF8());
            System.out.println("TaskController: Marking task as failed in database...");
            taskDBClient.markTaskFailed(task.getId(), "Failed to enqueue task");
            throw new Exception("Failed to enqueue task.");
        } catch (Exception e) {
            System.err.println("TaskController ERROR: Unexpected error during enqueue: " + e.getMessage());
            e.printStackTrace();
            System.out.println("TaskController: Marking task as failed in database...");
            taskDBClient.markTaskFailed(task.getId(), "Failed to enqueue task: " + e.getMessage());
            throw new Exception("Failed to enqueue task.");
        }

        System.out.println("TaskController: Upload process completed successfully");
        SubmitResponse body = new SubmitResponse(task.getId(), task.getStatus());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    public record SubmitResponse(String id, String status) {}
}
