package org.touchhome.app.rest;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.rossillo.spring.web.mvc.CacheControl;
import net.rossillo.spring.web.mvc.CachePolicy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.touchhome.app.manager.common.ClassFinder;
import org.touchhome.bundle.api.ui.UISidebarButton;
import org.touchhome.bundle.api.ui.UISidebarMenu;

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
        route.type = href;
        route.itemType = UISidebarMenu.class.isAssignableFrom(uiSidebarMenu.itemType()) ? aClass : uiSidebarMenu.itemType();
        route.allowCreateNewItems = uiSidebarMenu.allowCreateNewItems();
        route.sidebarButtons = new ArrayList<>();
        for (UISidebarButton button : aClass.getAnnotationsByType(UISidebarButton.class)) {
            route.sidebarButtons.add(new SidebarButton(button.buttonIcon(), button.buttonTitle(), button.buttonText(),
                    button.buttonIconColor(), button.confirm(), button.handlerClass().getSimpleName()));
        }
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

    @Getter
    public static class RouteJSON {

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
        private final String icon;
        private final String title;
        private final String text;
        private final String color;
        private final String confirm;
        private final String handlerClass;
    }
}
