package com.example.ImageWorker;

import com.example.shared.Task;
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
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;

@Service
public class ImageWorkerService {

    private final S3Client s3Client;
    private final String bucketName;
    private static final int MAX_WIDTH = 1024;
    private static final int MAX_HEIGHT = 1024;

    public ImageWorkerService(
            @Value("${aws.s3.bucket}") final String bucketName,
            @Value("${aws.region:us-east-1}") final String region
    ) {
        this.bucketName = bucketName;
        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    public void process(final Task task) throws Exception {
        long start = System.currentTimeMillis();

        final String s3Url = (String) task.getPayload().get("s3Url");
        final String fileName = (String) task.getPayload().get("fileName");
        final Map<String, Object> options = (Map<String, Object>) task.getPayload().get("options");

        System.out.println("Starting image task: " + fileName + " with options " + options);

        final Path input = Files.createTempFile("input-", "-" + fileName);
        final Path output = Files.createTempFile("processed-", "-" + fileName.replaceAll("\\..+$", ".jpg"));

        downloadFromS3(s3Url, input);

        BufferedImage img = ImageIO.read(input.toFile());
        if (img == null) throw new IOException("Invalid image format for: " + fileName);

        if (getBoolean(options, "resize")) img = resizeIfNeeded(img);
        if (getBoolean(options, "grayscale")) img = convertToGrayscale(img);
        if (getBoolean(options, "invert")) img = invertColors(img);
        if (getBoolean(options, "blur")) img = blurImage(img);
        if (options.get("watermark") != null)
            img = addWatermark(img, options.get("watermark").toString());

        compressToJPEG(img, output, 0.85f);

        final String key = String.format("processed/%s/%d-%s",
                task.getId(), Instant.now().getEpochSecond(), fileName.replaceAll("\\..+$", ".jpg"));

        uploadToS3(output, key);

        Files.deleteIfExists(input);
        Files.deleteIfExists(output);

        final double time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.printf("Completed image task in %.2fs → uploaded to %s%n", time, key);
    }

    private BufferedImage resizeIfNeeded(BufferedImage img) {
        final int width = img.getWidth(), height = img.getHeight();
        if (width <= MAX_WIDTH && height <= MAX_HEIGHT) return img;

        final double scale = Math.min((double) MAX_WIDTH / width, (double) MAX_HEIGHT / height);
        int newW = (int) (width * scale), newH = (int) (height * scale);

        final Image scaled = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
        final BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = resized.createGraphics();
        g.drawImage(scaled, 0, 0, null);
        g.dispose();
        System.out.println("Resized to " + newW + "x" + newH);
        return resized;
    }

    private BufferedImage convertToGrayscale(BufferedImage original) {
        BufferedImage gray = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = gray.createGraphics();
        g2d.drawImage(original, 0, 0, null);
        g2d.dispose();
        System.out.println("Converted to grayscale");
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
        System.out.println("Inverted colors");
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
        System.out.println("Applied blur filter");
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
        System.out.println("Added watermark: " + text);
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
        } catch (Exception e) {
            throw new IOException("Failed to upload file to S3: " + e.getMessage());
        }
    }

    private void downloadFromS3(final String s3Url, final Path dest) throws IOException {
        try (final InputStream in = new URL(s3Url).openStream();
             final OutputStream out = new FileOutputStream(dest.toFile())) {
            in.transferTo(out);
        } catch (Exception e) {
            throw new IOException("Failed to download file from S3: " + e.getMessage());
        }
    }

    private boolean getBoolean(Map<String, Object> options, String key) {
        Object val = options.get(key);
        return val != null && Boolean.parseBoolean(val.toString());
    }
}
