package com.remipreparateur;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RemiPreparateurApplication {

	public static void main(String[] args) {
		SpringApplication.run(RemiPreparateurApplication.class, args);
	}

}
