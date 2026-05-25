package com.imageencrypter.app.listener;

import com.imageencrypter.app.controller.NotificationController;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DoneListener {

    private final NotificationController notificationController;

    private static final Pattern JOB_ID_PATTERN = Pattern.compile("\"jobId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern IMAGE_ID_PATTERN = Pattern.compile("\"imageId\"\\s*:\\s*(\\d+)");

    public DoneListener(NotificationController notificationController) {
        this.notificationController = notificationController;
    }

    @RabbitListener(queues = "image.done.queue")
    public void onDone(byte[] message) {
        try {
            String json = new String(message);
            System.out.println("Received done message: " + json);

            Matcher jobMatcher = JOB_ID_PATTERN.matcher(json);
            Matcher imgMatcher = IMAGE_ID_PATTERN.matcher(json);

            if (jobMatcher.find() && imgMatcher.find()) {
                String jobId = jobMatcher.group(1);
                int imageId = Integer.parseInt(imgMatcher.group(1));

                System.out.printf("Job %s completed, image id=%d%n", jobId, imageId);
                notificationController.notifyDone(jobId, imageId);
            } else {
                System.err.println("Could not parse done message: " + json);
            }
        } catch (Exception e) {
            System.err.println("Error processing done message: " + e.getMessage());
        }
    }
}
