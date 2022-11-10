package org.touchhome.app.config;

import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.messaging.context.AuthenticationPrincipalArgumentResolver;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

@Log4j2
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  public static final String DESTINATION_PREFIX = "/smart-dest-ws";
  static final String ENDPOINT = "/smart-websocket";

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
        log.error("WebSocket error: <{}>", ex.getMessage());
        return null; // WebSocket avoid response error messages to client
      }
    });

    registry.addEndpoint(ENDPOINT).setAllowedOrigins("*");
  }

  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
    argumentResolvers.add(new AuthenticationPrincipalArgumentResolver());
  }
}
