package org.homio.addon.camera.ui;

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
import org.homio.addon.camera.entity.VideoActionsContext;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.model.OptionModel.KeyValueEnum;
import org.homio.api.state.JsonType;
import org.homio.api.state.State;
import org.homio.api.ui.action.DynamicOptionLoader;
import org.homio.api.ui.action.UIActionHandler;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.ui.field.action.v1.UIEntityItemBuilder;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.action.v1.item.UIMultiButtonItemBuilder;
import org.homio.api.ui.field.action.v1.item.UISelectBoxItemBuilder;
import org.homio.api.ui.field.action.v1.item.UISliderItemBuilder;
import org.homio.api.ui.field.action.v1.layout.UIFlexLayoutBuilder;
import org.homio.api.ui.field.action.v1.layout.UILayoutBuilder;
import org.homio.api.ui.field.action.v1.layout.dialog.UIStickyDialogItemBuilder;
import org.homio.api.ui.field.selection.UIFieldSelection;
import org.homio.api.util.CommonUtils;

@Log4j2
public class CameraActionBuilder {

    public static void assembleActions(VideoActionsContext instance, UIInputBuilder uiInputBuilder) {
        Set<String> handledMethods = new HashSet<>();
        for (Method method : MethodUtils.getMethodsWithAnnotation(instance.getClass(), UIVideoAction.class, true, false)) {
            UICameraActionConditional cameraActionConditional = method.getDeclaredAnnotation(UICameraActionConditional.class);
            if (handledMethods.add(method.getName()) && (cameraActionConditional == null ||
                CommonUtils.newInstance(cameraActionConditional.value()).test(instance))) {

                UIVideoAction uiVideoAction = method.getDeclaredAnnotation(UIVideoAction.class);
                Parameter actionParameter = method.getParameters()[0];
                Function<String, Object> actionParameterConverter = buildParameterActionConverter(actionParameter);

                UIFieldType type;
                if (uiVideoAction.type() == UIVideoAction.ActionType.AutoDiscover) {
                    if (method.isAnnotationPresent(UICameraSelectionAttributeValues.class) || method.isAnnotationPresent(UIFieldSelection.class)) {
                        type = UIFieldType.SelectBox;
                    } else {
                        type = getFieldTypeFromMethod(actionParameter);
                    }
                } else {
                    type = uiVideoAction.type() == UIVideoAction.ActionType.Dimmer
                        ? UIFieldType.Slider : uiVideoAction.type() == UIVideoAction.ActionType.Switch
                        ? UIFieldType.Boolean : UIFieldType.String;
                }

                UIActionHandler actionHandler = (entityContext, params) -> {
                    try {
                        method.invoke(instance, actionParameterConverter.apply(params.optString("value")));
                    } catch (Exception ex) {
                        log.error("Unable to invoke camera action: <{}>", CommonUtils.getErrorMessage(ex));
                    }
                    return null;
                };

                UIEntityItemBuilder uiEntityItemBuilder;
                UILayoutBuilder layoutBuilder = uiInputBuilder;
                if (StringUtils.isNotEmpty(uiVideoAction.group())) {

                    if (StringUtils.isEmpty(uiVideoAction.subGroup())) {
                        layoutBuilder = uiInputBuilder.addFlex(uiVideoAction.group(), uiVideoAction.order())
                                                      .columnFlexDirection()
                                                      .setBorderArea(uiVideoAction.group());
                    } else {
                        UIStickyDialogItemBuilder stickyLayoutBuilder = layoutBuilder.addStickyDialogButton(uiVideoAction.group() + "_sb",
                            new Icon(uiVideoAction.subGroupIcon()), uiVideoAction.order()).editButton(buttonItemBuilder ->
                            buttonItemBuilder.setText(uiVideoAction.group()));

                        layoutBuilder = stickyLayoutBuilder.addFlex(uiVideoAction.subGroup(), uiVideoAction.order())
                                                           .setBorderArea(uiVideoAction.subGroup())
                                                           .columnFlexDirection();
                    }
                }
                UICameraDimmerButton[] buttons = method.getDeclaredAnnotationsByType(UICameraDimmerButton.class);
                if (buttons.length > 0) {
                    if (uiVideoAction.type() != UIVideoAction.ActionType.Dimmer) {
                        throw new RuntimeException(
                            "Method " + method.getName() + " annotated with @UICameraDimmerButton, but @UICameraAction has no dimmer type");
                    }
                    UIFlexLayoutBuilder flex = layoutBuilder.addFlex("dimmer", uiVideoAction.order());
                    UIMultiButtonItemBuilder multiButtonItemBuilder = flex.addMultiButton("dimm_btns", actionHandler, uiVideoAction.order());
                    for (UICameraDimmerButton button : buttons) {
                        multiButtonItemBuilder.addButton(button.name(), new Icon(button.icon()));
                    }
                    layoutBuilder = flex;
                }

                uiEntityItemBuilder = (UIEntityItemBuilder) createUIEntity(uiVideoAction, type, layoutBuilder, actionHandler)
                    .setIcon(new Icon(uiVideoAction.icon(), uiVideoAction.iconColor()));

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
                            State state = instance.getAttribute(attributeValues.value());
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
                    } else if (method.isAnnotationPresent(UIFieldSelection.class)) {
                        DynamicOptionLoader dynamicOptionLoader;
                        UIFieldSelection attributeValues = method.getDeclaredAnnotation(UIFieldSelection.class);
                        try {
                            Constructor<? extends DynamicOptionLoader> constructor = attributeValues.value().getDeclaredConstructor(instance.getClass());
                            constructor.setAccessible(true);
                            dynamicOptionLoader = constructor.newInstance(instance);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                        uiEntityItemBuilder.addFetchValueHandler("update-selection", () -> {
                            ((UISelectBoxItemBuilder) uiEntityItemBuilder)
                                .setOptions(dynamicOptionLoader.loadOptions(
                                    new DynamicOptionLoader.DynamicOptionLoaderParameters(
                                        instance.getEntity(),
                                        instance.getEntityContext(),
                                        attributeValues.staticParameters(), null)
                                ));
                        });
                    }
                }
                Method getter = findGetter(instance, uiVideoAction.name());

                // add update value handler
                if (getter != null) {
                    uiEntityItemBuilder.addFetchValueHandler("update-value", () -> {
                        try {
                            Object value = getter.invoke(instance);
                            if (value == null) {
                                return;
                            }
                            uiEntityItemBuilder.setValue(type.getConvertToObject().apply(value));
                        } catch (Exception ex) {
                            log.error("Unable to fetch getter value for action: <{}>. Msg: <{}>",
                                uiVideoAction.name(), CommonUtils.getErrorMessage(ex));
                        }
                    });
                }
            }
        }
    }

    private static UIEntityItemBuilder<?, ?> createUIEntity(UIVideoAction uiVideoAction, UIFieldType type,
        UILayoutBuilder flex,
        UIActionHandler handler) {
        return switch (type) {
            case SelectBox -> flex.addSelectBox(uiVideoAction.name(), handler, uiVideoAction.order()).setSelectReplacer(uiVideoAction.min(),
                                      uiVideoAction.max(), uiVideoAction.selectReplacer())
                                  .setSeparatedText("CONTEXT.ACTION." + uiVideoAction.name());
            case Slider -> flex.addSlider(uiVideoAction.name(), 0F, (float) uiVideoAction.min(),
                                   (float) uiVideoAction.max(), handler, UISliderItemBuilder.SliderType.Regular, uiVideoAction.order())
                               .setSeparatedText("CONTEXT.ACTION." + uiVideoAction.name());
            case Boolean -> flex.addCheckbox(uiVideoAction.name(), false, handler, uiVideoAction.order())
                                .setSeparatedText("CONTEXT.ACTION." + uiVideoAction.name());
            case String -> flex.addInfo(uiVideoAction.name(), uiVideoAction.order());
            default -> throw new RuntimeException("Unknown type: " + type);
        };
    }

    private static Method findGetter(Object instance, String name) {
        for (Method method : MethodUtils.getMethodsWithAnnotation(instance.getClass(), UIVideoActionGetter.class, true, true)) {
            if (method.getDeclaredAnnotation(UIVideoActionGetter.class).value().equals(name)) {
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
        if (parameter.getType().isEnum()) {
            return value -> Enum.valueOf((Class<? extends Enum>) parameter.getType(), value);
        }
        return command -> command;
    }

    private static UIFieldType getFieldTypeFromMethod(Parameter parameter) {
        switch (parameter.getType().getSimpleName()) {
            case "boolean":
                return UIFieldType.Boolean;
            case "int":
                return UIFieldType.Slider;
        }
        if (parameter.getType().isEnum()) {
            return UIFieldType.SelectBox;
        }

        return UIFieldType.String;
    }
}
