package com.example.task_queue.S3Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Service
public class S3Service {

    private final S3Client s3Client;
    private final String bucketName;
    private final String region;

    public S3Service(
            @Value("${aws.region}") final String region,
            @Value("${aws.s3.bucket}") final String bucketName
    ) {
        this.region = region;
        this.bucketName = bucketName;
        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        log.info("S3Service initialized for region '{}' and bucket '{}'", region, bucketName);
    }

    public String uploadFile(final Path filePath, final String originalFileName) throws Exception {
        final String key = "uploads/" + UUID.randomUUID() + "/" + originalFileName;
        log.info("Uploading file '{}' to S3 bucket '{}' with key '{}'", originalFileName, bucketName, key);

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .build(),
                    RequestBody.fromFile(filePath)
            );

            final String s3Url = String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, key);
            log.info("File '{}' successfully uploaded to S3 (key: {}, URL: {})", originalFileName, key, s3Url);
            return s3Url;

        } catch (Exception e) {
            log.error("Failed to upload file '{}' to S3 bucket '{}': {}", originalFileName, bucketName, e.getMessage(), e);
            throw new Exception("Error uploading file: " + e.getMessage());
        }
    }

    public void deleteFile(final String key) throws Exception {
        log.info("Deleting S3 object '{}' from bucket '{}'", key, bucketName);
        try {
            s3Client.deleteObject(b -> b.bucket(bucketName).key(key));
            log.info("Successfully deleted S3 object '{}' from bucket '{}'", key, bucketName);
        } catch (Exception e) {
            log.error("Failed to delete S3 object '{}' from bucket '{}': {}", key, bucketName, e.getMessage(), e);
            throw new Exception("Error deleting file: " + e.getMessage());
        }
    }
}
