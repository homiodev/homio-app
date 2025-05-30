package org.homio.app.rest;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
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
import org.homio.app.rest.ConsoleController.ConsoleTab;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.cloud.gateway.mvc.ProxyExchange;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

@RestController
@RequestMapping(value = "/rest/route", produces = "application/json")
public class RouteController {

  private final Context context;
  private final List<Class<?>> uiSidebarMenuClasses;
  private final AddonService addonService;
  private final SettingController settingController;
  private final ConsoleController consoleController;

  public RouteController(
    ClassFinder classFinder,
    AddonService addonService,
    SettingController settingController,
    ConsoleController consoleController,
    Context context) {
    this.uiSidebarMenuClasses = classFinder.getClassesWithAnnotation(UISidebarMenu.class);
    this.addonService = addonService;
    this.settingController = settingController;
    this.context = context;
    this.consoleController = consoleController;
  }

  private static @NotNull HttpHeaders buildResponseHeader(
          ResponseEntity<byte[]> response,
          Map<String, String> applyHeader) {
    HttpHeaders responseHeaders = new HttpHeaders();
    for (Entry<String, List<String>> rh : response.getHeaders().entrySet()) {
      if (!rh.getKey().equalsIgnoreCase("X-Frame-Options")) {
        responseHeaders.addAll(rh.getKey(), rh.getValue());
      }
    }

    if (applyHeader != null) {
      for (Entry<String, String> entry : applyHeader.entrySet()) {
        if (!entry.getKey().equalsIgnoreCase("X-Frame-Options")) {
          responseHeaders.add(entry.getKey(), entry.getValue());
        }
      }
    }
    return responseHeaders;
  }

  @SneakyThrows
  private static byte[] modifyResponseBody(byte[] body, String baseUrl, String redirectURI) {
    Document doc = Jsoup.parse(new String(body));
    doc.head().prependElement("base")
            .attr("href", baseUrl + "/");
    String path = new URI(baseUrl).getPath();
    for (Element el : doc.head().children()) {
      switch (el.tagName()) {
        case "link":
          String href = el.attr("href");
          if(!href.startsWith("http") && !href.isEmpty()) {
            if (href.startsWith("/")) {
              el.attr("href", path + href);
            }
          }
          break;
        case "script":
          String src = el.attr("src");
            if(!src.startsWith("http") && !src.isEmpty()) {
              if (src.startsWith("/")) {
                el.attr("src", path + src);
              } else {
                el.attr("src", path + redirectURI + "/" + src);
              }
          }
          break;
      }
    }
    doc.head().appendElement("script")
            .attr("type", "text/javascript")
            .appendChild(new DataNode("""
        (function() {
            // Patch XMLHttpRequest to modify URL before sending
            const originalXHROpen = XMLHttpRequest.prototype.open;
            XMLHttpRequest.prototype.open = function(method, url, ...args) {
                let modifiedUrl = url;
                if (typeof url === 'string' && url.startsWith('/')) {
                    // If URL starts with '/', remove the leading slash.
                    // This makes it relative to the <base> tag URI.
                    modifiedUrl = url.substring(1);
                }
                // Call the original open method with potentially modified URL and all other arguments
                return originalXHROpen.call(this, method, modifiedUrl, ...args);
            };

            // Patch fetch to modify URL before sending
            const originalFetch = window.fetch;
            if (originalFetch) { // Check if fetch API exists
                window.fetch = function(input, init) {
                    let modifiedInput = input;
                    if (typeof input === 'string' && input.startsWith('/')) {
                        // If input is a string URL starting with '/', remove the leading slash.
                        // This makes it relative to the <base> tag URI.
                        modifiedInput = input.substring(1);
                    }
                    // For Request objects as input, their URL is typically already resolved
                    // by the browser considering the document's <base href> when the Request object was created.
                    // Modifying it would require creating a new Request object, which adds complexity.
                    // We keep it simple here, targeting explicit string URLs similar to the original jQuery behavior.
                    return originalFetch.call(this, modifiedInput, init);
                };
            }

            // Event listener for parent window messages (e.g., for navigation)
            window.addEventListener('message', function(event) {
              if (event.data === 'back') {
                window.history.back();
              }
              if (event.data === 'forward') {
                window.history.forward();
              }
              if (event.data === 'reload') {
                window.location.reload();
              }
            });
        })();
        """));
    String txtBody = doc.toString();
    txtBody = txtBody.replaceAll("<img src=\"/", "<img src=\"" + path + "/");
    return txtBody.getBytes();
  }

  private static void modifyRequestBody(@NotNull ProxyExchange<byte[]> proxy, @NotNull HttpServletRequest request)
          throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    String contentType = request.getHeader("Content-Type");
    // somehow if request url is i.e.: data=blabla=2 it converts second '=' to %3X
    if (contentType != null && contentType.startsWith("application/x-www-form-urlencoded")) {
      byte[] body = (byte[]) MethodUtils.invokeMethod(proxy, true, "body");
      String decodedValue = URLDecoder.decode(new String(body, UTF_8), UTF_8);
      proxy.body(decodedValue.getBytes(UTF_8));
    }
  }

  private static void modifyHeaders(@NotNull HttpServletRequest request, ProxyExchange<byte[]> proxyExchange, URI uri, RouteProxyImpl routeProxy) {
    Enumeration<String> names = request.getHeaderNames();
    while (names.hasMoreElements()) {
      String headerName = names.nextElement();
      proxyExchange.header(headerName, request.getHeader(headerName));
    }
    proxyExchange.header("Host", uri.getHost() + ":" + uri.getPort());
    proxyExchange.header("Origin", routeProxy.getUrl());
    proxyExchange.header("Referer", routeProxy.getUrl());
  }

  @GetMapping("/bootstrap")
  public BootstrapContext getBootstrap(WebRequest webRequest) {
    BootstrapContext bootstrapContext = new BootstrapContext();
    bootstrapContext.routes = getRoutes();
    bootstrapContext.menu = getMenu();
    bootstrapContext.addons = addonService.getAllAddonJson();
    bootstrapContext.settings = settingController.getSettings();
    bootstrapContext.notifications = ((ContextUIImpl) context.ui()).getNotifications();
    bootstrapContext.consoleTabs = consoleController.getTabs();
    bootstrapContext.customImages = ContextUIImpl.customImages;

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

  @PutMapping("/proxy/{entityID}/**")
  public ResponseEntity<?> proxyPut(@PathVariable("entityID") String entityID, HttpServletRequest request, ProxyExchange<byte[]> proxy) {
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

    modifyRequestBody(proxy, request);

    ProxyExchange<byte[]> proxyExchange = proxy.uri(uri);
        /*String homioToken = request.getParameter("homio_token");
        if(homioToken != null) {
            proxyExchange.header("Authorization", "Bearer " + homioToken);
        }*/

    proxyExchange.excluded(""); // allow pass Cookie and Authentication headers
    modifyHeaders(request, proxyExchange, uri, routeProxy);

    ResponseEntity<byte[]> response = handler.apply(proxyExchange);
    String redirectURI = "";
    if (response.getStatusCode().is3xxRedirection()) {
      URI locationURI = response.getHeaders().getLocation();
      String redirectPath  = locationURI == null || "./".equals(locationURI.getPath()) ? "" : locationURI.getPath();
      String query = locationURI == null ? "" : locationURI.getQuery();
      String fragment = locationURI == null ? "" : locationURI.getFragment();
      uri = new URI(uri.getScheme(), uri.getAuthority(), redirectPath, query, fragment);
      proxyExchange = proxy.uri(uri);
      proxyExchange.excluded("");
      modifyHeaders(request, proxyExchange, uri, routeProxy);
      response = handler.apply(proxyExchange);
      if (request.getRequestURI().endsWith("proxy_index.html")) {
        redirectURI = uri.getPath();
      }
    }

    byte[] body = response.getBody();
    Map<String, String> applyHeader = routeProxy.applyResponseHeaders(proxyUrl);
    HttpHeaders responseHeaders = buildResponseHeader(response, applyHeader);

    if (body != null && request.getRequestURI().endsWith(".html")) {
      String url = request.getRequestURL().toString();
      String baseUrl = url.substring(0, url.lastIndexOf('/'));
      body = modifyResponseBody(body, baseUrl, redirectURI);
    }

    responseHeaders.add(ACCESS_CONTROL_EXPOSE_HEADERS, "*");
    return ResponseEntity.status(response.getStatusCode())
            .headers(responseHeaders)
            .body(body);
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
    public Set<SettingEntity> settings;
    public ContextUIImpl.NotificationResponse notifications;
    public Set<ConsoleTab> consoleTabs;
    public Map<String, String> customImages;
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