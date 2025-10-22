package com.example.ImageWorker;

import com.example.shared.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;

@Slf4j
@Service
public class ImageWorkerService {

    private final S3Client s3Client;
    private final String bucketName;
    private final String region;
    private final String lambdaDownloadUrl;
    private final HttpClient httpClient;
    private static final int MAX_WIDTH = 1024;
    private static final int MAX_HEIGHT = 1024;

    public ImageWorkerService(
            @Value("${aws.s3.bucket}") final String bucketName,
            @Value("${aws.region:us-east-1}") final String region,
            @Value("${aws.lambda.download.url}") final String lambdaDownloadUrl
    ) {
        this.bucketName = bucketName;
        this.region = region;
        this.lambdaDownloadUrl = lambdaDownloadUrl;
        this.httpClient = HttpClient.newHttpClient();
        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        log.info("Initialized ImageWorkerService for region '{}' and bucket '{}'", region, bucketName);
    }

    public void process(final Task task) throws Exception {
        final long start = System.currentTimeMillis();
        log.info("Starting image processing for task ID: {}", task.getId());

        final String s3Key = (String) task.getPayload().get("key");
        final String fileName = (String) task.getPayload().get("fileName");
        @SuppressWarnings("unchecked")
        final Map<String, Object> options = (Map<String, Object>) task.getPayload().get("options");

        log.debug("Task {} payload: S3 Key={}, File={}, Options={}", task.getId(), s3Key, fileName, options);

        final Path input = Files.createTempFile("input-", "-" + fileName);
        final Path output = Files.createTempFile("processed-", "-" + fileName.replaceAll("\\..+$", ".jpg"));
        log.debug("Temporary input file: {}", input);
        log.debug("Temporary output file: {}", output);

        try {
            log.info("Downloading image from S3 via Lambda for task {}", task.getId());
            downloadFromLambda(s3Key, input);
            log.info("Successfully downloaded image for task {}", task.getId());

            BufferedImage img = ImageIO.read(input.toFile());
            if (img == null) {
                log.error("Invalid image format for file '{}'", fileName);
                throw new IOException("Invalid image format: " + fileName);
            }
            log.debug("Loaded image: {}x{}", img.getWidth(), img.getHeight());

            // Apply transformations
            if (getBoolean(options, "resize")) {
                log.info("Applying resize to task {}", task.getId());
                img = resizeIfNeeded(img);
            }
            if (getBoolean(options, "grayscale")) {
                log.info("Applying grayscale filter to task {}", task.getId());
                img = convertToGrayscale(img);
            }
            if (getBoolean(options, "invert")) {
                log.info("Applying color inversion to task {}", task.getId());
                img = invertColors(img);
            }
            if (getBoolean(options, "blur")) {
                log.info("Applying blur filter to task {}", task.getId());
                img = blurImage(img);
            }
            if (options.get("watermark") != null) {
                log.info("Adding watermark '{}' to task {}", options.get("watermark"), task.getId());
                img = addWatermark(img, options.get("watermark").toString());
            }

            log.info("Compressing task {} image to JPEG", task.getId());
            compressToJPEG(img, output, 0.85f);

            final String key = String.format("processed/%s/%d-%s",
                    task.getId(), Instant.now().getEpochSecond(),
                    fileName.replaceAll("\\..+$", ".jpg"));
            log.debug("Generated S3 output key: {}", key);

            log.info("Uploading processed image for task {} to S3", task.getId());
            uploadToS3(output, key);
            log.info("Successfully uploaded processed image to S3 with key {}", key);

            task.setResultUrl(key);
            log.debug("Set result S3 key for task {}: {}", task.getId(), task.getResultUrl());

        } catch (Exception e) {
            log.error("Error processing image for task {}: {}", task.getId(), e.getMessage(), e);
            throw e;
        } finally {
            log.debug("Cleaning up temporary files for task {}", task.getId());
            Files.deleteIfExists(input);
            Files.deleteIfExists(output);
        }

        final double time = (System.currentTimeMillis() - start) / 1000.0;
        log.info("Image processing for task {} completed in {}s", task.getId(), time);
    }

    // --- Image processing helpers ---

    private BufferedImage resizeIfNeeded(BufferedImage img) {
        int width = img.getWidth(), height = img.getHeight();
        if (width <= MAX_WIDTH && height <= MAX_HEIGHT) return img;

        double scale = Math.min((double) MAX_WIDTH / width, (double) MAX_HEIGHT / height);
        int newW = (int) (width * scale), newH = (int) (height * scale);

        Image scaled = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.drawImage(scaled, 0, 0, null);
        g.dispose();
        log.debug("Resized image to {}x{}", newW, newH);
        return resized;
    }

    private BufferedImage convertToGrayscale(BufferedImage original) {
        BufferedImage gray = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = gray.createGraphics();
        g2d.drawImage(original, 0, 0, null);
        g2d.dispose();
        log.debug("Converted image to grayscale");
        return gray;
    }

    private BufferedImage invertColors(BufferedImage img) {
        BufferedImage inverted = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgba = img.getRGB(x, y);
                Color col = new Color(rgba, true);
                col = new Color(255 - col.getRed(), 255 - col.getGreen(), 255 - col.getBlue());
                inverted.setRGB(x, y, col.getRGB());
            }
        }
        log.debug("Inverted image colors");
        return inverted;
    }

    private BufferedImage blurImage(BufferedImage img) {
        float[] kernel = {
                1 / 9f, 1 / 9f, 1 / 9f,
                1 / 9f, 1 / 9f, 1 / 9f,
                1 / 9f, 1 / 9f, 1 / 9f
        };
        ConvolveOp op = new ConvolveOp(new Kernel(3, 3, kernel));
        BufferedImage blurred = op.filter(img, null);
        log.debug("Applied blur filter");
        return blurred;
    }

    private BufferedImage addWatermark(BufferedImage img, String text) {
        Graphics2D g2d = (Graphics2D) img.getGraphics();
        g2d.setFont(new Font("Arial", Font.BOLD, 30));
        g2d.setColor(new Color(255, 255, 255, 180));
        FontMetrics fm = g2d.getFontMetrics();
        int x = img.getWidth() - fm.stringWidth(text) - 20;
        int y = img.getHeight() - 20;
        g2d.drawString(text, x, y);
        g2d.dispose();
        log.debug("Added watermark: {}", text);
        return img;
    }

    private void compressToJPEG(BufferedImage img, Path output, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) throw new IllegalStateException("No JPEG writers available");
        ImageWriter writer = writers.next();

        try (OutputStream os = new FileOutputStream(output.toFile());
             ImageOutputStream ios = ImageIO.createImageOutputStream(os)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(img, null, null), param);
            log.debug("Compressed image to JPEG with quality {}", quality);
        } finally {
            writer.dispose();
        }
    }

    private void uploadToS3(Path filePath, String key) throws IOException {
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .build(),
                    RequestBody.fromFile(filePath)
            );
            log.debug("Uploaded file to S3 key {}", key);
        } catch (Exception e) {
            log.error("Failed to upload file to S3: {}", e.getMessage(), e);
            throw new IOException("Failed to upload file to S3: " + e.getMessage());
        }
    }

    private void downloadFromLambda(final String s3Key, final Path dest) throws IOException {
        try {
            final String encodedKey = URLEncoder.encode(s3Key, StandardCharsets.UTF_8);
            final String url = lambdaDownloadUrl + "?key=" + encodedKey;
            log.debug("Downloading from Lambda URL: {}", url);

            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            final HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                log.error("Lambda download failed with status {}", response.statusCode());
                throw new IOException("Lambda download failed with status " + response.statusCode());
            }

            try (OutputStream out = new FileOutputStream(dest.toFile())) {
                out.write(response.body());
            }

            log.debug("Downloaded {} bytes from Lambda for key {}", response.body().length, s3Key);
        } catch (Exception e) {
            log.error("Failed to download file from Lambda: {}", e.getMessage(), e);
            throw new IOException("Failed to download file from Lambda: " + e.getMessage(), e);
        }
    }

    private boolean getBoolean(Map<String, Object> options, String key) {
        Object val = options.get(key);
        return val != null && Boolean.parseBoolean(val.toString());
    }
}
