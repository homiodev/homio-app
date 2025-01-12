package org.homio.app.config;

import jakarta.servlet.DispatcherType;
import lombok.AllArgsConstructor;
import lombok.val;
import org.apache.catalina.filters.RequestFilter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.homio.app.auth.AccessFilter;
import org.homio.app.auth.CacheAuthenticationProvider;
import org.homio.app.auth.JwtTokenProvider;
import org.homio.app.auth.UserEntityDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import java.util.ArrayList;
import java.util.List;

import static org.homio.app.config.WebSocketConfig.CUSTOM_WEB_SOCKET_ENDPOINT;
import static org.homio.app.config.WebSocketConfig.WEB_SOCKET_ENDPOINT;

@Configuration
@EnableWebSecurity
@AllArgsConstructor
public class SecurityConfiguration {

  private final PasswordEncoder passwordEncoder;
  private final UserEntityDetailsService userEntityDetailsService;
  private final JwtTokenProvider jwtTokenProvider;
  private final Log log = LogFactory.getLog(RequestFilter.class);
  private final Environment env;

  @Bean
  public MethodValidationPostProcessor methodValidationPostProcessor() {
    return new MethodValidationPostProcessor();
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    // http.antMatcher("/rest/frame/**").headers().frameOptions().deny();
    // http.csrf().csrfTokenRepository(csrfTokenRepository()).;

    // No session will be created or used by spring security
    http.sessionManagement(session ->
      session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

    // Entry points
    if (env.getProperty("security-disable", Boolean.class, false)) {
      log.warn("""
        -----------------------------------
        !!! HOMIO security disabled !!!
        -----------------------------------
        """);
      http.authorizeHttpRequests(authorize ->
        authorize.requestMatchers(WEB_SOCKET_ENDPOINT, CUSTOM_WEB_SOCKET_ENDPOINT + "/**", "/rest/**").permitAll());
    } else {
      http.authorizeHttpRequests(authorize -> {
        List<String> paths = new ArrayList<>(List.of(
          WEB_SOCKET_ENDPOINT,
          CUSTOM_WEB_SOCKET_ENDPOINT + "/**",
          "/swagger-ui/**",
          "/v3/api-docs/**",
          "/rest/i18n/**",
          "/rest/auth/status",
          "/rest/auth/login",
          "/rest/auth/register",
          "/rest/frame/**",
          "/rest/access/get/**",
          "rest/resource/**",
          "/rest/media/stream/**",
          "/rest/media/image/**",
          "/rest/media/video/**",
          "/rest/media/video/playback/**",
          "/rest/addon/image/**",
          "/rest/route/proxy/**",
          "/rest/device/**"
        ));
        // to avoid issue with ResponseEntity<StreamingResponseBody>
        authorize.dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD, DispatcherType.ASYNC).permitAll();
        authorize.requestMatchers(paths.toArray(new String[0])).permitAll();
        // allow preflight requests
        authorize.requestMatchers(HttpMethod.OPTIONS).permitAll();
        authorize.requestMatchers("/rest/**").authenticated();
      });
      http.csrf(AbstractHttpConfigurer::disable);
      http.headers(headers -> headers.frameOptions(FrameOptionsConfig::disable));
    }

    // If a user try to access a resource without having enough permissions
    http.exceptionHandling(exception -> exception.accessDeniedPage("/login"));

    // Apply JWT
    AccessFilter customFilter = new AccessFilter(jwtTokenProvider);

    http.addFilterBefore(customFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  public AuthenticationManager authManager(HttpSecurity http) throws Exception {
    val authenticationManagerBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
    authenticationManagerBuilder.authenticationProvider(authenticationProvider());
    return authenticationManagerBuilder.build();
  }

  @Bean
  public DaoAuthenticationProvider authenticationProvider() {
    CacheAuthenticationProvider authProvider = new CacheAuthenticationProvider();
    authProvider.setUserDetailsService(userEntityDetailsService);
    authProvider.setPasswordEncoder(passwordEncoder);
    return authProvider;
  }
}
