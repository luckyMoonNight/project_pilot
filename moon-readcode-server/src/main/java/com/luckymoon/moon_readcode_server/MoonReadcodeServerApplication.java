package com.luckymoon.moon_readcode_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MoonReadcodeServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(MoonReadcodeServerApplication.class, args);
	}

}
