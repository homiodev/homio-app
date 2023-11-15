package org.homio.app.manager.common.impl;

import static org.homio.api.util.Constants.PRIMARY_DEVICE;
import static org.homio.app.config.WebSocketConfig.CUSTOM_WEB_SOCKET_ENDPOINT;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.homio.api.ContextService;
import org.homio.api.ContextService.RouteProxyBuilder.ProxyUrl;
import org.homio.api.entity.device.DeviceBaseEntity;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.service.EntityService.ServiceInstance;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.user.UserBaseEntity;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.server.support.WebSocketHandlerMapping;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;

@Log4j2
public class ContextServiceImpl implements ContextService {

    public static final Map<String, Class<? extends HasEntityIdentifier>> entitySelectMap = new ConcurrentHashMap<>();
    private static final Map<String, ServiceInstance> entityToService = new ConcurrentHashMap<>();
    private final @Getter @Accessors(fluent = true) ContextImpl context;
    private final @Getter Map<String, RouteProxyImpl> proxy = new ConcurrentHashMap<>();

    public ContextServiceImpl(ContextImpl context) {
        this.context = context;
        context.bgp().executeOnExit(() ->
            entityToService.values().forEach(serviceInstance -> {
                try {
                    serviceInstance.destroy(false, null);
                } catch (Exception ignore) {
                }
            }));
    }

    @Override
    public @NotNull String getPrimaryMqttEntity() {
        return DeviceBaseEntity.PREFIX + "mqtt_" + PRIMARY_DEVICE;
    }

    @Override
    public void registerEntityTypeForSelection(@NotNull Class<? extends HasEntityIdentifier> entityClass, @NotNull String type) {
        if (entitySelectMap.containsKey(type)) {
            throw new IllegalArgumentException("Entity type: '" + type + "' already registered");
        }
        entitySelectMap.put(type, entityClass);
    }

    @Override
    public void registerUserRoleResource(@NotNull String resource) {
        UserBaseEntity.registerResource(resource);
    }

    @Override
    public boolean unRegisterUrlProxy(@NotNull String entityID) {
        return proxy.remove(entityID) != null;
    }

    @Override
    public @NotNull String registerUrlProxy(@NotNull String entityID, @NotNull String url, @NotNull Consumer<RouteProxyBuilder> builder) {
        RouteProxyImpl routeProxy = new RouteProxyImpl(entityID, url);
        builder.accept(new RouteProxyBuilder() {

            @Override
            public void setUrlProducer(Function<HttpServletRequest, ProxyUrl> urlBuilder) {
                routeProxy.urlBuilder = urlBuilder;
            }

            @Override
            public void setResponseHeaders(Function<ProxyUrl, Map<String, String>> responseHeaderBuilder) {
                routeProxy.responseHeaderBuilder = responseHeaderBuilder;
            }
        });
        proxy.put(entityID, routeProxy);
        return "$DEVICE_URL/rest/route/proxy/" + entityID + "/proxy_index.html";
    }
    @Override
    public ServiceInstance getEntityService(@NotNull String entityID) {
        return entityToService.get(entityID);
    }

    @Override
    public void addEntityService(@NotNull String entityID, @NotNull ServiceInstance service) {
        entityToService.put(entityID, service);
        context.bgp().getWatchdogBgpService().addWatchDogService(entityID, service);
    }

    @Override
    public ServiceInstance removeEntityService(@NotNull String entityID) {
        context.bgp().getWatchdogBgpService().removeWatchDogService(entityID);
        return entityToService.remove(entityID);
    }

    private static final Set<String> WS_HANDLERS = new HashSet<>();

    @SneakyThrows
    public void registerWebSocketEndpoint(String path, DynamicWebSocketHandler webSocketHandler) {
        if (!path.startsWith(CUSTOM_WEB_SOCKET_ENDPOINT)) {
            throw new IllegalArgumentException("Custom ws path must starts with '/cws'");
        }
        if (WS_HANDLERS.add(path)) {
            WebSocketHandlerMapping webSocketHandlerMapping = context.getBean("webSocketHandlerMapping", WebSocketHandlerMapping.class);
            WebSocketHttpRequestHandler httpHandler = new WebSocketHttpRequestHandler(webSocketHandler, new DefaultHandshakeHandler());
            httpHandler.setHandshakeInterceptors(List.of(webSocketHandler));
            MethodUtils.invokeMethod(webSocketHandlerMapping, true, "registerHandler", path, httpHandler);
        }
    }

    public interface DynamicWebSocketHandler extends WebSocketHandler, HandshakeInterceptor {

        @Override
        default boolean beforeHandshake(@NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response, @NotNull WebSocketHandler wsHandler,
                                        @NotNull Map<String, Object> attributes) {
            return true;
        }

        @Override
        default void afterHandshake(@NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response, @NotNull WebSocketHandler wsHandler,
                                    Exception exception) {
        }
    }

    @Getter
    @RequiredArgsConstructor
    public static class RouteProxyImpl {

        private final @NotNull String entityID;
        private final @NotNull String url;
        private Function<HttpServletRequest, ProxyUrl> urlBuilder;
        private Function<ProxyUrl, Map<String, String>> responseHeaderBuilder;

        public @NotNull ProxyUrl buildUrl(HttpServletRequest request) {
            String subRequest = request.getRequestURI().substring(("/rest/route/proxy/" + entityID).length());
            if (subRequest.equals("/proxy_index.html")) {
                subRequest = "";
            }
            if (urlBuilder != null) {
                return urlBuilder.apply(request);
            }
            return new ProxyUrl(url + subRequest, null);
        }

        public Map<String, String> applyResponseHeaders(ProxyUrl proxyUrl) {
            if (responseHeaderBuilder != null) {
                return responseHeaderBuilder.apply(proxyUrl);
            }
            return null;
        }
    }
}
