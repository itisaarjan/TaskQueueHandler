package com.example.task_queue.S3Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.file.Path;
import java.util.UUID;

@Service
public class S3Service {
    private final S3Client s3Client;
    private final String bucketName;
    public S3Service(
            @Value("${aws.region}") String region,
            @Value("${aws.s3.bucket}") String bucketName
    ){
        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        this.bucketName = bucketName;
        System.out.println("Service has been created");
    }

    public String uploadFile(Path filePath, String originalFileName) throws Exception {
        String key = "uploads/" + UUID.randomUUID() + "/" + originalFileName;

        try{
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .build(),
                    RequestBody.fromFile(filePath)
            );

            return "https://" + bucketName + "/" + key;
        }catch(Exception e){
            e.printStackTrace();
            throw new Exception("Error uploading file");
        }
    }

    public void deleteFile(String key) throws Exception {
        try{
            s3Client.deleteObject(b -> b.bucket(bucketName).key(key));
        }catch (Exception e){
            throw new Exception("Error deleting file");
        }
    }

}