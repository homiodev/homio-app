package org.homio.app.manager.common.impl;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.homio.addon.mqtt.entity.MQTTBaseEntity;
import org.homio.api.EntityContextService;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.app.manager.common.EntityContextImpl;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.server.support.WebSocketHandlerMapping;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;

import java.util.*;

import static org.homio.app.config.WebSocketConfig.CUSTOM_WEB_SOCKET_ENDPOINT;

@Log4j2
public class EntityContextServiceImpl implements EntityContextService {

    public static final Map<String, Class<? extends HasEntityIdentifier>> entitySelectMap = new HashMap<>();

    static {
        entitySelectMap.put(EntityContextService.MQTT_SERVICE, MQTTBaseEntity.class);
    }

    @Getter
    private final EntityContextImpl entityContext;

    public EntityContextServiceImpl(EntityContextImpl entityContext) {
        this.entityContext = entityContext;
    }

    @Override
    public void registerEntityTypeForSelection(@NotNull Class<? extends HasEntityIdentifier> entityClass, @NotNull String type) {
        if (entitySelectMap.containsKey(type)) {
            throw new IllegalArgumentException("Entity type: '" + type + "' already registered");
        }
        entitySelectMap.put(type, entityClass);
    }

    private static final Set<String> WS_HANDLERS = new HashSet<>();

    @SneakyThrows
    public void registerWebSocketEndpoint(String path, DynamicWebSocketHandler webSocketHandler) {
        if (!path.startsWith(CUSTOM_WEB_SOCKET_ENDPOINT)) {
            throw new IllegalArgumentException("Custom ws path must starts with '/cws'");
        }
        if (WS_HANDLERS.add(path)) {
            WebSocketHandlerMapping webSocketHandlerMapping = entityContext.getBean("webSocketHandlerMapping", WebSocketHandlerMapping.class);
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
