package org.touchhome.app.rest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.touchhome.app.config.TouchHomeProperties;
import org.touchhome.app.manager.common.ClassFinder;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.manager.common.impl.EntityContextUIImpl;
import org.touchhome.app.model.entity.SettingEntity;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.ui.UISidebarButton;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.ui.action.UIActionHandler;
import org.touchhome.bundle.api.util.TouchHomeUtils;

@RestController
@RequestMapping("/rest/route")
public class RouteController {

  private final EntityContext entityContext;
  private final List<Class<?>> uiSidebarMenuClasses;
  private final BundleController bundleController;
  private final SettingController settingController;
  private final ItemController itemController;
  private final TouchHomeProperties touchHomeProperties;

  public RouteController(ClassFinder classFinder, BundleController bundleController,
      ItemController itemController,
      TouchHomeProperties touchHomeProperties,
      SettingController settingController, EntityContext entityContext) {
    this.uiSidebarMenuClasses = classFinder.getClassesWithAnnotation(UISidebarMenu.class);
    this.bundleController = bundleController;
    this.itemController = itemController;
    this.settingController = settingController;
    this.entityContext = entityContext;
    this.touchHomeProperties = touchHomeProperties;
  }

  @GetMapping("/bootstrap")
  public BootstrapContext getBootstrap() {
    BootstrapContext context = new BootstrapContext();
    context.appVersion = touchHomeProperties.getVersion();
    context.runCount = TouchHomeUtils.RUN_COUNT;
    context.bundleUpdateCount = EntityContextImpl.BUNDLE_UPDATE_COUNT;
    context.routes = getRoutes();
    context.menu = getMenu();
    context.bundles = bundleController.getBundles();
    context.settings = settingController.getSettings();
    context.notifications = ((EntityContextUIImpl) entityContext.ui()).getNotifications();

    return context;
  }

  private Map<String, List<SidebarMenuItem>> getMenu() {
    Map<String, List<SidebarMenuItem>> sidebarMenus = new HashMap<>();

    for (Class<?> item : this.uiSidebarMenuClasses) {
      getSubMenu(sidebarMenus, item, item.getDeclaredAnnotation(UISidebarMenu.class));
    }

    for (List<SidebarMenuItem> sidebarMenuItems : sidebarMenus.values()) {
      sidebarMenuItems.sort(Comparator.comparingInt(SidebarMenuItem::getOrder));
    }

    return sidebarMenus;
  }

  private void getSubMenu(Map<String, List<SidebarMenuItem>> sidebarMenus, Class<?> item, UISidebarMenu uiSidebarMenu) {
    String parent = uiSidebarMenu.parent().name().toLowerCase();
    if (!sidebarMenus.containsKey(parent)) {
      sidebarMenus.put(parent, new ArrayList<>());
    }
    sidebarMenus.get(parent).add(SidebarMenuItem.fromAnnotation(item, uiSidebarMenu));
  }

  private List<RouteJSON> getRoutes() {
    List<RouteJSON> routes = new ArrayList<>();
    for (Class<?> aClass : this.uiSidebarMenuClasses) {
      addRouteFromUISideBarMenu(routes, aClass, aClass.getAnnotation(UISidebarMenu.class));
    }

    routes.addAll(Stream.of("dashboard").map(RouteJSON::new).collect(Collectors.toList()));
    return routes;
  }

  private void addRouteFromUISideBarMenu(List<RouteJSON> routes, Class<?> aClass, UISidebarMenu uiSidebarMenu) {
    String href = StringUtils.defaultIfEmpty(uiSidebarMenu.overridePath(), aClass.getSimpleName());
    RouteJSON route = new RouteJSON(uiSidebarMenu.parent().name().toLowerCase() + "/" + href);
    route.type = aClass.getSimpleName();
    route.path = href;
    route.itemType = UISidebarMenu.class.isAssignableFrom(uiSidebarMenu.itemType()) ? aClass : uiSidebarMenu.itemType();
    route.allowCreateNewItems = uiSidebarMenu.allowCreateNewItems();
    route.sidebarButtons = new ArrayList<>();
    for (UISidebarButton button : aClass.getAnnotationsByType(UISidebarButton.class)) {
      UIActionHandler actionHandler = itemController.getOrCreateUIActionHandler(button.handlerClass());
      if (actionHandler.isEnabled(entityContext)) {
        String key = aClass.getSimpleName() + "_" + button.handlerClass().getSimpleName();
        route.sidebarButtons.add(new SidebarButton(key, button.buttonIcon(), button.buttonTitle(), button.buttonText(),
            button.buttonIconColor(), button.confirm(), actionHandler.getClass().getSimpleName()));
      }
    }
    routes.add(route);
  }

  private static class BootstrapContext {

    public int appVersion;
    public int runCount;
    public int bundleUpdateCount;
    public List<RouteJSON> routes;
    public Map<String, List<SidebarMenuItem>> menu;
    public List<BundleController.BundleJson> bundles;
    public List<SettingEntity> settings;
    public EntityContextUIImpl.NotificationResponse notifications;
  }

  @Getter
  public static class SidebarMenuItem {

    private String href;
    private String icon;
    private String bg;
    private String label;
    private int order;

    private SidebarMenuItem(String href, String icon, String bg, String label, int order) {
      this.href = href;
      this.icon = icon;
      this.bg = bg;
      this.label = label;
      this.order = order;
    }

    static SidebarMenuItem fromAnnotation(Class<?> clazz, UISidebarMenu uiSidebarMenu) {
      return new SidebarMenuItem(
          StringUtils.defaultIfEmpty(uiSidebarMenu.overridePath(), clazz.getSimpleName()),
          uiSidebarMenu.icon(),
          uiSidebarMenu.bg(),
          clazz.getSimpleName(),
          uiSidebarMenu.order()
      );
    }
  }

  @Getter
  public static class RouteJSON {

    private String path;
    private String url;
    private String type;
    private Class<?> itemType;
    private boolean allowCreateNewItems;
    private List<SidebarButton> sidebarButtons;

    public RouteJSON(String url) {
      this.url = url;
    }
  }

  @Getter
  @AllArgsConstructor
  public static class SidebarButton {

    private final String entityID;
    private final String icon;
    private final String title;
    private final String text;
    private final String color;
    private final String confirm;
    private final String handlerClass;
  }
}
