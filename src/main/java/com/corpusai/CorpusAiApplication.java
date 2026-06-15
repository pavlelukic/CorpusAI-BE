package com.corpusai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CorpusAiApplication {

	public static void main(String[] args) {
		SpringApplication.run(CorpusAiApplication.class, args);
	}

}
