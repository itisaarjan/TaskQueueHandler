package com.example.EmailService;

import com.example.EmailService.Clients.QueueServiceClient;
import com.example.shared.EmailRequest;
import com.example.shared.Task;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

@Component
public class EmailPoller {
    private final QueueServiceClient queueServiceClient;
    private final EmailSenderService emailSenderService;

    public EmailPoller(final QueueServiceClient queueServiceClient,
                       final EmailSenderService emailSenderService) {
        this.queueServiceClient = queueServiceClient;
        this.emailSenderService = emailSenderService;
    }

    @Scheduled(fixedDelay = 5000)
    public void pollQueue() {
        try {
            final Task task = queueServiceClient.getFrontTask();
            if (task == null) return;

            if ("email".equalsIgnoreCase(task.getType())) {
                final Task dequeued = queueServiceClient.popFrontTask(task.getId());
                final var payload = dequeued.getPayload();
                final String to = (String) payload.get("to");
                final String subject = (String) payload.get("subject");
                final String body = (String) payload.get("body");
                final boolean html = payload.get("html") != null && Boolean.parseBoolean(payload.get("html").toString());

                final EmailRequest request = new EmailRequest(to, subject, body, html);
                emailSenderService.sendEmail(request);
                queueServiceClient.markTaskAsCompleted(dequeued);
            }
        } catch (Exception e) {
            System.err.println("Error while polling email queue: " + e.getMessage());
        }
    }
}