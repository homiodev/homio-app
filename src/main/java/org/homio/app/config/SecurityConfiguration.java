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
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.cache.SpringCacheBasedUserCache;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

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
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
       // http.antMatcher("/rest/frame/**").headers().frameOptions().deny();

        // No session will be created or used by spring security
        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);

        // Entry points
        if (appProperties.isDisableSecurity()) {
            log.warn("\n-----------------------------------"
                + "\n!!! TouchHome security disabled !!!"
                + "\n-----------------------------------");
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
        authProvider.setUserCache(new SpringCacheBasedUserCache(new ConcurrentMapCache("auth-users")));
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
