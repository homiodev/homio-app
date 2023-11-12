package org.homio.app.rest;

import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.Context;
import org.homio.api.ContextService.RouteProxyBuilder.ProxyUrl;
import org.homio.api.exception.ServerException;
import org.homio.api.ui.UISidebarMenu;
import org.homio.app.manager.AddonService;
import org.homio.app.manager.AddonService.AddonJson;
import org.homio.app.manager.common.ClassFinder;
import org.homio.app.manager.common.impl.ContextServiceImpl;
import org.homio.app.manager.common.impl.ContextServiceImpl.RouteProxyImpl;
import org.homio.app.manager.common.impl.ContextUIImpl;
import org.homio.app.model.entity.SettingEntity;
import org.jetbrains.annotations.NotNull;
import org.springframework.cloud.gateway.mvc.ProxyExchange;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

@RestController
@RequestMapping("/rest/route")
public class RouteController {

    private final Context context;
    private final List<Class<?>> uiSidebarMenuClasses;
    private final AddonService addonService;
    private final SettingController settingController;

    public RouteController(
            ClassFinder classFinder,
            AddonService addonService,
            SettingController settingController,
        Context context) {
        this.uiSidebarMenuClasses = classFinder.getClassesWithAnnotation(UISidebarMenu.class);
        this.addonService = addonService;
        this.settingController = settingController;
        this.context = context;
    }

    @GetMapping("/bootstrap")
    public BootstrapContext getBootstrap(WebRequest webRequest) {
        BootstrapContext bootstrapContext = new BootstrapContext();
        bootstrapContext.routes = getRoutes();
        bootstrapContext.menu = getMenu();
        bootstrapContext.addons = addonService.getAllAddonJson();
        bootstrapContext.settings = settingController.getSettings();
        bootstrapContext.notifications = ((ContextUIImpl) context.ui()).getNotifications();

        String eTag = String.valueOf(bootstrapContext.hashCode());
        if (webRequest.checkNotModified(eTag)) {
            return null;
        }
        return bootstrapContext;
    }

    @GetMapping("/proxy/{entityID}/**")
    public ResponseEntity<?> proxyGet(@PathVariable("entityID") String entityID, HttpServletRequest request, ProxyExchange<byte[]> proxy) {
        return proxyUrl(proxy, request, entityID, ProxyExchange::get);
    }

    @PostMapping("/proxy/{entityID}/**")
    public ResponseEntity<?> proxyPost(@PathVariable("entityID") String entityID, HttpServletRequest request, ProxyExchange<byte[]> proxy) {
        return proxyUrl(proxy, request, entityID, ProxyExchange::post);
    }

    @DeleteMapping("/proxy/{entityID}/**")
    public ResponseEntity<?> proxyDelete(@PathVariable("entityID") String entityID, HttpServletRequest request, ProxyExchange<byte[]> proxy) {
        return proxyUrl(proxy, request, entityID, ProxyExchange::delete);
    }

    @SneakyThrows
    private ResponseEntity<?> proxyUrl(
        @NotNull ProxyExchange<byte[]> proxy,
        @NotNull HttpServletRequest request,
        @NotNull String entityID,
        @NotNull Function<ProxyExchange<byte[]>, ResponseEntity<byte[]>> handler) {
        RouteProxyImpl routeProxy = ((ContextServiceImpl) context.service()).getProxy().get(entityID);
        if (routeProxy == null) {
            throw new ServerException("No proxy found for entity: " + entityID);
        }
        ProxyUrl proxyUrl = routeProxy.buildUrl(request);
        URI uri = new URI(proxyUrl.url());
        uri = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), request.getQueryString(), null);
        ProxyExchange<byte[]> proxyExchange = proxy.uri(uri);
        if (proxyUrl.headers() != null) {
            for (Entry<String, List<String>> header : proxyUrl.headers().entrySet()) {
                proxyExchange.header(header.getKey(), header.getValue().toArray(new String[0]));
            }
        }

        ResponseEntity<byte[]> response = handler.apply(proxyExchange);
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.addAll(response.getHeaders());

        Map<String, String> applyHeader = routeProxy.applyResponseHeaders(proxyUrl);
        if (applyHeader != null) {
            for (Entry<String, String> entry : applyHeader.entrySet()) {
                responseHeaders.add(entry.getKey(), entry.getValue());
            }
        }

        responseHeaders.add(ACCESS_CONTROL_EXPOSE_HEADERS, "*");
        return ResponseEntity.status(response.getStatusCode())
                             .headers(responseHeaders)
                             .body(response.getBody());
    }

    private Map<String, List<SidebarMenuItem>> getMenu() {
        Map<String, List<SidebarMenuItem>> sidebarMenus = new HashMap<>();

        for (Class<?> item : this.uiSidebarMenuClasses) {
            getSubMenu(sidebarMenus, item, item.getDeclaredAnnotation(UISidebarMenu.class));
        }

        for (List<SidebarMenuItem> sidebarMenuItems : sidebarMenus.values()) {
            sidebarMenuItems.sort(Comparator.comparingInt(SidebarMenuItem::order));
        }

        return sidebarMenus;
    }

    private void getSubMenu(
            Map<String, List<SidebarMenuItem>> sidebarMenus,
            Class<?> item,
            UISidebarMenu uiSidebarMenu) {
        String parent = uiSidebarMenu.parent().name().toLowerCase();
        if (!sidebarMenus.containsKey(parent)) {
            sidebarMenus.put(parent, new ArrayList<>());
        }
        sidebarMenus.get(parent).add(SidebarMenuItem.fromAnnotation(item, uiSidebarMenu));
    }

    private List<RouteDTO> getRoutes() {
        List<RouteDTO> routes = new ArrayList<>();
        for (Class<?> aClass : this.uiSidebarMenuClasses) {
            addRouteFromUISideBarMenu(routes, aClass, aClass.getAnnotation(UISidebarMenu.class));
        }

        routes.addAll(Stream.of("dashboard").map(RouteDTO::new).toList());
        return routes;
    }

    private void addRouteFromUISideBarMenu(
            List<RouteDTO> routes, Class<?> aClass, UISidebarMenu uiSidebarMenu) {
        String href = StringUtils.defaultIfEmpty(uiSidebarMenu.overridePath(), aClass.getSimpleName());
        RouteDTO route = new RouteDTO(uiSidebarMenu.parent().name().toLowerCase() + "/" + href);
        route.type = aClass.getSimpleName();
        route.path = href;
        route.allowCreateNewItems = uiSidebarMenu.allowCreateNewItems();
        route.sort = Stream.of(uiSidebarMenu.sort()).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        route.filter = Stream.of(uiSidebarMenu.filter()).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        routes.add(route);
    }

    public static class BootstrapContext {

        public List<RouteDTO> routes;
        public Map<String, List<SidebarMenuItem>> menu;
        public List<AddonJson> addons;
        public List<SettingEntity> settings;
        public ContextUIImpl.NotificationResponse notifications;
    }

    public record SidebarMenuItem(String href, String icon, String bg, String label, int order) {

        static SidebarMenuItem fromAnnotation(Class<?> clazz, UISidebarMenu uiSidebarMenu) {
            return new SidebarMenuItem(
                    StringUtils.defaultIfEmpty(uiSidebarMenu.overridePath(), clazz.getSimpleName()),
                    uiSidebarMenu.icon(),
                    uiSidebarMenu.bg(),
                    clazz.getSimpleName(),
                    uiSidebarMenu.order());
        }
    }

    @Getter
    @RequiredArgsConstructor
    public static class RouteDTO {

        private final String url;
        private List<String> sort;
        private List<String> filter;
        private String path;
        private String type;
        private boolean allowCreateNewItems;
    }
}
