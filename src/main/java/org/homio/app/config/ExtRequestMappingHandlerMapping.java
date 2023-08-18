package org.homio.app.config;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.homio.app.extloader.AddonContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

public class ExtRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

    private final Map<Object, List<RequestMappingInfo>> controllerToMethodRegisterInfo = new HashMap<>();

    public void registerAddonRestControllers(@NotNull AddonContext addonContext) {
        Collection<Object> restControllers = addonContext.getApplicationContext().getBeansWithAnnotation(RestController.class).values();
        for (Object restController : restControllers) {
            controllerToMethodRegisterInfo.put(restController, new ArrayList<>());
            super.detectHandlerMethods(restController);
            addonContext.onDestroy(() -> {
                for (RequestMappingInfo requestMappingInfo : controllerToMethodRegisterInfo.get(restController)) {
                    unregisterMapping(requestMappingInfo);
                }
            });
        }
    }

    @Override
    protected void registerHandlerMethod(Object handler, Method method, RequestMappingInfo mapping) {
        if (controllerToMethodRegisterInfo.containsKey(handler)) {
            controllerToMethodRegisterInfo.get(handler).add(mapping);
        }
        super.registerHandlerMethod(handler, method, mapping);
    }
}
