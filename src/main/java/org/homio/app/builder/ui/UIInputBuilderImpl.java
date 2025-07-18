package org.homio.app.builder.ui;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.homio.api.Context;
import org.homio.api.model.Icon;
import org.homio.api.ui.UIActionHandler;
import org.homio.api.ui.field.action.v1.UIEntityBuilder;
import org.homio.api.ui.field.action.v1.UIEntityItemBuilder;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.action.v1.UIInputEntity;
import org.homio.api.ui.field.action.v1.item.UIButtonItemBuilder;
import org.homio.api.ui.field.action.v1.layout.UILayoutBuilder;
import org.homio.api.ui.field.action.v1.layout.dialog.UIDialogLayoutBuilder;
import org.homio.api.util.CommonUtils;
import org.homio.app.builder.ui.layout.UIDialogLayoutBuilderImpl;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.rest.ItemController;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@SuppressWarnings("rawtypes")
@Getter
@RequiredArgsConstructor
public class UIInputBuilderImpl extends UIBaseLayoutBuilderImpl implements UIInputBuilder {

  private final @Accessors(fluent = true) Context context;
  private final String entityID = null;

  public UIButtonItemBuilder addReferenceAction(String name, String reference, int order) {
    return addEntity(
      new UIButtonItemBuilderImpl(UIItemType.Button, name, null, order)
        .setActionReference(reference));
  }

  public UIButtonItemBuilderImpl addFireActionBeforeChange(
    String name, String[] actions, String reference, int order) {
    return addEntity(new UIButtonItemBuilderImpl(UIItemType.Button, name, null, order)
      .setActionReference(reference))
      .setFireActionsBeforeChange(actions);
  }

  @Override
  public int getOrder() {
    return 0;
  }

  @Override
  public void from(@Nullable UIInputBuilder source) {
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
      if (entityBuilder instanceof UIBaseEntityItemBuilderImpl) {
        if (entityBuilder.getEntityID().equals(key)) {
          return ((UIBaseEntityItemBuilderImpl) entityBuilder).getActionHandler();
        }
      }
    }
    return null;
  }

  @Override
  public @NotNull UIButtonItemBuilder addSelectableButton(@NotNull String name, Icon icon, @Nullable UIActionHandler action, int order) {
    return addEntity(new UIButtonItemBuilderImpl(UIItemType.SelectableButton, name, icon, order).setActionHandler(action));
  }

  @Override
  public UIInputBuilder.@NotNull DialogEntity<UIButtonItemBuilder> addOpenDialogSelectableButton(@NotNull String name, Icon icon,
                                                                                                 @NotNull UIActionHandler action,
                                                                                                 int order) {
    return addOpenDialogSelectableButtonInternal(name, icon, action);
  }

  @Override
  public void addOpenDialogSelectableButtonFromClassInstance(@NotNull String name, @Nullable Icon icon, @NotNull Object entityInstance, @NotNull UIActionHandler action) {
    var entityClass = entityInstance.getClass();
    ItemController.baseEntitySimpleClasses.put(entityClass.getSimpleName(), entityClass);
    ContextImpl.FIELD_FETCH_TYPE.put(entityClass.getSimpleName(), entityInstance);
    ((UIButtonItemBuilderImpl) addSelectableButton(name, icon, action))
            .setActionReferenceV2(entityClass.getSimpleName());
  }

  public UIInputBuilder.DialogEntity<UIButtonItemBuilder> addOpenDialogSelectableButtonInternal(
    @NotNull String name, @Nullable Icon icon, @Nullable UIActionHandler action) {
    var uiDialogLayoutBuilder = new UIDialogLayoutBuilderImpl(name);
    var dialogEntityBuilder = addEntity(uiDialogLayoutBuilder);
    uiDialogLayoutBuilder.setTitle(name, icon);
    var entityBuilder = ((UIButtonItemBuilderImpl) addSelectableButton(name, icon, action))
      .setActionReference(dialogEntityBuilder.getEntityID());
    return new UIInputBuilder.DialogEntity<>() {
      @Override
      public @NotNull UIInputBuilder up() {
        return UIInputBuilderImpl.this;
      }

      @Override
      public UIInputBuilder.@NotNull DialogEntity<UIButtonItemBuilder> dialogWidth(int dialogWidth) {
        uiDialogLayoutBuilder.setWidth(dialogWidth);
        return this;
      }

      @Override
      public @NotNull UIInputBuilder edit(Consumer<UIButtonItemBuilder> editHandler) {
        editHandler.accept(entityBuilder);
        return UIInputBuilderImpl.this;
      }

      @Override
      public @NotNull UIInputBuilder editDialog(Consumer<UIDialogLayoutBuilder> editDialogHandler) {
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
