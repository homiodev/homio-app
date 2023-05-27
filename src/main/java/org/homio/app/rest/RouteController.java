package org.homio.app.rest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.EntityContext;
import org.homio.api.ui.UISidebarMenu;
import org.homio.app.manager.common.ClassFinder;
import org.homio.app.manager.common.impl.EntityContextUIImpl;
import org.homio.app.model.entity.SettingEntity;
import org.homio.app.rest.AddonController.AddonJson;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rest/route")
public class RouteController {

    private final EntityContext entityContext;
    private final List<Class<?>> uiSidebarMenuClasses;
    private final AddonController addonController;
    private final SettingController settingController;

    public RouteController(
        ClassFinder classFinder,
        AddonController addonController,
        SettingController settingController,
        EntityContext entityContext) {
        this.uiSidebarMenuClasses = classFinder.getClassesWithAnnotation(UISidebarMenu.class);
        this.addonController = addonController;
        this.settingController = settingController;
        this.entityContext = entityContext;
    }

    @GetMapping("/bootstrap")
    public BootstrapContext getBootstrap() {
        BootstrapContext context = new BootstrapContext();
        context.routes = getRoutes();
        context.menu = getMenu();
        context.addons = addonController.getAddons();
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

        routes.addAll(Stream.of("dashboard").map(RouteDTO::new).collect(Collectors.toList()));
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
        routes.add(route);
    }

    private static class BootstrapContext {

        public List<RouteDTO> routes;
        public Map<String, List<SidebarMenuItem>> menu;
        public List<AddonJson> addons;
        public List<SettingEntity> settings;
        public EntityContextUIImpl.NotificationResponse notifications;
    }

    @Getter
    @RequiredArgsConstructor
    public static class SidebarMenuItem {

        private final String href;
        private final String icon;
        private final String bg;
        private final String label;
        private final int order;

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
        private String path;
        private String type;
        private boolean allowCreateNewItems;
    }
}
