package com.example.ImageWorker;

import com.example.ImageWorker.Clients.QueueServiceClient;
import com.example.shared.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
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
    public void pollQueue() {
        log.info("Polling queue for image tasks");

        try {
            log.debug("Calling queueServiceClient.getFrontTask()");
            final Task task = queueServiceClient.getFrontTask();

            if (task == null) {
                log.debug("No tasks found in queue");
                return;
            }

            log.info("Found task - ID: {}, Type: {}", task.getId(), task.getType());

            if ("image".equalsIgnoreCase(task.getType())) {
                log.info("Processing image task {}", task.getId());

                log.debug("Calling queueServiceClient.popFrontTask() for task {}", task.getId());
                final Task dequeued = queueServiceClient.popFrontTask(task.getId());

                if (dequeued == null) {
                    log.warn("Failed to dequeue task {}", task.getId());
                    return;
                }

                log.info("Task dequeued successfully - ID: {}", dequeued.getId());

                final var payload = dequeued.getPayload();
                final String s3Key = (String) payload.get("key");
                final String fileName = (String) payload.get("fileName");

                @SuppressWarnings("unchecked")
                final Map<String, Object> options = (Map<String, Object>) payload.get("options");

                log.debug("Task details - S3 Key: {}, File: {}, Options: {}", s3Key, fileName, options);

                log.info("Starting image processing for task {}", dequeued.getId());
                imageWorkerService.process(dequeued);
                log.info("Image processing completed successfully for task {}", dequeued.getId());

                log.info("Marking task {} as completed", dequeued.getId());
                queueServiceClient.markTaskAsCompleted(dequeued);
                log.info("Task {} marked as completed", dequeued.getId());

            } else {
                log.info("Skipping non-image task type: {}", task.getType());
            }

        } catch (Exception e) {
            log.error("Error while polling image queue: {}", e.getMessage(), e);
        }
    }
}
