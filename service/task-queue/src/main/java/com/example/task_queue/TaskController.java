package com.example.task_queue;

import com.example.shared.Task;
import com.example.task_queue.Clients.QueueServiceClient;
import com.example.task_queue.S3Service.S3Service;
import org.springframework.cloud.client.discovery.DiscoveryClient;
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
    private final DiscoveryClient discoveryClient;


    public TaskController(final S3Service s3Service, QueueServiceClient queueServiceClient, final DiscoveryClient discoveryClient) {
        this.s3Service = s3Service;
        System.out.println("Controller has been created");
        this.queueServiceClient = queueServiceClient;
        this.discoveryClient=discoveryClient;
    }
    @GetMapping("/")
    public String index(){
        return "index";
    }
    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("type") String type,
                                         @RequestParam("content") MultipartFile file,
                                         @RequestParam(value = "grayscale", required = false) final Boolean grayscale,
                                         @RequestParam(value = "invert", required = false) final Boolean invert,
                                         @RequestParam(value = "blur", required = false) final Boolean blur,
                                         @RequestParam(value = "resize", required = false) final Boolean resize,
                                         @RequestParam(value = "watermark", required = false) final Boolean watermark
    ) throws Exception{
        final Path tempFile;
        final Map<String, Object> payload;
        final Task task;
        final String s3Url;
        try {
            tempFile = Files.createTempFile("upload-", "-" + file.getOriginalFilename());
        }catch (Exception e){
            System.err.println(e.getMessage());
            throw new Exception("Error occurred while uploading the file or the file is corrupted.");
        }

        try{
            file.transferTo(tempFile.toFile());
        }catch (Exception e){
            System.err.println(e.getMessage());
            throw new Exception("Error occurred while writing the uploaded file to temporary disk.");
        }

        try{
            s3Url = s3Service.uploadFile(tempFile,file.getOriginalFilename());
        }catch (Exception e){
            System.err.println(e.getMessage());
            throw new Exception("Error occured while uploading the file to s3.");
        }

        final Map<String, Object> options = new HashMap<>();
        if(grayscale!=null) options.put("grayscale", grayscale);
        if(invert!=null) options.put("invert", invert);
        if(blur!=null) options.put("blur", blur);
        if(resize!=null) options.put("resize", resize);
        if(watermark!=null) options.put("watermark", watermark);

        try{
            Files.deleteIfExists(tempFile);
        }catch (Exception e){
            System.err.println(e.getMessage());
            throw new Exception("File deletion Error Occurred.");
        }

        payload = Map.of(
                "s3Url", s3Url,
                "fileName", file.getOriginalFilename(),
                "options", options
        );

        task = new Task(type, payload);

        try{
            queueServiceClient.enqueueTask(task);
        }catch (FeignException e){
            System.err.println(e.contentUTF8());
            throw new Exception("Error occurred while enqueueing the task.");
        }
        final SubmitResponse body = new SubmitResponse(task.getId(), task.getStatus());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    public record SubmitResponse(String id, String status) {}
}