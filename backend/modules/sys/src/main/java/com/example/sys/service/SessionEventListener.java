package com.example.sys.service;

import com.example.common.login.LoginSuccessEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SessionEventListener {

  private final SessionService sessionService;

  @EventListener
  public void onLoginSuccess(LoginSuccessEvent event) {
    sessionService.recordSession(event);
  }
}
