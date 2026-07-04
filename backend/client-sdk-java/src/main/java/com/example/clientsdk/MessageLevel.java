package com.example.clientsdk;

/** Message urgency level. {@code URGENT} triggers an immediate WebSocket push to recipients. */
public enum MessageLevel {
  URGENT,
  IMPORTANT,
  NORMAL
}
