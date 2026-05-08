package com.app.chat_consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class ChatConsumerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChatConsumerApplication.class, args);
	}

}
