package com.example.task_queue;

import com.example.shared.Task;
import com.example.task_queue.Clients.QueueServiceClient;
import com.example.task_queue.Clients.TaskDBClient;
import com.example.task_queue.S3Service.S3Service;
import feign.FeignException;
import org.springframework.cloud.client.discovery.DiscoveryClient;
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
    private final DiscoveryClient discoveryClient;

    public TaskController(
            final S3Service s3Service,
            final QueueServiceClient queueServiceClient,
            final TaskDBClient taskDBClient,
            final DiscoveryClient discoveryClient
    ) {
        this.s3Service = s3Service;
        this.queueServiceClient = queueServiceClient;
        this.taskDBClient = taskDBClient;
        this.discoveryClient = discoveryClient;
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

        Path tempFile;
        String s3Url;

        try {
            tempFile = Files.createTempFile("upload-", "-" + file.getOriginalFilename());
            file.transferTo(tempFile.toFile());
        } catch (Exception e) {
            System.err.println(e.getMessage());
            throw new Exception("File upload failed during temp storage.");
        }

        try {
            s3Url = s3Service.uploadFile(tempFile, file.getOriginalFilename());
        } catch (Exception e) {
            System.err.println(e.getMessage());
            throw new Exception("S3 upload failed.");
        }

        try {
            Files.deleteIfExists(tempFile);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }

        Map<String, Object> options = new HashMap<>();
        if (grayscale != null) options.put("grayscale", grayscale);
        if (invert != null) options.put("invert", invert);
        if (blur != null) options.put("blur", blur);
        if (resize != null) options.put("resize", resize);
        if (watermark != null) options.put("watermark", watermark);

        Map<String, Object> payload = Map.of(
                "s3Url", s3Url,
                "fileName", file.getOriginalFilename(),
                "options", options
        );

        Task task = new Task(type, payload);
        task.setStatus("processing");

        try {
            taskDBClient.createTask(task);
        } catch (FeignException e) {
            System.err.println(e.contentUTF8());
            throw new Exception("Failed to save task to DB.");
        }

        try {
            queueServiceClient.enqueueTask(task);
        } catch (FeignException e) {
            System.err.println(e.contentUTF8());
            taskDBClient.markTaskFailed(task.getId(), "Failed to enqueue task");
            throw new Exception("Failed to enqueue task.");
        }

        SubmitResponse body = new SubmitResponse(task.getId(), task.getStatus());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    public record SubmitResponse(String id, String status) {}
}
