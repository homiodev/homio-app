package org.homio.app.config;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.hibernate5.jakarta.Hibernate5JakartaModule;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.extern.log4j.Log4j2;
import org.homio.api.Context;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.device.DeviceBaseEntity;
import org.homio.api.util.SecureString;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.homio.app.config.cacheControl.CacheControlHandlerInterceptor;
import org.homio.app.json.jsog.JSOGGenerator;
import org.homio.app.json.jsog.JSOGResolver;
import org.homio.app.manager.CacheService;
import org.homio.app.manager.common.ClassFinder;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.widget.WidgetEntity;
import org.homio.app.workspace.block.Scratch3Space;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryBuilderCustomizer;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.gateway.mvc.config.ProxyExchangeArgumentResolver;
import org.springframework.cloud.gateway.mvc.config.ProxyProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.orm.jpa.persistenceunit.PersistenceUnitManager;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Log4j2
@Configuration
@EnableCaching
@EnableScheduling
@ComponentScan({"org.homio"})
@Import(value = {WebSocketConfig.class})
@EnableConfigurationProperties({JpaProperties.class, HibernateProperties.class})
public class AppConfig implements WebMvcConfigurer, SchedulingConfigurer, ApplicationListener {

    @Autowired
    private ApplicationContext applicationContext;

    private boolean applicationReady;

    @Bean
    public EntityManagerFactoryBuilder entityManagerFactoryBuilder(
            JpaProperties jpaProperties,
            ObjectProvider<PersistenceUnitManager> persistenceUnitManager,
            ObjectProvider<EntityManagerFactoryBuilderCustomizer> customizers) {
        HibernateJpaVendorAdapter jpaVendorAdapter = new HibernateJpaVendorAdapter();
        EntityManagerFactoryBuilder builder = new EntityManagerFactoryBuilder(jpaVendorAdapter,
                jpaProperties.getProperties(), persistenceUnitManager.getIfAvailable());
        customizers.orderedStream().forEach((customizer) -> customizer.customize(builder));
        return builder;
    }

    @Bean
    public ThreadPoolTaskScheduler threadPoolTaskScheduler() {
        ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
        threadPoolTaskScheduler.setPoolSize(100);
        threadPoolTaskScheduler.setThreadNamePrefix("th-async-");
        threadPoolTaskScheduler.setRemoveOnCancelPolicy(true);
        return threadPoolTaskScheduler;
    }

    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(threadPoolTaskScheduler());
    }

    @Bean
    public WebMvcRegistrations mvcRegistrations() {
        return new WebMvcRegistrations() {
            private final ExtRequestMappingHandlerMapping handlerMapping = new ExtRequestMappingHandlerMapping();

            @Override
            public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
                return handlerMapping;
            }
        };
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**");
    }

    // not too safe for now
    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(false);
        config.addAllowedOrigin("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    @Bean
    public List<WidgetEntity> widgetBaseEntities(ClassFinder classFinder) {
        return ClassFinder.createClassesWithParent(WidgetEntity.class, classFinder);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(5);
    }

    @Bean
    public CacheManager cacheManager() {
        return CacheService.createCacheManager();
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(Executors.newScheduledThreadPool(4));
    }

    @Bean
    public ObjectMapper objectMapper() {
        Hibernate5JakartaModule hibernate5Module = new Hibernate5JakartaModule();
        hibernate5Module.disable(Hibernate5JakartaModule.Feature.USE_TRANSIENT_ANNOTATION);

        SimpleModule simpleModule = new SimpleModule();
        simpleModule.addSerializer(String.class, new StringComplexSerializer());
        simpleModule.addSerializer(SecureString.class, new JsonSerializer<>() {
            @Override
            public void serialize(SecureString secureString, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
                    throws IOException {
                jsonGenerator.writeString(secureString.toString());
            }
        });

        simpleModule.addSerializer(Scratch3ExtensionBlocks.class, new JsonSerializer<>() {
            @Override
            public void serialize(Scratch3ExtensionBlocks block, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                gen.writeStartObject();
                gen.writeStringField("id", block.getId());
                if (block.getName() != null) {
                    gen.writeStringField("name", block.getName());
                }
                gen.writeStringField("blockIconURI", block.getBlockIconURI());
                gen.writeStringField("color1", block.getScratch3Color().getColor1());
                gen.writeStringField("color2", block.getScratch3Color().getColor2());
                gen.writeStringField("color3", block.getScratch3Color().getColor3());
                gen.writeObjectField("blocks", block.getBlocks());
                gen.writeObjectField("menus", block.getMenus());
                gen.writeEndObject();
            }
        });

        simpleModule.addSerializer(Scratch3Space.class, new JsonSerializer<>() {
            @Override
            public void serialize(Scratch3Space extension, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString("---");
            }
        });

        simpleModule.addDeserializer(DeviceBaseEntity.class, new JsonDeserializer<>() {
            @Override
            public DeviceBaseEntity deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                return applicationContext.getBean(ContextImpl.class).db().getEntity(p.getText());
            }
        });

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper
                .disable(DeserializationFeature.FAIL_ON_UNRESOLVED_OBJECT_IDS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .registerModule(hibernate5Module)
                .registerModule(new JsonOrgModule())
                .registerModule(simpleModule)
                .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
                .addMixIn(BaseEntity.class, Bean2MixIn.class);

        return objectMapper;
    }

    // Add converters for convert String to desired class in rest controllers
    @Override
    public void addFormatters(final FormatterRegistry registry) {
        registry.addConverter(String.class, DeviceBaseEntity.class, source ->
            applicationContext.getBean(Context.class).db().getEntity(source));
    }

    @Bean
    public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setPrefixJson(false);
        converter.setSupportedMediaTypes(Collections.singletonList(MediaType.APPLICATION_JSON));
        converter.setObjectMapper(objectMapper());
        return converter;
    }

    /**
     * After spring context initialization
     */
    @Override
    public void onApplicationEvent(@NotNull ApplicationEvent event) {
        if (event instanceof ContextRefreshedEvent cre && !this.applicationReady) {
            this.applicationReady = true;
            this.printEnvVariables(cre.getApplicationContext().getEnvironment());
            ApplicationContext applicationContext = ((ContextRefreshedEvent) event).getApplicationContext();
            ContextImpl contextImpl = applicationContext.getBean(ContextImpl.class);
            contextImpl.afterContextStart(applicationContext);
        }
    }

    private void printEnvVariables(Environment env) {
        final MutablePropertySources sources = ((AbstractEnvironment) env).getPropertySources();
        StringBuilder props = new StringBuilder();
        props.append("\n\tActive profiles: %s\n".formatted(Arrays.toString(env.getActiveProfiles())));
        Map<String, String> variables = StreamSupport
                .stream(sources.spliterator(), false)
                .filter(ps -> ps instanceof EnumerablePropertySource)
                .map(ps -> ((EnumerablePropertySource) ps).getPropertyNames())
                .flatMap(Arrays::stream)
                .distinct()
                .filter(prop -> !(prop.contains("java.class.path") || prop.contains("Path") || prop.contains("java.library.path") || prop.contains("credentials")
                        || prop.contains("password")))
                .collect(Collectors.toMap(prop -> prop, key -> env.getProperty(key, "---"),
                        (v1, v2) -> {
                            throw new RuntimeException(String.format("Duplicate key for values %s and %s", v1, v2));
                        },
                        TreeMap::new));
        for (Entry<String, String> entry : variables.entrySet()) {
            props.append("\t\t%s: %s\n".formatted(entry.getKey(), entry.getValue()));
        }
        props.append("\n===========================================");
        log.info("\n====== Environment and configuration ======{}", props.toString());
    }

    /**
     * Force flush cache on request
     */
    @Bean
    public FilterRegistrationBean<Filter> saveDelayFilter() {
        FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain filterChain)
                    throws IOException, ServletException {
                applicationContext.getBean(CacheService.class).flushDelayedUpdates();
                filterChain.doFilter(request, response);
            }
        });
        registrationBean.addUrlPatterns("/map", "/dashboard", "/items/*", "/media/*", "/hardware*/", "/devices/*");

        return registrationBean;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new CacheControlHandlerInterceptor());
    }

    @JsonIdentityInfo(generator = JSOGGenerator.class, property = "entityID", resolver = JSOGResolver.class)
    interface Bean2MixIn {

    }

    @Bean
    public ProxyExchangeArgumentResolver proxyExchangeArgumentResolver(Optional<RestTemplateBuilder> optional,
        ProxyProperties proxy) {
        RestTemplateBuilder builder = optional.orElse(new RestTemplateBuilder());
        RestTemplate template = builder.build();
        template.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public void handleError(ClientHttpResponse response) throws IOException {
            }
        });
        template.getMessageConverters().add(new ByteArrayHttpMessageConverter() {
            @Override
            public boolean supports(Class<?> clazz) {
                return true;
            }

            @Override
            public byte[] readInternal(Class<? extends byte[]> clazz, HttpInputMessage message) throws IOException {
                // avoid read content-length. sometime it's not match!
                return message.getBody().readAllBytes();
            }
        });
        ProxyExchangeArgumentResolver resolver = new ProxyExchangeArgumentResolver(template);
        resolver.setHeaders(proxy.convertHeaders());
        resolver.setAutoForwardedHeaders(proxy.getAutoForward());
        resolver.setSensitive(proxy.getSensitive()); // can be null
        return resolver;
    }
}
