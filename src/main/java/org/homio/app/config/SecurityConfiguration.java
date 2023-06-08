package org.homio.app.config;

import lombok.AllArgsConstructor;
import lombok.val;
import org.apache.catalina.filters.RequestFilter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.homio.app.auth.CacheAuthenticationProvider;
import org.homio.app.auth.JwtTokenFilterConfigurer;
import org.homio.app.auth.JwtTokenProvider;
import org.homio.app.auth.UserEntityDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

@Configuration
@EnableWebSecurity
@AllArgsConstructor
public class SecurityConfiguration {

    private final PasswordEncoder passwordEncoder;
    private final UserEntityDetailsService userEntityDetailsService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AppProperties appProperties;
    private final Log log = LogFactory.getLog(RequestFilter.class);

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
        if (appProperties.isDisableSecurity()) {
            log.warn("\n-----------------------------------"
                + "\n!!! TouchHome security disabled !!!"
                + "\n-----------------------------------");
            http.authorizeHttpRequests(authorize ->
                authorize.requestMatchers(WebSocketConfig.ENDPOINT, "/rest/**").permitAll());
        } else {
            http.authorizeHttpRequests(authorize -> {
                authorize.requestMatchers(
                    WebSocketConfig.ENDPOINT,
                    "/rest/test",
                    "/rest/frame/**",
                    "/rest/media/audio/**/play",
                    "/rest/media/video/**/play",
                    "/rest/media/video/playback/**/download",
                    "/rest/media/video/playback/**/thumbnail/**",
                    "/rest/auth/status",
                    "/rest/auth/login",
                    "/rest/addon/image/**",
                    "/rest/media/image/**",
                    "/rest/device/**").permitAll();
                authorize.requestMatchers("/rest/**").authenticated();
            });
            http.csrf(AbstractHttpConfigurer::disable);
            http.headers(headers -> headers.frameOptions(FrameOptionsConfig::disable));
        }

        // If a user try to access a resource without having enough permissions
        http.exceptionHandling(exception -> exception.accessDeniedPage("/login"));

        // Apply JWT
        http.apply(new JwtTokenFilterConfigurer(jwtTokenProvider));
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
