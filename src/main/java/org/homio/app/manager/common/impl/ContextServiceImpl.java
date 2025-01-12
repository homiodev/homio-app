package org.homio.app.manager.common.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.homio.api.Context;
import org.homio.api.ContextService;
import org.homio.api.ContextService.RouteProxyBuilder.ProxyUrl;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.device.DeviceBaseEntity;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.model.OptionModel;
import org.homio.api.service.BaseService;
import org.homio.api.service.EntityService;
import org.homio.api.service.EntityService.ServiceInstance;
import org.homio.app.manager.bgp.WatchdogBgpService;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.user.UserBaseEntity;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.server.support.WebSocketHandlerMapping;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.homio.api.util.Constants.PRIMARY_DEVICE;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;
import static org.homio.app.config.WebSocketConfig.CUSTOM_WEB_SOCKET_ENDPOINT;

@Log4j2
public class ContextServiceImpl implements ContextService {

  public static final Map<String, Class<? extends HasEntityIdentifier>> entitySelectMap = new ConcurrentHashMap<>();
  private static final Map<String, BaseService> entityToService = new ConcurrentHashMap<>();
  private static final Set<String> WS_HANDLERS = new HashSet<>();
  private final @Getter
  @Accessors(fluent = true) ContextImpl context;
  private final @Getter Map<String, RouteProxyImpl> proxy = new ConcurrentHashMap<>();

  public ContextServiceImpl(ContextImpl context) {
    this.context = context;
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
    return (ServiceInstance) entityToService.get(entityID);
  }

  @Override
  public void addService(@NotNull String entityID, @NotNull BaseService service) {
    entityToService.put(entityID, service);
    if (service instanceof EntityService.WatchdogService wds) {
      context.bgp().getWatchdogBgpService().addWatchDogService(entityID, wds);
    }
  }

  @Override
  public BaseService removeService(@NotNull String entityID) {
    context.bgp().getWatchdogBgpService().removeWatchDogService(entityID);
    return entityToService.remove(entityID);
  }

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

  public List<OptionModel> getServices() {
    Map<String, List<BaseService>> servicesByName = new HashMap<>();
    for (BaseService service : entityToService.values()) {
      if (service.isExposeService() && service.getName() != null) {
        String name = StringUtils.defaultIfEmpty(service.getParent(), service.getName());
        servicesByName.computeIfAbsent(name, s -> new ArrayList<>())
          .add(service);
      }
    }
    List<OptionModel> list = new ArrayList<>();
    for (Map.Entry<String, List<BaseService>> entry : servicesByName.entrySet()) {
      if (entry.getValue().size() == 1) {
        list.add(buildOptionModel(entry.getValue().get(0)));
      } else {
        OptionModel parent = OptionModel.of(entry.getKey());
        list.add(parent);
        for (BaseService service : entry.getValue()) {
          parent.addChild(buildOptionModel(service));
        }
      }
    }

    return list;
  }

  private OptionModel buildOptionModel(BaseService service) {
    OptionModel optionModel = OptionModel.of(service.getEntityID(), service.getName());
    optionModel.setIcon(service.getIcon());
    optionModel.setColor(service.getColor());
    return optionModel;
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
  public static class RouteProxyImpl {

    private final @NotNull String entityID;
    private final @NotNull String url;
    private Function<HttpServletRequest, ProxyUrl> urlBuilder;
    private Function<ProxyUrl, Map<String, String>> responseHeaderBuilder;

    public RouteProxyImpl(@NotNull String entityID, @NotNull String url) {
      this.entityID = entityID;
      this.url = url;
    }

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
