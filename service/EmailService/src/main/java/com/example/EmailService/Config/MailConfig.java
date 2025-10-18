package com.example.EmailService.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class MailConfig {
    @Bean
    public JavaMailSender mailSender() {
        final JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(System.getenv().getOrDefault("MAIL_HOST", "smtp.gmail.com"));
        mailSender.setPort(Integer.parseInt(System.getenv().getOrDefault("MAIL_PORT", "587")));
        mailSender.setUsername(System.getenv("MAIL_USERNAME"));
        mailSender.setPassword(System.getenv("MAIL_PASSWORD"));

        final Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.debug", "false");
        return mailSender;
    }
}