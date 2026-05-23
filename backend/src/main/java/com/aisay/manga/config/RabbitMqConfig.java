package com.aisay.manga.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableRabbit
public class RabbitMqConfig {

    @Bean
    public DirectExchange aiExchange() {
        return new DirectExchange(AiRabbitConstants.EXCHANGE, true, false);
    }

    @Bean
    public Queue storyRequestQueue() {
        return QueueBuilder.durable(AiRabbitConstants.STORY_REQUEST_QUEUE).build();
    }

    @Bean
    public Queue storyResultQueue() {
        return QueueBuilder.durable(AiRabbitConstants.STORY_RESULT_QUEUE).build();
    }

    @Bean
    public Queue chatPersistQueue() {
        return QueueBuilder.durable(AiRabbitConstants.CHAT_PERSIST_QUEUE).build();
    }

    @Bean
    public Binding storyRequestBinding(
            @Qualifier("storyRequestQueue") Queue storyRequestQueue,
            DirectExchange aiExchange
    ) {
        return BindingBuilder.bind(storyRequestQueue)
                .to(aiExchange)
                .with(AiRabbitConstants.STORY_REQUEST_ROUTING_KEY);
    }

    @Bean
    public Binding storyResultBinding(
            @Qualifier("storyResultQueue") Queue storyResultQueue,
            DirectExchange aiExchange
    ) {
        return BindingBuilder.bind(storyResultQueue)
                .to(aiExchange)
                .with(AiRabbitConstants.STORY_RESULT_ROUTING_KEY);
    }

    @Bean
    public Binding chatPersistBinding(
            @Qualifier("chatPersistQueue") Queue chatPersistQueue,
            DirectExchange aiExchange
    ) {
        return BindingBuilder.bind(chatPersistQueue)
                .to(aiExchange)
                .with(AiRabbitConstants.CHAT_PERSIST_ROUTING_KEY);
    }

    @Bean
    public MessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
