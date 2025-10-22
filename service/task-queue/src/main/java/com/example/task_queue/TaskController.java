package com.example.task_queue;

import com.example.shared.Task;
import com.example.task_queue.Clients.QueueServiceClient;
import com.example.task_queue.Clients.TaskDBClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final QueueServiceClient queueServiceClient;
    private final TaskDBClient taskDBClient;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final String lambdaUploadUrl;

    public TaskController(
            final QueueServiceClient queueServiceClient,
            final TaskDBClient taskDBClient,
            @Value("${aws.lambda.upload.url}") final String lambdaUploadUrl
    ) {
        this.queueServiceClient = queueServiceClient;
        this.taskDBClient = taskDBClient;
        this.httpClient = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
        this.lambdaUploadUrl = lambdaUploadUrl;
    }

    @GetMapping("/")
    public String index() {
        log.info("Health check request received: TaskController active");
        return "Task Controller active";
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("type") final String type,
            @RequestParam("content") final MultipartFile file,
            @RequestParam(value = "grayscale", required = false) final Boolean grayscale,
            @RequestParam(value = "invert", required = false) final Boolean invert,
            @RequestParam(value = "blur", required = false) final Boolean blur,
            @RequestParam(value = "resize", required = false) final Boolean resize,
            @RequestParam(value = "watermark", required = false) final Boolean watermark
    ) throws Exception {

        final long startTime = System.currentTimeMillis();
        final String taskId = UUID.randomUUID().toString();
        log.info("Received upload request for file '{}' of type '{}' (Task ID: {})",
                file.getOriginalFilename(), type, taskId);

        final Map<String, Object> options = new HashMap<>();
        if (grayscale != null) options.put("grayscale", grayscale);
        if (invert != null) options.put("invert", invert);
        if (blur != null) options.put("blur", blur);
        if (resize != null) options.put("resize", resize);
        if (watermark != null) options.put("watermark", watermark);
        log.debug("Task {} options: {}", taskId, options);

        final String s3Key;
        try {
            log.info("Uploading file '{}' for task {} to Lambda endpoint", file.getOriginalFilename(), taskId);
            s3Key = uploadFileToLambda(lambdaUploadUrl, taskId, file);
            log.info("Task {} successfully uploaded to S3 with key {}", taskId, s3Key);
        } catch (Exception e) {
            log.error("Task {} failed during Lambda upload: {}", taskId, e.getMessage(), e);
            throw e;
        }

        final Map<String, Object> payload = Map.of(
                "key", s3Key,
                "fileName", file.getOriginalFilename(),
                "options", options
        );

        final Task task = new Task(type, payload);
        task.setId(taskId);
        task.setStatus("queued");

        try {
            log.info("Persisting task {} in database", taskId);
            taskDBClient.createTask(task);
            log.info("Task {} successfully saved to DB", taskId);
        } catch (Exception e) {
            log.error("Database write failed for task {}: {}", taskId, e.getMessage(), e);
            throw e;
        }

        try {
            log.info("Enqueuing task {} to QueueService", taskId);
            queueServiceClient.enqueueTask(task);
            log.info("Task {} successfully enqueued", taskId);
        } catch (FeignException e) {
            log.error("Failed to enqueue task {}: {}", taskId, e.contentUTF8(), e);
            taskDBClient.markTaskFailed(taskId, "Failed to enqueue task");
            throw new Exception("Failed to enqueue task: " + e.contentUTF8());
        } catch (Exception e) {
            log.error("Unexpected enqueue error for task {}: {}", taskId, e.getMessage(), e);
            taskDBClient.markTaskFailed(taskId, "Unexpected enqueue error: " + e.getMessage());
            throw e;
        }

        final long duration = System.currentTimeMillis() - startTime;
        log.info("Task {} completed upload and enqueue in {} ms", taskId, duration);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new SubmitResponse(task.getId(), task.getStatus()));
    }

    private String uploadFileToLambda(final String lambdaUrl, final String taskId, final MultipartFile file) throws Exception {
        final var body = Map.of(
                "taskId", taskId,
                "fileName", file.getOriginalFilename(),
                "fileContent", Base64.getEncoder().encodeToString(file.getBytes())
        );

        log.debug("Sending upload request to Lambda for task {}", taskId);
        final var request = HttpRequest.newBuilder()
                .uri(URI.create(lambdaUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        final var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.debug("Lambda response for task {}: HTTP {} - {}", taskId, response.statusCode(), response.body());

        if (response.statusCode() != 200) {
            log.error("Lambda upload failed for task {}: {}", taskId, response.body());
            throw new Exception("Lambda upload failed: " + response.body());
        }

        final var json = mapper.readTree(response.body());
        final String key = json.get("key").asText();
        log.info("Lambda upload for task {} returned key {}", taskId, key);
        return key;
    }

    public record SubmitResponse(String id, String status) {}
}
