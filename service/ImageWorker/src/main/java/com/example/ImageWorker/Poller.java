package com.example.ImageWorker;

import com.example.ImageWorker.Clients.QueueServiceClient;
import com.example.shared.Task;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class Poller {
    private final QueueServiceClient queueServiceClient;
    private final ImageWorkerService imageWorkerService;

    public Poller(final QueueServiceClient queueServiceClient,
                  final ImageWorkerService imageWorkerService) {
        this.queueServiceClient = queueServiceClient;
        this.imageWorkerService = imageWorkerService;
    }

    @Scheduled(fixedDelay = 5000)
    public void pollQueue(){
        System.out.println("=== IMAGE WORKER POLLER: Polling queue for image tasks ===");
        try{
            System.out.println("ImageWorker Poller: Calling queueServiceClient.getFrontTask()...");
            final Task task = queueServiceClient.getFrontTask();
            if(task == null){
                System.out.println("ImageWorker Poller: No tasks found in queue");
                return;
            }
            
            System.out.println("ImageWorker Poller: Found task - ID: " + task.getId() + ", Type: " + task.getType());
            
            if("image".equalsIgnoreCase(task.getType())){
                System.out.println("ImageWorker Poller: Processing image task...");
                System.out.println("ImageWorker Poller: Calling queueServiceClient.popFrontTask()...");
                final Task dequeued = queueServiceClient.popFrontTask(task.getId());
                
                if(dequeued == null) {
                    System.err.println("ImageWorker Poller ERROR: Failed to dequeue task");
                    return;
                }
                
                System.out.println("ImageWorker Poller: Task dequeued successfully - ID: " + dequeued.getId());
                
                final var payload = dequeued.getPayload();
                final String s3Url = (String) payload.get("s3Url");
                final String fileName = (String) payload.get("fileName");
                final Map<String, Object> options = (Map<String, Object>) payload.get("options");

                System.out.println("ImageWorker Poller: Task details - File: " + fileName + ", Options: " + options);
                System.out.println("ImageWorker Poller: Calling imageWorkerService.process()...");
                
                imageWorkerService.process(dequeued);
                System.out.println("ImageWorker Poller: Image processing completed successfully");
                
                System.out.println("ImageWorker Poller: Marking task as completed...");
                queueServiceClient.markTaskAsCompleted(dequeued);
                System.out.println("ImageWorker Poller: Task marked as completed successfully");
            } else {
                System.out.println("ImageWorker Poller: Skipping non-image task type: " + task.getType());
            }
        } catch (Exception e) {
            System.err.println("ImageWorker Poller ERROR: Error while polling image queue: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
