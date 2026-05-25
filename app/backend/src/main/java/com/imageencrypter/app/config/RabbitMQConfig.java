package com.imageencrypter.app.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "image.exchange";
    public static final String PROCESS_QUEUE = "image.process.queue";
    public static final String DONE_QUEUE = "image.done.queue";
    public static final String PROCESS_KEY = "image.process";
    public static final String DONE_KEY = "image.done";

    @Bean
    public TopicExchange imageExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue processQueue() {
        return QueueBuilder.durable(PROCESS_QUEUE).build();
    }

    @Bean
    public Queue doneQueue() {
        return QueueBuilder.durable(DONE_QUEUE).build();
    }

    @Bean
    public Binding processBinding(Queue processQueue, TopicExchange imageExchange) {
        return BindingBuilder.bind(processQueue).to(imageExchange).with(PROCESS_KEY);
    }

    @Bean
    public Binding doneBinding(Queue doneQueue, TopicExchange imageExchange) {
        return BindingBuilder.bind(doneQueue).to(imageExchange).with(DONE_KEY);
    }
}
