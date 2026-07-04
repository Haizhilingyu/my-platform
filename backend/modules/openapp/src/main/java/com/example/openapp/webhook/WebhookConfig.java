package com.example.openapp.webhook;

import com.example.openapp.client.JdbcRegisteredClientRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.web.client.RestTemplate;

@Configuration
public class WebhookConfig {

  @Bean
  public RestTemplate webhookRestTemplate() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);
    factory.setReadTimeout(5000);
    return new RestTemplate(factory);
  }

  @Bean
  public LogoutWebhookService logoutWebhookService(
      JdbcRegisteredClientRepository clientRepository, RestTemplate webhookRestTemplate) {
    return new LogoutWebhookService(clientRepository, webhookRestTemplate);
  }

  @Bean
  public HttpSessionEventPublisher httpSessionEventPublisher() {
    return new HttpSessionEventPublisher();
  }
}
