package org.touchhome.app.config;

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
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.touchhome.app.auth.JwtTokenProvider;

@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketAuthenticationSecurityConfig implements WebSocketMessageBrokerConfigurer {

  @Override
  public void configureClientInboundChannel(final ChannelRegistration registration) {
    registration.interceptors(this.authChannelInterceptorAdapter(null));
  }

  @Bean
  public ChannelInterceptor authChannelInterceptorAdapter(JwtTokenProvider jwtTokenProvider) {
    return new ChannelInterceptor() {
      @Override
      public Message<?> preSend(Message<?> message, MessageChannel channel) {
        final StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (StompCommand.CONNECT == (accessor != null ? accessor.getCommand() : null)) {
          String token = jwtTokenProvider.resolveToken(accessor.getFirstNativeHeader("Authorization"));
          if (token != null && jwtTokenProvider.validateToken(token)) {
            accessor.setUser(jwtTokenProvider.getAuthentication(token));
          }
        }
        return message;
      }
    };
  }
}
