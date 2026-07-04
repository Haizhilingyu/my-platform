package com.example.notify.websocket;

public final class WebSocketMessages {

  private WebSocketMessages() {}

  public static final String FIELD_TYPE = "type";
  public static final String FIELD_LAST_SEQ = "lastSeqReceived";

  public static final String TYPE_REPLAY_REQUEST = "replay";
}
