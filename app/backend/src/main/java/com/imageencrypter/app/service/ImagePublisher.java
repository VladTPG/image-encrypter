package com.imageencrypter.app.service;

import com.imageencrypter.app.config.RabbitMQConfig;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class ImagePublisher {

    private final RabbitTemplate rabbitTemplate;

    public ImagePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(byte[] imageBytes, String jobId, String operation,
                        String mode, String key, String originalName, String userEmail) {
        Message message = MessageBuilder
                .withBody(imageBytes)
                .setContentType(MessageProperties.CONTENT_TYPE_BYTES)
                .setHeader("jobId", jobId)
                .setHeader("operation", operation)
                .setHeader("mode", mode)
                .setHeader("key", key)
                .setHeader("originalName", originalName)
                .setHeader("userEmail", userEmail)
                .build();

        rabbitTemplate.send(RabbitMQConfig.EXCHANGE, RabbitMQConfig.PROCESS_KEY, message);
    }
}
