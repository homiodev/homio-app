package org.homio.addon.camera.ui;

import static org.springframework.objenesis.instantiator.util.ClassUtils.newInstance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.homio.addon.camera.onvif.brand.BaseOnvifCameraBrandHandler;
import org.homio.addon.camera.service.OnvifCameraService;
import org.homio.addon.camera.service.CameraDeviceEndpoint;
import org.homio.addon.camera.ui.UICameraActionConditional.ActionConditional;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.model.OptionModel.KeyValueEnum;
import org.homio.api.state.JsonType;
import org.homio.api.state.State;
import org.homio.api.ui.UIActionHandler;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.ui.field.action.v1.UIEntityItemBuilder;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.action.v1.item.UIMultiButtonItemBuilder;
import org.homio.api.ui.field.action.v1.item.UISelectBoxItemBuilder;
import org.homio.api.ui.field.action.v1.item.UISliderItemBuilder;
import org.homio.api.ui.field.action.v1.layout.UIFlexLayoutBuilder;
import org.homio.api.ui.field.action.v1.layout.UILayoutBuilder;
import org.homio.api.ui.field.action.v1.layout.dialog.UIStickyDialogItemBuilder;
import org.homio.api.ui.field.selection.dynamic.DynamicOptionLoader;
import org.homio.api.ui.field.selection.dynamic.UIFieldDynamicSelection;
import org.homio.api.util.CommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Log4j2
public class CameraActionBuilder {

    public static void assembleActions(BaseOnvifCameraBrandHandler brandHandler, UIInputBuilder uiInputBuilder) {
        Set<String> handledMethods = new HashSet<>();
        Method[] actions = MethodUtils.getMethodsWithAnnotation(brandHandler.getClass(), UIVideoAction.class, true, false);
        Method[] endpointActions = MethodUtils.getMethodsWithAnnotation(brandHandler.getClass(), UIVideoEndpointAction.class, true, false);

        assembleActions(brandHandler, uiInputBuilder, handledMethods, actions, method -> new ActionContext(method, brandHandler.getService()));
        assembleActions(brandHandler, uiInputBuilder, handledMethods, endpointActions, method -> new ActionContext(method, brandHandler.getService()));
    }

    private static void assembleActions(
            BaseOnvifCameraBrandHandler brandHandler,
            UIInputBuilder uiInputBuilder,
            Set<String> handledMethods,
            Method[] actions,
            Function<Method, ActionContext> actionContextGetter) {
        for (Method method : actions) {
            if (handledMethods.add(method.getName())) {
                ActionContext context = actionContextGetter.apply(method);
                if (!context.condition.match(brandHandler.getService(), method, context.name)) {
                    continue;
                }

                Parameter actionParameter = method.getParameters()[0];
                Function<String, Object> actionParameterConverter = buildParameterActionConverter(actionParameter);

                UIFieldType type;
                if (context.type == VideoActionType.auto) {
                    if (method.isAnnotationPresent(UICameraSelectionAttributeValues.class) || method.isAnnotationPresent(UIFieldDynamicSelection.class)) {
                        type = UIFieldType.SelectBox;
                    } else {
                        type = getFieldTypeFromMethod(actionParameter);
                    }
                } else {
                    type = context.type == VideoActionType.slider
                            ? UIFieldType.Slider : context.type == VideoActionType.bool
                            ? UIFieldType.Boolean : UIFieldType.String;
                }

                UIActionHandler actionHandler = (entityContext, params) -> {
                    try {
                        method.invoke(brandHandler, actionParameterConverter.apply(params.optString("value")));
                    } catch (Exception ex) {
                        log.error("Unable to invoke camera action: <{}>", CommonUtils.getErrorMessage(ex));
                    }
                    return null;
                };

                UIEntityItemBuilder uiEntityItemBuilder;
                UILayoutBuilder layoutBuilder = uiInputBuilder;
                if (StringUtils.isNotEmpty(context.group)) {

                    if (StringUtils.isEmpty(context.subGroup)) {
                        layoutBuilder = uiInputBuilder.addFlex(context.group, context.order)
                                .columnFlexDirection()
                                .setBorderArea(context.group);
                    } else {
                        UIStickyDialogItemBuilder stickyLayoutBuilder = layoutBuilder.addStickyDialogButton(context.group + "_sb",
                                new Icon(context.subGroupIcon), context.order).editButton(buttonItemBuilder ->
                                buttonItemBuilder.setText(context.group));

                        layoutBuilder = stickyLayoutBuilder.addFlex(context.subGroup, context.order)
                                .setBorderArea(context.subGroup)
                                .columnFlexDirection();
                    }
                }
                UICameraDimmerButton[] buttons = method.getDeclaredAnnotationsByType(UICameraDimmerButton.class);
                if (buttons.length > 0) {
                    if (context.type != VideoActionType.slider) {
                        throw new RuntimeException(
                                "Method " + method.getName() + " annotated with @UICameraDimmerButton, but @UICameraAction has no dimmer type");
                    }
                    UIFlexLayoutBuilder flex = layoutBuilder.addFlex("dimmer", context.order);
                    UIMultiButtonItemBuilder multiButtonItemBuilder = flex.addMultiButton("dimm_btns", actionHandler, context.order);
                    for (UICameraDimmerButton button : buttons) {
                        multiButtonItemBuilder.addButton(button.name(), new Icon(button.icon()));
                    }
                    layoutBuilder = flex;
                }

                uiEntityItemBuilder = (UIEntityItemBuilder) createUIEntity(context, type, layoutBuilder, actionHandler)
                        .setIcon(context.icon);

                if (type == UIFieldType.SelectBox) {
                    if (actionParameter.getType().isEnum()) {
                        if (KeyValueEnum.class.isAssignableFrom(actionParameter.getType())) {
                            ((UISelectBoxItemBuilder) uiEntityItemBuilder).setOptions(
                                    OptionModel.list((Class<? extends KeyValueEnum>) actionParameter.getType()));
                        } else {
                            ((UISelectBoxItemBuilder) uiEntityItemBuilder).setOptions(OptionModel.enumList((Class<? extends Enum>) actionParameter.getType()));
                        }
                    } else if (method.isAnnotationPresent(UICameraSelectionAttributeValues.class)) {
                        uiEntityItemBuilder.addFetchValueHandler("update-selection", () -> {
                            UICameraSelectionAttributeValues attributeValues = method.getDeclaredAnnotation(UICameraSelectionAttributeValues.class);
                            State state = brandHandler.getAttribute(attributeValues.value());
                            if (state instanceof JsonType) {
                                JsonNode jsonNode = ((JsonType) state).get(attributeValues.path());
                                if (jsonNode instanceof ArrayNode) {
                                    List<OptionModel> items = OptionModel.list(jsonNode);
                                    for (int i = 0; i < attributeValues.prependValues().length; i += 2) {
                                        items.add(i / 2, OptionModel.of(attributeValues.prependValues()[i], attributeValues.prependValues()[i + 1]));
                                    }
                                    ((UISelectBoxItemBuilder) uiEntityItemBuilder).setOptions(items);
                                }
                            }
                        });
                    } else if (method.isAnnotationPresent(UIFieldDynamicSelection.class)) {
                        DynamicOptionLoader dynamicOptionLoader;
                        UIFieldDynamicSelection attributeValues = method.getDeclaredAnnotation(UIFieldDynamicSelection.class);
                        try {
                            Constructor<? extends DynamicOptionLoader> constructor = attributeValues.value().getDeclaredConstructor(brandHandler.getClass());
                            constructor.setAccessible(true);
                            dynamicOptionLoader = constructor.newInstance(brandHandler);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                        uiEntityItemBuilder.addFetchValueHandler("update-selection", () ->
                                ((UISelectBoxItemBuilder) uiEntityItemBuilder)
                                        .setOptions(dynamicOptionLoader.loadOptions(
                                                new DynamicOptionLoader.DynamicOptionLoaderParameters(
                                                        brandHandler.getEntity(),
                                                        brandHandler.getEntityContext(),
                                                        attributeValues.staticParameters(), null)
                                        )));
                    }
                }
                Method getter = findGetter(brandHandler, context.name);

                // add update value handler
                if (getter != null) {
                    uiEntityItemBuilder.addFetchValueHandler("update-value", () -> {
                        try {
                            Object value = getter.invoke(brandHandler);
                            if (value == null) {
                                return;
                            }
                            uiEntityItemBuilder.setValue(type.getConvertToObject().apply(value));
                        } catch (Exception ex) {
                            log.error("Unable to fetch getter value for action: <{}>. Msg: <{}>",
                                    context.name, CommonUtils.getErrorMessage(ex));
                        }
                    });
                }
            }
        }
    }

    private static UIEntityItemBuilder<?, ?> createUIEntity(ActionContext context, UIFieldType type,
                                                            UILayoutBuilder flex,
                                                            UIActionHandler handler) {
        return switch (type) {
            case SelectBox -> flex.addSelectBox(context.name, handler, context.order).setSelectReplacer(context.min,
                            context.max, context.selectReplacer)
                                  .setSeparatedText(context.name);
            case Slider -> flex.addSlider(context.name, 0F, (float) context.min,
                            (float) context.max, handler, UISliderItemBuilder.SliderType.Regular, context.order)
                               .setSeparatedText(context.name);
            case Boolean -> flex.addCheckbox(context.name, false, handler, context.order)
                                .setSeparatedText(context.name);
            case String -> flex.addInfo(context.name, context.order);
            default -> throw new RuntimeException("Unknown type: " + type);
        };
    }

    private static Method findGetter(Object instance, String name) {
        for (Method method : MethodUtils.getMethodsWithAnnotation(instance.getClass(), UICameraActionGetter.class, true, true)) {
            if (method.getDeclaredAnnotation(UICameraActionGetter.class).value().equals(name)) {
                return method;
            }
        }
        return null;
    }

    private static Function<String, Object> buildParameterActionConverter(Parameter parameter) {
        switch (parameter.getType().getSimpleName()) {
            case "boolean" -> {
                return Boolean::parseBoolean;
            }
            case "int" -> {
                return Integer::parseInt;
            }
        }
        if (parameter.getType().isEnum()) {
            return value -> Enum.valueOf((Class<? extends Enum>) parameter.getType(), value);
        }
        return command -> command;
    }

    private static UIFieldType getFieldTypeFromMethod(Parameter parameter) {
        switch (parameter.getType().getSimpleName()) {
            case "boolean" -> {
                return UIFieldType.Boolean;
            }
            case "int" -> {
                return UIFieldType.Slider;
            }
        }
        if (parameter.getType().isEnum()) {
            return UIFieldType.SelectBox;
        }

        return UIFieldType.String;
    }

    private static class ActionContext {

        private final String group;
        private final @NotNull VideoActionType type;
        private final @NotNull String name;
        private final int order;
        private final @NotNull Icon icon;
        private ActionConditional condition;

        private final @Nullable String subGroup;
        private final @Nullable String subGroupIcon;
        private final @Nullable String selectReplacer;
        private final int min;
        private final int max;

        public ActionContext(Method method, OnvifCameraService cameraService) {
            UIVideoActionMetadata metadata = method.getDeclaredAnnotation(UIVideoActionMetadata.class);
            this.subGroup = metadata == null ? null : metadata.subGroup();
            this.subGroupIcon = metadata == null ? null : metadata.subGroupIcon();
            this.selectReplacer = metadata == null ? null : metadata.selectReplacer();
            this.min = metadata == null ? 0 : metadata.min();
            this.max = metadata == null ? 0 : metadata.max();

            UICameraActionConditional actionCondition = method.getDeclaredAnnotation(UICameraActionConditional.class);
            if (actionCondition != null) {
                condition = newInstance(actionCondition.value());
            }
            if (method.isAnnotationPresent(UIVideoAction.class)) {
                UIVideoAction videoAction = method.getDeclaredAnnotation(UIVideoAction.class);
                this.type = videoAction.type();
                this.name = videoAction.name();
                this.order = videoAction.order();
                this.icon = new Icon(videoAction.icon(), videoAction.iconColor());
                this.group = videoAction.group();
            } else {
                UIVideoEndpointAction videoAction = method.getDeclaredAnnotation(UIVideoEndpointAction.class);
                this.type = videoAction.type();
                this.name = videoAction.value();

                if (condition == null) {
                    condition = (s, m, e) -> s.getEndpoints().containsKey(e);
                }
                CameraDeviceEndpoint endpoint = cameraService.getEndpoints().get(this.name);
                this.order = endpoint == null ? -1 : endpoint.getOrder();
                this.icon = endpoint == null ? new Icon() : endpoint.getIcon();
                this.group = endpoint == null ? null : "GROUP." + endpoint.getGroup().toUpperCase();
            }

            if (condition == null) {
                condition = (s, m, e) -> true;
            }
        }
    }
}
