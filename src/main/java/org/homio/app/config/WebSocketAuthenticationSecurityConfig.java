package org.homio.app.config;

import lombok.extern.log4j.Log4j2;
import org.homio.app.auth.JwtTokenProvider;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Log4j2
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
@EnableWebSocketSecurity
public class WebSocketAuthenticationSecurityConfig implements WebSocketMessageBrokerConfigurer {

  @Override
  public void configureClientInboundChannel(final ChannelRegistration registration) {
    registration.interceptors(this.authChannelInterceptorAdapter(null));
  }

  @Bean
  public ChannelInterceptor authChannelInterceptorAdapter(JwtTokenProvider jwtTokenProvider) {
    return new ChannelInterceptor() {
      @Override
      public @NotNull Message<?> preSend(
          @NotNull Message<?> message, @NotNull MessageChannel channel) {
        StompHeaderAccessor accessor =
            MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT == accessor.getCommand()) {
          log.info("STOMP CONNECT frame received. Attempting authentication.");
          String auth = accessor.getFirstNativeHeader("Authorization");
          String token = jwtTokenProvider.resolveToken(auth);

          if (token != null && jwtTokenProvider.validateToken(token)) {
            log.info("Token is valid. Setting user in STOMP accessor.");
            accessor.setUser(jwtTokenProvider.getAuthentication(token));
          } else {
            // This is critical! If auth fails, the connection might hang.
            log.warn("STOMP CONNECT failed: Invalid or missing token.");
            // You might need to explicitly throw an exception here to terminate the connection
            // cleanly.
            // For example: throw new MessagingException("Authentication failed");
          }
        }
        return message;
      }
    };
  }

  @Bean
  public AuthorizationManager<Message<?>> messageAuthorizationManager(
      MessageMatcherDelegatingAuthorizationManager.Builder messages) {
    messages.anyMessage().authenticated();
    return messages.build();
  }

  // disable csrf for web-sockets
  @Bean
  public ChannelInterceptor csrfChannelInterceptor() {
    return new ChannelInterceptor() {};
  }
}
