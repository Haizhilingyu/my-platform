package com.example.notify.autoconfig;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ComponentScan(basePackages = "com.example.notify")
@EntityScan(basePackages = "com.example.notify.domain")
@EnableJpaRepositories(basePackages = "com.example.notify.repository")
public class NotifyAutoConfiguration {}
