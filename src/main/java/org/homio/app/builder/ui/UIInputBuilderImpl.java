package org.homio.app.builder.ui;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.homio.api.EntityContext;
import org.homio.api.ui.action.UIActionHandler;
import org.homio.api.ui.field.action.v1.UIEntityBuilder;
import org.homio.api.ui.field.action.v1.UIEntityItemBuilder;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.action.v1.UIInputEntity;
import org.homio.api.ui.field.action.v1.item.UIButtonItemBuilder;
import org.homio.api.ui.field.action.v1.layout.UILayoutBuilder;
import org.homio.api.ui.field.action.v1.layout.dialog.UIDialogLayoutBuilder;
import org.homio.app.builder.ui.layout.UIDialogLayoutBuilderImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("rawtypes")
@Getter
@RequiredArgsConstructor
public class UIInputBuilderImpl extends UIBaseLayoutBuilderImpl implements UIInputBuilder {

    private final EntityContext entityContext;
    private final String entityID = null;

    public UIButtonItemBuilder addReferenceAction(String name, String reference, int order) {
        return addEntity(
            new UIButtonItemBuilderImpl(UIItemType.Button, name, name, null, order, null)
                .setActionReference(reference));
    }

    public UIButtonItemBuilderImpl addFireActionBeforeChange(
        String name, String[] actions, String reference, int order) {
        return addEntity(new UIButtonItemBuilderImpl(UIItemType.Button, name, name, null, order, null)
            .setActionReference(reference))
            .setFireActionsBeforeChange(actions);
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public void from(UIInputBuilder source) {
        super.from(source);
    }

    @Override
    public @NotNull Collection<UIInputEntity> buildAll() {
        return getUiEntityBuilders(false).stream()
                                         .map(UIEntityBuilder::buildEntity)
                                         .sorted(Comparator.comparingInt(UIInputEntity::getOrder))
                                         .collect(Collectors.toList());
    }

    @Override
    public void fireFetchValues() {
        for (UIEntityItemBuilder itemBuilder : getUiEntityItemBuilders(true)) {
            Map<String, Runnable> handlers =
                ((UIBaseEntityItemBuilderImpl) itemBuilder).getFetchValueHandlers();
            if (handlers != null) {
                for (Runnable handler : handlers.values()) {
                    handler.run();
                }
            }
        }
    }

    @Override
    public UIActionHandler findActionHandler(@NotNull String key) {
        for (UIEntityItemBuilder entityBuilder : this.getUiEntityItemBuilders(true)) {
            if (entityBuilder.getEntityID().equals(key)
                && entityBuilder instanceof UIBaseEntityItemBuilderImpl) {
                return ((UIBaseEntityItemBuilderImpl) entityBuilder).getActionHandler();
            }
        }
        return null;
    }

    @Override
    public @NotNull UIButtonItemBuilder addSelectableButton(@NotNull String name, String icon,
        String iconColor, @Nullable UIActionHandler action, int order) {
        return addEntity(new UIButtonItemBuilderImpl(UIItemType.SelectableButton, name, icon, iconColor, order, action));
    }

    @Override
    public UIInputBuilder.DialogEntity<UIButtonItemBuilder> addOpenDialogSelectableButton(@NotNull String name, String icon, String color,
        @Nullable Integer dialogWidth, @NotNull UIActionHandler action, int order) {
        return addOpenDialogSelectableButtonInternal(name, icon, color, dialogWidth, action);
    }

    public UIInputBuilder.DialogEntity<UIButtonItemBuilder> addOpenDialogSelectableButtonInternal(
        String name, String icon, String color, Integer dialogWidth, UIActionHandler action) {
        UIDialogLayoutBuilderImpl uiDialogLayoutBuilder =
            new UIDialogLayoutBuilderImpl(name, dialogWidth);
        UIDialogLayoutBuilderImpl dialogEntityBuilder = addEntity(uiDialogLayoutBuilder);
        UIButtonItemBuilder entityBuilder =
            ((UIButtonItemBuilderImpl) addSelectableButton(name, icon, color, action))
                .setActionReference(dialogEntityBuilder.getEntityID());
        return new UIInputBuilder.DialogEntity<>() {
            @Override
            public UIInputBuilder up() {
                return UIInputBuilderImpl.this;
            }

            @Override
            public UIInputBuilder edit(Consumer<UIButtonItemBuilder> editHandler) {
                editHandler.accept(entityBuilder);
                return UIInputBuilderImpl.this;
            }

            @Override
            public UIInputBuilder editDialog(Consumer<UIDialogLayoutBuilder> editDialogHandler) {
                editDialogHandler.accept(dialogEntityBuilder);
                return UIInputBuilderImpl.this;
            }
        };
    }

    @Override
    public UIInputEntity buildEntity() {
        throw new RuntimeException("Must be not fired!");
    }

    private UIActionHandler findActionHandler(UIEntityBuilder entityBuilder, String key) {
        if (entityBuilder != null) {
            if (entityBuilder.getEntityID().equals(key)
                && entityBuilder instanceof UIBaseEntityItemBuilderImpl) {
                return ((UIBaseEntityItemBuilderImpl) entityBuilder).getActionHandler();
            }
            if (entityBuilder instanceof UILayoutBuilder) {
                for (UIEntityBuilder children :
                    ((UILayoutBuilder) entityBuilder).getUiEntityBuilders(false)) {
                    UIActionHandler actionHandler = findActionHandler(children, key);
                    if (actionHandler != null) {
                        return actionHandler;
                    }
                }
            }
        }
        return null;
    }
}
