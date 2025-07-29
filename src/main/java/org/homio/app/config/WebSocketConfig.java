package org.homio.app.config;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.messaging.context.AuthenticationPrincipalArgumentResolver;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;
import org.springframework.web.socket.server.support.OriginHandshakeInterceptor;

@Log4j2
@Configuration
@EnableWebSocket
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer, WebSocketConfigurer {

  public static final String DESTINATION_PREFIX = "/homio-dest-ws";
  public static final String CUSTOM_WEB_SOCKET_ENDPOINT = "/cws";
  static final String WEB_SOCKET_ENDPOINT = "/hws";

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    // These are endpoints the client can subscribe to.
    config.enableSimpleBroker(DESTINATION_PREFIX);
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.setErrorHandler(new StompSubProtocolErrorHandler() {
      @Override
      public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, @NotNull Throwable ex) {
        log.error("WebSocket error: <{}>", ex.getMessage());
        return null; // WebSocket avoid response error messages to client
      }
    });

    registry.addEndpoint(WEB_SOCKET_ENDPOINT).setAllowedOriginPatterns("*");
  }

  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
    argumentResolvers.add(new AuthenticationPrincipalArgumentResolver());
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry webSocketHandlerRegistry) {
    webSocketHandlerRegistry.addHandler(new AbstractWebSocketHandler() {
        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
          super.handleTransportError(session, exception);
          log.error("WebSocket transport error: <{}>", exception.getMessage());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
          super.afterConnectionClosed(session, status);
          log.warn("WebSocket connection closed: <{}>", session.getId());
        }

        @Override
        public void afterConnectionEstablished(@NotNull WebSocketSession session) throws Exception {
          super.afterConnectionEstablished(session);
          log.info("WebSocket connection established: <{}>", session.getId());
        }
      }, "/tttt")
      .addInterceptors(new OriginHandshakeInterceptor())
      .setAllowedOriginPatterns("*");
  }
}
