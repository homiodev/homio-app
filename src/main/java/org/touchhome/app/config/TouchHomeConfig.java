package org.touchhome.app.config;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;
import com.pi4j.io.gpio.Pin;
import lombok.extern.log4j.Log4j2;
import net.rossillo.spring.web.mvc.CacheControlHandlerInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.touchhome.app.json.jsog.JSOGGenerator;
import org.touchhome.app.json.jsog.JSOGResolver;
import org.touchhome.app.manager.CacheService;
import org.touchhome.app.manager.common.ClassFinder;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.repository.crud.base.CrudRepositoryFactoryBean;
import org.touchhome.app.utils.HardwareUtils;
import org.touchhome.app.workspace.block.Scratch3Space;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.DeviceBaseEntity;
import org.touchhome.bundle.api.entity.widget.WidgetBaseEntity;
import org.touchhome.bundle.api.hquery.HardwareRepositoryFactoryPostHandler;
import org.touchhome.bundle.api.hquery.HardwareRepositoryFactoryPostProcessor;
import org.touchhome.bundle.api.util.ApplicationContextHolder;
import org.touchhome.bundle.api.util.SecureString;
import org.touchhome.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

@Log4j2
@Configuration
@EnableCaching
@EnableScheduling
@EntityScan(basePackages = {"org.touchhome"})
@ComponentScan({"org.touchhome"})
@Import(value = {WebSocketConfig.class})
@EnableJpaRepositories(basePackages = "org.touchhome.app.repository.crud", repositoryFactoryBeanClass = CrudRepositoryFactoryBean.class)
@EnableTransactionManagement(proxyTargetClass = true)
public class TouchHomeConfig implements WebMvcConfigurer, SchedulingConfigurer, ApplicationListener {

    @Autowired
    private ApplicationContext applicationContext;

    private boolean applicationReady;

    @Bean
    public ThreadPoolTaskScheduler threadPoolTaskScheduler() {
        ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
        threadPoolTaskScheduler.setPoolSize(100);
        threadPoolTaskScheduler.setThreadNamePrefix("th-async-");
        threadPoolTaskScheduler.setRemoveOnCancelPolicy(true);
        return threadPoolTaskScheduler;
    }

    @Bean
    public HardwareRepositoryFactoryPostProcessor.HardwareRepositoryThreadPool hardwareRepositoryThreadPool(ThreadPoolTaskScheduler scheduler) {
        return (name, runnable) -> scheduler.submit(runnable);
    }

    @Bean
    public WebMvcRegistrations mvcRegistrations() {
        return new WebMvcRegistrations() {
            private ExtRequestMappingHandlerMapping handlerMapping = new ExtRequestMappingHandlerMapping();

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
    public FilterRegistrationBean corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(false);
        config.addAllowedOrigin("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        source.registerCorsConfiguration("/**", config);
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @Bean
    public List<WidgetBaseEntity> widgetBaseEntities(ClassFinder classFinder) {
        return ClassFinder.createClassesWithParent(WidgetBaseEntity.class, classFinder);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(5);
    }

    @Bean
    public CommonsMultipartResolver multipartResolver() {
        CommonsMultipartResolver resolver = new CommonsMultipartResolver();
        resolver.setDefaultEncoding("utf-8");
        resolver.setMaxUploadSize(20971520);
        return resolver;
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
        Hibernate5Module hibernate5Module = new Hibernate5Module();
        hibernate5Module.disable(Hibernate5Module.Feature.USE_TRANSIENT_ANNOTATION);

        SimpleModule simpleModule = new SimpleModule();
        simpleModule.addSerializer(SecureString.class, new JsonSerializer<SecureString>() {
            @Override
            public void serialize(SecureString secureString, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                jsonGenerator.writeString(secureString.toString());
            }
        });
        simpleModule.addSerializer(Pin.class, new JsonSerializer<Pin>() {
            @Override
            public void serialize(Pin pin, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                jsonGenerator.writeStartObject();
                jsonGenerator.writeStringField("name", pin.getName());
                jsonGenerator.writeEndObject();
            }
        });

        simpleModule.addSerializer(Scratch3ExtensionBlocks.class, new JsonSerializer<Scratch3ExtensionBlocks>() {
            @Override
            public void serialize(Scratch3ExtensionBlocks block, JsonGenerator gen, SerializerProvider serializers) throws IOException {
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

        simpleModule.addSerializer(Scratch3Space.class, new JsonSerializer<Scratch3Space>() {
            @Override
            public void serialize(Scratch3Space extension, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString("---");
            }
        });

        simpleModule.addDeserializer(DeviceBaseEntity.class, new JsonDeserializer<DeviceBaseEntity>() {
            @Override
            public DeviceBaseEntity deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                return applicationContext.getBean(EntityContextImpl.class).getEntity(p.getText());
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
                applicationContext.getBean(EntityContext.class).getEntity(source));
    }

    @Bean
    public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setPrefixJson(false);
        converter.setSupportedMediaTypes(Collections.singletonList(MediaType.APPLICATION_JSON));
        converter.setObjectMapper(objectMapper());
        return converter;
    }

    @Bean
    public ApplicationContextHolder applicationContextHolder() {
        return new ApplicationContextHolder();
    }

    /**
     * After spring context initialization
     */
    @Override
    public void onApplicationEvent(@NotNull ApplicationEvent event) {
        if (event instanceof ContextRefreshedEvent && !this.applicationReady) {
            this.applicationReady = true;
            ApplicationContext applicationContext = ((ContextRefreshedEvent) event).getApplicationContext();
            EntityContextImpl entityContextImpl = applicationContext.getBean(EntityContextImpl.class);
            entityContextImpl.afterContextStart(applicationContext);
        }
    }

    /**
     * Force flush cache on request
     */
    @Bean
    public FilterRegistrationBean<Filter> saveDelayFilter() {
        FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws IOException, ServletException {
                applicationContext.getBean(CacheService.class).flushDelayedUpdates();
                filterChain.doFilter(request, response);
            }
        });
        registrationBean.addUrlPatterns("/map", "/dashboard", "/items/*", "/hardware*/", "/one_wire/*", "/admin/*");

        return registrationBean;
    }

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.favorPathExtension(false);
    }

    @Bean
    public HardwareRepositoryFactoryPostHandler hardwareRepositoryFactoryPostHandler() {
        return HardwareUtils::prepareHardware;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new CacheControlHandlerInterceptor());
    }

    @JsonIdentityInfo(generator = JSOGGenerator.class, property = "entityID", resolver = JSOGResolver.class)
    interface Bean2MixIn {
    }
}
