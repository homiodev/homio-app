package org.homio.app.config;

import jakarta.servlet.DispatcherType;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
    http.sessionManagement(
        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

    // Entry points
    if (env.getProperty("security-disable", Boolean.class, false)) {
      log.warn(
          """
        -----------------------------------
        !!! HOMIO security disabled !!!
        -----------------------------------
        """);
      http.authorizeHttpRequests(authorize -> authorize.requestMatchers("/rest/**").permitAll());
    } else {
      http.authorizeHttpRequests(
          authorize -> {
            List<String> paths =
                new ArrayList<>(
                    List.of(
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/rest/i18n/**",
                        "/rest/auth/status",
                        "/rest/auth/login",
                        "/rest/auth/register",
                        "/rest/frame/**",
                        "rest/resource/**",
                        "/rest/media/stream/**", // requires for video widget
                        "/rest/workspace/http/**",
                        // "/rest/media/image/**",
                        // "/rest/media/video/**",
                        "/rest/media/video/playback/**",
                        "/rest/route/proxy/**",
                        "/rest/device/characteristic/**"));
            // to avoid issue with ResponseEntity<StreamingResponseBody>
            authorize
                .dispatcherTypeMatchers(
                    DispatcherType.ERROR, DispatcherType.FORWARD, DispatcherType.ASYNC)
                .permitAll();
            authorize
                .requestMatchers(HttpMethod.GET, "/rest/ota")
                .permitAll()
                .requestMatchers(HttpMethod.OPTIONS)
                .permitAll()
                .requestMatchers(paths.toArray(new String[0]))
                .permitAll()
                .requestMatchers("/rest/**")
                .authenticated();
          });
      http.cors(cors -> cors.configurationSource(corsConfigurationSource()));
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
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(List.of("http://localhost:*", "https://homio.org"));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  public AuthenticationManager authManager(HttpSecurity http) throws Exception {
    val authenticationManagerBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
    authenticationManagerBuilder.authenticationProvider(authenticationProvider());
    return authenticationManagerBuilder.build();
  }

  @Bean
  public DaoAuthenticationProvider authenticationProvider() {
    CacheAuthenticationProvider authProvider =
        new CacheAuthenticationProvider(userEntityDetailsService);
    authProvider.setPasswordEncoder(passwordEncoder);
    return authProvider;
  }
}
