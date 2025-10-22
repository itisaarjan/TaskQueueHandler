package com.example.EmailService;

import com.example.shared.EmailRequest;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailSenderService {

    private final JavaMailSender mailSender;

    public EmailSenderService(final JavaMailSender mailSender) {
        this.mailSender = mailSender;
        log.info("EmailSenderService initialized successfully");
    }

    public void sendEmail(final EmailRequest request) throws Exception {
        if (request == null) {
            log.warn("Received null EmailRequest — skipping email send");
            throw new IllegalArgumentException("EmailRequest cannot be null");
        }

        log.info("Preparing to send email to={}, subject={}, isHtml={}",
                request.getTo(), request.getSubject(), request.isHtml());

        try {
            if (request.isHtml()) {
                log.debug("Composing HTML email...");
                final MimeMessage mimeMessage = mailSender.createMimeMessage();
                final MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

                helper.setFrom("noreply@taskqueue.com");
                helper.setTo(request.getTo());
                helper.setSubject(request.getSubject());
                helper.setText(request.getBody(), true);

                mailSender.send(mimeMessage);
                log.info("Successfully sent HTML email to {}", request.getTo());
            } else {
                log.debug("Composing plain text email...");
                final SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom("noreply@taskqueue.com");
                message.setTo(request.getTo());
                message.setSubject(request.getSubject());
                message.setText(request.getBody());

                mailSender.send(message);
                log.info("Successfully sent plain text email to {}", request.getTo());
            }
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", request.getTo(), e.getMessage(), e);
            throw new Exception("Email send failed: " + e.getMessage(), e);
        }
    }
}
