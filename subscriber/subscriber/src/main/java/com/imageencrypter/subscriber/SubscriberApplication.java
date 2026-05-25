package com.imageencrypter.subscriber;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class SubscriberApplication {

    private static final String EXCHANGE_NAME = "image.exchange";
    private static final String PROCESS_QUEUE = "image.process.queue";
    private static final String DONE_QUEUE = "image.done.queue";
    private static final String PROCESS_KEY = "image.process";
    private static final String DONE_KEY = "image.done";

    public static void main(String[] args) throws Exception {
        String rabbitHost = System.getenv("RABBITMQ_HOST");
        if (rabbitHost == null) rabbitHost = "broker";

        String storageHost = System.getenv("STORAGE_HOST");
        if (storageHost == null) storageHost = "storage";

        System.out.println("Connecting to RabbitMQ at " + rabbitHost + "...");

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitHost);
        factory.setPort(5672);
        factory.setUsername("guest");
        factory.setPassword("guest");

        // Retry connection with backoff
        Connection connection = null;
        for (int i = 0; i < 30; i++) {
            try {
                connection = factory.newConnection();
                break;
            } catch (Exception e) {
                System.out.println("Waiting for RabbitMQ... (" + (i + 1) + "/30)");
                Thread.sleep(2000);
            }
        }
        if (connection == null) {
            throw new RuntimeException("Could not connect to RabbitMQ after 30 attempts");
        }

        System.out.println("Connected to RabbitMQ");

        Channel channel = connection.createChannel();

        // Declare exchange and queues
        channel.exchangeDeclare(EXCHANGE_NAME, "topic", true);
        channel.queueDeclare(PROCESS_QUEUE, true, false, false, null);
        channel.queueDeclare(DONE_QUEUE, true, false, false, null);
        channel.queueBind(PROCESS_QUEUE, EXCHANGE_NAME, PROCESS_KEY);
        channel.queueBind(DONE_QUEUE, EXCHANGE_NAME, DONE_KEY);

        // Process one message at a time
        channel.basicQos(1);

        System.out.println("Waiting for messages on " + PROCESS_QUEUE + "...");

        MessageHandler handler = new MessageHandler(channel, storageHost);
        channel.basicConsume(PROCESS_QUEUE, false, handler, consumerTag -> {});

        // Keep alive
        Thread.currentThread().join();
    }
}
