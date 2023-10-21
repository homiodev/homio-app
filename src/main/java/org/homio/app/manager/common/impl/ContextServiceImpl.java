package org.homio.app.manager.common.impl;

import static org.homio.app.config.WebSocketConfig.CUSTOM_WEB_SOCKET_ENDPOINT;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.homio.api.ContextService;
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

    public ContextServiceImpl(ContextImpl context) {
        this.context = context;
        context.bgp().executeOnExit(() ->
            entityToService.values().forEach(serviceInstance -> {
                try {
                    serviceInstance.destroy();
                } catch (Exception ignore) {
                }
            }));
    }

    @Override
    public void registerEntityTypeForSelection(@NotNull Class<? extends HasEntityIdentifier> entityClass, @NotNull String type) {
        if (entitySelectMap.containsKey(type)) {
            throw new IllegalArgumentException("Entity type: '" + type + "' already registered");
        }
        entitySelectMap.put(type, entityClass);
    }

    @Override
    public void registerUserRoleResource(String resource) {
        UserBaseEntity.registerResource(resource);
    }

    @Override
    public ServiceInstance getEntityService(String entityID) {
        return entityToService.get(entityID);
    }

    @Override
    public void addEntityService(String entityID, ServiceInstance service) {
        entityToService.put(entityID, service);
        context.bgp().getWatchdogBgpService().addWatchDogService(entityID, service);
    }

    @Override
    public ServiceInstance removeEntityService(String entityID) {
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
}
