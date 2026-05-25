package com.imageencrypter.subscriber;

import com.google.gson.Gson;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.Delivery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class MessageHandler implements DeliverCallback {

    private static final String EXCHANGE = "image.exchange";
    private static final String DONE_KEY = "image.done";
    private static final Gson GSON = new Gson();

    private final Channel channel;
    private final String storageHost;

    public MessageHandler(Channel channel, String storageHost) {
        this.channel = channel;
        this.storageHost = storageHost;
    }

    @Override
    public void handle(String consumerTag, Delivery delivery) throws IOException {
        Map<String, Object> headers = delivery.getProperties().getHeaders();

        String jobId = getHeader(headers, "jobId");
        String operation = getHeader(headers, "operation");
        String mode = getHeader(headers, "mode");
        String key = getHeader(headers, "key");
        String originalName = getHeader(headers, "originalName");
        String userEmail = getHeader(headers, "userEmail");
        byte[] imageBytes = delivery.getBody();

        System.out.printf("Received job %s: %s %s (%d bytes)%n",
                jobId, operation, mode, imageBytes.length);

        // Debug: print first bytes to verify BMP signature
        if (imageBytes.length >= 4) {
            System.out.printf("First 4 bytes: %02x %02x %02x %02x%n",
                    imageBytes[0], imageBytes[1], imageBytes[2], imageBytes[3]);
        }

        try {
            // Write input BMP to temp file
            Path inputPath = Path.of("/tmp/input_" + jobId + ".bmp");
            Path outputPath = Path.of("/tmp/output_" + jobId + ".bmp");
            Files.write(inputPath, imageBytes);

            // Launch MPI encryption
            MpiLauncher.run(inputPath.toString(), outputPath.toString(),
                    operation, mode, key);

            // Read the result
            byte[] resultBytes = Files.readAllBytes(outputPath);
            System.out.printf("Job %s: MPI completed, result is %d bytes%n",
                    jobId, resultBytes.length);

            // Store in MySQL via storage REST API
            int imageId = StorageClient.store(storageHost, resultBytes,
                    jobId, operation, mode, originalName, userEmail);
            System.out.printf("Job %s: stored as image id=%d%n", jobId, imageId);

            // Publish done notification
            Map<String, Object> doneMsg = Map.of(
                    "jobId", jobId,
                    "imageId", imageId
            );
            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                    .contentType("application/json")
                    .build();
            channel.basicPublish(EXCHANGE, DONE_KEY, props,
                    GSON.toJson(doneMsg).getBytes());
            System.out.printf("Job %s: done notification sent%n", jobId);

            // Acknowledge the message
            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);

            // Cleanup
            Files.deleteIfExists(inputPath);
            Files.deleteIfExists(outputPath);

        } catch (Exception e) {
            System.err.printf("Job %s failed: %s%n", jobId, e.getMessage());
            e.printStackTrace();
            // Reject and requeue
            channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
        }
    }

    private String getHeader(Map<String, Object> headers, String key) {
        if (headers == null || !headers.containsKey(key)) return "";
        Object val = headers.get(key);
        return val != null ? val.toString() : "";
    }
}
