package com.example.task_queue;

import com.example.shared.Task;
import com.example.task_queue.QueueService.QueueService;
import com.example.task_queue.S3Service.S3Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/tasks")
public class TaskController {
    private final QueueService queueService;
    private final S3Service s3Service;


    public TaskController(  final QueueService queueService,
                            final S3Service s3Service
    ){
        this.queueService = queueService;
        this.s3Service = s3Service;
        System.out.println("Controller has been created");
    }
    @GetMapping("/")
    public String index(){
        return "index";
    }
    @PostMapping("/upload")
    public Task upload(@RequestParam("type") String type,
                       @RequestParam("content") MultipartFile file
    ) throws Exception{
        Path tempFile;
        Map<String, Object> payload;
        Task task;
        String s3Url;
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

        try{
            Files.deleteIfExists(tempFile);
        }catch (Exception e){
            System.err.println(e.getMessage());
            throw new Exception("File deletion Error Occurred.");
        }

        payload = Map.of(
                "s3Url", s3Url,
                "fileName", file.getOriginalFilename()
        );

        task = new Task(type, payload);

        try{
            queueService.enqueueTask(task);
        }catch (Exception e){
            System.err.println(e.getMessage());
            throw new Exception("Error occurred while enqueueing the task.");
        }
        return task;
    }


}