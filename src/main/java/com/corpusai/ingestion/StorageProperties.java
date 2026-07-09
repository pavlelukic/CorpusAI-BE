package com.corpusai.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "corpusai.storage")
public record StorageProperties(String root) {
}
