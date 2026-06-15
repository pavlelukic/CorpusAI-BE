package com.corpusai.config;

import com.corpusai.subject.Subject;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "corpusai")
public record SubjectsProperties(List<Subject> subjects) {
}
