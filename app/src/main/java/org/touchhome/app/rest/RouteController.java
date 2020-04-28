package org.touchhome.app.rest;

import lombok.Getter;
import net.rossillo.spring.web.mvc.CacheControl;
import net.rossillo.spring.web.mvc.CachePolicy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.touchhome.app.json.RouteJSON;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.util.ClassFinder;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/rest/route")
public class RouteController {

    private final List<Class<?>> uiSidebarMenuClasses;

    public RouteController(ClassFinder classFinder) {
        this.uiSidebarMenuClasses = classFinder.getClassesWithAnnotation(UISidebarMenu.class);
    }

    @GetMapping
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public List<RouteJSON> getRoutes() {
        List<RouteJSON> routes = new ArrayList<>();
        for (Class<?> aClass : this.uiSidebarMenuClasses) {
            addRouteFromUISideBarMenu(routes, aClass, aClass.getAnnotation(UISidebarMenu.class));
        }

        routes.addAll(Stream.of("dashboard").map(RouteJSON::new).collect(Collectors.toList()));
        return routes;
    }

    @GetMapping("menu")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public Map<String, List<SidebarMenuItem>> getMenu() {
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

    private void addRouteFromUISideBarMenu(List<RouteJSON> routes, Class<?> aClass, UISidebarMenu uiSidebarMenu) {
        String href = aClass.getSimpleName();
        RouteJSON route = new RouteJSON(uiSidebarMenu.parent().name().toLowerCase() + "/" + href);
        route.setType(href);
        route.setItemType(UISidebarMenu.class.isAssignableFrom(uiSidebarMenu.itemType()) ? aClass : uiSidebarMenu.itemType());
        route.setAllowCreateNewItems(uiSidebarMenu.allowCreateNewItems());
        routes.add(route);
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
                    clazz.getSimpleName(),
                    uiSidebarMenu.icon(),
                    uiSidebarMenu.bg(),
                    clazz.getSimpleName(),
                    uiSidebarMenu.order()

            );
        }
    }
}
