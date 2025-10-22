package com.example.EmailService;

import com.example.EmailService.Clients.QueueServiceClient;
import com.example.shared.EmailRequest;
import com.example.shared.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EmailPoller {

    private final QueueServiceClient queueServiceClient;
    private final EmailSenderService emailSenderService;

    public EmailPoller(final QueueServiceClient queueServiceClient,
                       final EmailSenderService emailSenderService) {
        this.queueServiceClient = queueServiceClient;
        this.emailSenderService = emailSenderService;
        log.info("EmailPoller initialized successfully");
    }

    @Scheduled(fixedDelay = 5000)
    public void pollQueue() {
        log.debug("Polling queue for email tasks...");

        try {
            final Task task = queueServiceClient.getFrontTask();
            if (task == null) {
                log.trace("No tasks found in the email queue");
                return;
            }

            log.info("Found task ID={}, type={}", task.getId(), task.getType());

            if ("email".equalsIgnoreCase(task.getType())) {
                log.debug("Processing email task ID={}", task.getId());

                final Task dequeued = queueServiceClient.popFrontTask(task.getId());
                if (dequeued == null) {
                    log.warn("Task ID={} could not be dequeued (possibly already in processing)", task.getId());
                    return;
                }

                final var payload = dequeued.getPayload();
                final String to = (String) payload.get("to");
                final String subject = (String) payload.get("subject");
                final String body = (String) payload.get("body");
                final boolean html = payload.get("html") != null && Boolean.parseBoolean(payload.get("html").toString());

                log.info("Preparing email for recipient={}, subject={}", to, subject);

                final EmailRequest request = new EmailRequest(to, subject, body, html);
                emailSenderService.sendEmail(request);

                queueServiceClient.markTaskAsCompleted(dequeued);
                log.info("Successfully processed and completed email task ID={} for recipient={}", task.getId(), to);
            } else {
                log.debug("Skipping non-email task type: {}", task.getType());
            }

        } catch (Exception e) {
            log.error("Error while polling or processing email queue: {}", e.getMessage(), e);
        }
    }
}
