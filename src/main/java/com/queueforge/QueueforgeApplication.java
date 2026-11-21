package com.queueforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class QueueforgeApplication {
	public static void main(String[] args) {
		SpringApplication.run(QueueforgeApplication.class, args);
	}
}