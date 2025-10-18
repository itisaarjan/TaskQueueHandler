package com.example.TaskDBService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class TaskDbServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(TaskDbServiceApplication.class, args);
	}

}
