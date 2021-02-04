package org.touchhome.app.videoStream.ui;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@Getter
@Log4j2
public class CameraAction {
    private final String name;
    private final String icon;
    private final String iconColor;
    private final UICameraAction.ActionType type;
    private final JSONObject options;

    @JsonIgnore
    private final Consumer<String> action;

    @JsonIgnore
    private final Supplier<String> getter;

    @Setter
    private String value;

    private CameraAction(String name, String icon, String iconColor, UICameraAction.ActionType type,
                         Consumer<String> action, JSONObject options, Supplier<String> getter) {
        this.name = name;
        this.type = type;
        this.action = action;
        this.icon = icon;
        this.iconColor = iconColor;
        this.getter = getter;
        this.options = options;
    }

    public static CameraActionsBuilder builder() {
        return new CameraActionsBuilder();
    }

    public static class CameraActionsBuilder {

        private List<CameraAction> actions = new ArrayList<>();
        private CameraAction lastAction;

        public CameraActionsBuilder add(String name, String icon, String iconColor, UICameraAction.ActionType type,
                                        JSONObject params, Consumer<String> action,
                                        Supplier<String> getter) {
            this.lastAction = new CameraAction(name, icon, iconColor, type, action, params, getter);
            actions.add(this.lastAction);
            return this;
        }

        public List<CameraAction> get() {
            return actions;
        }

        public void addAll(List<CameraAction> cameraActions) {
            this.actions.addAll(cameraActions);
        }

        private void addButton(String name, String icon) {
            if (!this.lastAction.options.has("buttons")) {
                this.lastAction.options.put("buttons", new JSONArray());
            }
            ((JSONArray) this.lastAction.options.get("buttons")).put(new JSONObject().put("name", name).put("icon", icon));
        }
    }

    public static List<CameraAction> assemble(Object instance, Object conditionalInstance) {
        CameraAction.CameraActionsBuilder builder = CameraAction.builder();
        for (Method method : MethodUtils.getMethodsWithAnnotation(instance.getClass(), UICameraAction.class, false, true)) {
            UICameraActionConditional cameraActionConditional = method.getDeclaredAnnotation(UICameraActionConditional.class);

            if (cameraActionConditional == null || TouchHomeUtils.newInstance(cameraActionConditional.value()).test(conditionalInstance)) {
                UICameraAction uiCameraAction = method.getDeclaredAnnotation(UICameraAction.class);
                Parameter actionParameter = method.getParameters()[0];
                Function<String, Object> actionParameterConverter = buildParameterActionConverter(actionParameter);
                JSONObject options = new JSONObject();
                if (uiCameraAction.min() != 0) {
                    options.put("min", uiCameraAction.min());
                }
                if (uiCameraAction.max() != 100) {
                    options.put("max", uiCameraAction.max());
                }
                if (StringUtils.isNotEmpty(uiCameraAction.selectReplacer())) {
                    options.put("selectReplacer", uiCameraAction.selectReplacer());
                }

                UICameraAction.ActionType type = uiCameraAction.type() == UICameraAction.ActionType.AutoDiscover ? getFieldTypeFromMethod(actionParameter) : uiCameraAction.type();
                Method getter = findGetter(instance, uiCameraAction.name());
                builder.add(uiCameraAction.name(), uiCameraAction.icon(), uiCameraAction.iconColor(), type,
                        options, param -> {
                            try {
                                method.invoke(instance, actionParameterConverter.apply(param));
                            } catch (Exception ex) {
                                log.error("Unable to invoke camera action: <{}>", TouchHomeUtils.getErrorMessage(ex));
                            }
                        }, () -> {
                            try {
                                if (getter != null) {
                                    Object value = getter.invoke(instance);
                                    return value == null ? null : value.toString();
                                }
                                return null;
                            } catch (Exception ex) {
                                log.error("Unable to fetch getter value for action: <{}>. Msg: <{}>",
                                        uiCameraAction.name(), TouchHomeUtils.getErrorMessage(ex));
                            }
                            return null;
                        });
                UICameraDimmerButton[] buttons = method.getDeclaredAnnotationsByType(UICameraDimmerButton.class);
                if (buttons.length > 0 && uiCameraAction.type() != UICameraAction.ActionType.Dimmer) {
                    throw new RuntimeException("Method " + method.getName() + " annotated with @UICameraDimmerButton, but @UICameraAction has no dimmer type");
                }
                for (UICameraDimmerButton button : buttons) {
                    builder.addButton(button.name(), button.icon());
                }
            }
        }
        return builder.get();
    }

    private static Method findGetter(Object instance, String name) {
        for (Method method : MethodUtils.getMethodsWithAnnotation(instance.getClass(), UICameraActionGetter.class, false, true)) {
            if (method.getDeclaredAnnotation(UICameraActionGetter.class).value().equals(name)) {
                return method;
            }
        }
        return null;
    }

    private static Function<String, Object> buildParameterActionConverter(Parameter parameter) {
        switch (parameter.getType().getSimpleName()) {
            case "boolean":
                return Boolean::parseBoolean;
            case "int":
                return Integer::parseInt;
        }
        return command -> command;
    }

    private static UICameraAction.ActionType getFieldTypeFromMethod(Parameter parameter) {
        switch (parameter.getType().getSimpleName()) {
            case "boolean":
                return UICameraAction.ActionType.Switch;
            case "int":
                return UICameraAction.ActionType.Dimmer;
        }
        return UICameraAction.ActionType.String;
    }
}
