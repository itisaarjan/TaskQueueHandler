package com.example.ImageWorker;

import com.example.ImageWorker.Clients.QueueServiceClient;
import com.example.shared.Task;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
        try{
            final Task task = queueServiceClient.getFrontTask();
            if(task != null){
                return;
            }

            if("image".equalsIgnoreCase(task.getType())){
                imageWorkerService.process(task);
                queueServiceClient.markTaskAsCompleted(task);
            }
        } catch (Exception e) {
            System.err.println("Error while polling queue: " + e.getMessage());
        }
    }
}
