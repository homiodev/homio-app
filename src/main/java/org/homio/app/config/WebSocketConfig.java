package org.homio.app.config;

import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.app.ssh.SSHServerEndpoint;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.messaging.context.AuthenticationPrincipalArgumentResolver;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Log4j2
@Configuration
@EnableWebSocket
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer, WebSocketConfigurer {

  public static final String DESTINATION_PREFIX = "/smart-dest-ws";
  static final String ENDPOINT = "/smart-websocket";

  @Autowired
  private SSHServerEndpoint sshServerEndpoint;

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    // These are endpoints the client can subscribe to.
    config.enableSimpleBroker(DESTINATION_PREFIX, "/webssh");
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

    registry.addEndpoint(ENDPOINT).setAllowedOrigins("*");
  }

  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
    argumentResolvers.add(new AuthenticationPrincipalArgumentResolver());
  }

  // Register endpoint for ssh ws sockets
  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry webSocketHandlerRegistry) {
    webSocketHandlerRegistry.addHandler(sshServerEndpoint, "/webssh")
                            .addInterceptors(new WebSSHSocketInterceptor())
                            .setAllowedOrigins("*");
  }

  private class WebSSHSocketInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(@NotNull ServerHttpRequest serverHttpRequest, @NotNull ServerHttpResponse response,
        @NotNull WebSocketHandler wsHandler, @NotNull Map<String, Object> attributes) {
      if (serverHttpRequest instanceof ServletServerHttpRequest) {
        ServletServerHttpRequest request = (ServletServerHttpRequest) serverHttpRequest;
        String token = request.getServletRequest().getParameter("token");
        if (!StringUtils.isEmpty(token) && sshServerEndpoint.hasToken(token)) {
          attributes.put("token", token);
          return true;
        }
      }
      return false;
    }

    @Override
    public void afterHandshake(@NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response, @NotNull WebSocketHandler wsHandler,
        Exception exception) {
    }
  }
}
