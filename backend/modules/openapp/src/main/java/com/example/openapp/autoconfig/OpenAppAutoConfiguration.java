package com.example.openapp.autoconfig;

import com.example.openapp.config.OpenAppProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@ComponentScan(basePackages = "com.example.openapp")
@EnableConfigurationProperties(OpenAppProperties.class)
@EnableScheduling
public class OpenAppAutoConfiguration {}
