package com.queueforge.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE_NAME = "queueforge.jobs.exchange";
    public static final String QUEUE_NAME = "queueforge.jobs.default";
    public static final String ROUTING_KEY = "job.default";

    @Bean
    public DirectExchange jobExchange() {
        return new DirectExchange(EXCHANGE_NAME, true, false); // Durable
    }

    @Bean
    public Queue defaultQueue() {
        return QueueBuilder.durable(QUEUE_NAME)
                .build();
    }

    @Bean
    public Binding binding(Queue defaultQueue, DirectExchange jobExchange) {
        return BindingBuilder.bind(defaultQueue).to(jobExchange).with(ROUTING_KEY);
    }
}