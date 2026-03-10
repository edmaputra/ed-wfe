package com.ed.workflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@org.springframework.scheduling.annotation.EnableScheduling
public class WorkflowEngineApplication {

	static void main(String[] args) {
		SpringApplication.run(WorkflowEngineApplication.class, args);
	}

}
