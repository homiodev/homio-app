package org.homio.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  public static final String DESTINATION_PREFIX = "/homio-dest-ws";
  static final String WEB_SOCKET_ENDPOINT = "/hws";

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    // These are endpoints the client can subscribes to.
    config.enableSimpleBroker(DESTINATION_PREFIX);
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.setErrorHandler(new StompSubProtocolErrorHandler() {
      @Override
      public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
        System.err.printf("WebSocket error: '%s'%n", ex.getMessage());
        return null; // WebSocket avoid response error messages to client
      }
    });

    registry.addEndpoint(WEB_SOCKET_ENDPOINT).setAllowedOrigins("*");
  }
}
