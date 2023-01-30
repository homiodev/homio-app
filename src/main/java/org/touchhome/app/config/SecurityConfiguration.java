package org.touchhome.app.config;

import lombok.AllArgsConstructor;
import lombok.val;
import org.apache.catalina.filters.RequestFilter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.touchhome.app.auth.DoubleCheckPasswordAuthenticationProvider;
import org.touchhome.app.auth.JwtTokenFilterConfigurer;
import org.touchhome.app.auth.JwtTokenProvider;
import org.touchhome.app.auth.UserEntityDetailsService;
import org.touchhome.app.repository.UserRepository;

@Configuration
@EnableWebSecurity
@AllArgsConstructor
public class SecurityConfiguration {

    private final PasswordEncoder passwordEncoder;
    private final UserEntityDetailsService userEntityDetailsService;
    private final JwtTokenProvider jwtTokenProvider;
    private final TouchHomeProperties touchHomeProperties;
    private final Log log = LogFactory.getLog(RequestFilter.class);

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
       // http.antMatcher("/rest/frame/**").headers().frameOptions().deny();

        // No session will be created or used by spring security
        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);

        // Entry points
        if (touchHomeProperties.isDisableSecurity()) {
            log.warn("!!! TouchHome security disabled !!!");
            http.authorizeRequests().antMatchers(WebSocketConfig.ENDPOINT, "/rest/**").permitAll();
        } else {
            http.authorizeRequests()//
                .antMatchers(
                    WebSocketConfig.ENDPOINT,
                    "/rest/frame/**",
                    "/rest/media/audio/**/play",
                    "/rest/media/video/**/play",
                    "/rest/media/video/playback/**/download",
                    "/rest/media/video/playback/**/thumbnail/**",
                    "/rest/auth/status",
                    "/rest/auth/login",
                    "/rest/bundle/image/**",
                    "/rest/device/**").permitAll()
                .antMatchers("/rest/**").authenticated()
                .and()
                .csrf().disable()
                .headers().frameOptions().disable();
        }

        // If a user try to access a resource without having enough permissions
        http.exceptionHandling().accessDeniedPage("/login");

        // Apply JWT
        http.apply(new JwtTokenFilterConfigurer(jwtTokenProvider));
        return http.build();
    }

    /*@Override
    @Bean(name = "authenticationManager")
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }*/

    @Bean
    public AuthenticationManager authManager(HttpSecurity http) throws Exception {
        val authenticationManagerBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        authenticationManagerBuilder.authenticationProvider(authenticationProvider(null));
        return authenticationManagerBuilder.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(UserRepository userRepository) {
        DoubleCheckPasswordAuthenticationProvider authProvider = new DoubleCheckPasswordAuthenticationProvider(userRepository);
        authProvider.setUserDetailsService(userEntityDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

   /* @Override
    protected void configure(AuthenticationManagerBuilder auth) {
        auth.authenticationProvider(authenticationProvider(null));
    }*/

    /*@Bean
    public FilterRegistrationBean remoteAddressFilter() {
        FilterRegistrationBean<RequestFilter> filterRegistrationBean = new FilterRegistrationBean<>();
        RequestFilter filter = new RequestFilter() {

            @Override
            protected Log getLogger() {
                return log;
            }

            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
            ServletException {
                if (!"https".equals(request.getScheme())) {
                    process(request.getRemoteAddr(), request, response, chain);
                } else {
                    chain.doFilter(request, response);
                }
            }
        };

        filter.setAllow("0:0:0:0:0:0:0:1");
        filter.setDenyStatus(HttpStatus.FORBIDDEN.value());

        filterRegistrationBean.setFilter(filter);
        filterRegistrationBean.addUrlPatterns("/*");

        return filterRegistrationBean;

    }*/
}
