package org.homio.app.notification;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pivovarit.function.ThrowingBiFunction;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.homio.api.ContextUI.NotificationBlockBuilder;
import org.homio.api.ContextUI.NotificationInfoLineBuilder;
import org.homio.api.entity.BaseEntity;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.model.Status;
import org.homio.api.ui.UIActionHandler;
import org.homio.api.ui.field.action.v1.UIInputEntity;
import org.homio.api.ui.field.action.v1.item.UIInfoItemBuilder;
import org.homio.api.ui.field.action.v1.layout.UILayoutBuilder;
import org.homio.app.builder.ui.UIButtonItemBuilderImpl;
import org.homio.app.builder.ui.UICheckboxItemBuilderImpl;
import org.homio.app.builder.ui.UIInfoItemBuilderImpl;
import org.homio.app.builder.ui.UIItemType;
import org.homio.app.builder.ui.layout.UIStickyDialogItemBuilderImpl;
import org.homio.app.utils.UIFieldUtils;
import org.homio.hquery.ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Getter
@Setter
@RequiredArgsConstructor
public class NotificationBlock {

  private final @NotNull String entityID;
  private final @NotNull String name;
  private final @Nullable Icon icon;
  @JsonIgnore
  private final @NotNull Map<String, Info> infoItemMap = new ConcurrentHashMap<>();
  private @Nullable Consumer<NotificationBlockBuilder> refreshBlockBuilder;
  private @Nullable String nameColor;
  private @Nullable String version;
  private @Nullable Status status;
  private @Nullable String statusColor;
  private @Nullable Boolean updating;
  private @Nullable List<OptionModel> versions;
  private @Nullable String link;
  private @Nullable String linkType;
  private @Nullable String borderColor;
  private @Nullable Collection<UIInputEntity> contextMenuActions;
  @JsonIgnore
  private @NotNull Map<String, Collection<UIInputEntity>> keyValueActions = new HashMap<>();

  @JsonIgnore
  private @Nullable ThrowingBiFunction<ProgressBar, String, ActionResponseModel, Exception> updateHandler;

  @JsonIgnore
  private Runnable fireOnFetchHandler;

  @JsonIgnore
  private String email;

  public Collection<UIInputEntity> getActions() {
    if (keyValueActions.isEmpty()) {
      return null;
    }
    Collection<UIInputEntity> actions = new ArrayList<>();
    for (Collection<UIInputEntity> item : keyValueActions.values()) {
      actions.addAll(item);
    }
    return actions;
  }

  public Collection<Info> getInfoItems() {
    return infoItemMap.values();
  }

  public Info addInfoLine(@NotNull String key, @Nullable String info, @Nullable Icon icon) {
    Info infoItem = new Info(key, info, icon);
    infoItemMap.put(key, infoItem);
    return infoItem;
  }

  public void setUpdatable(@NotNull ThrowingBiFunction<ProgressBar, String, ActionResponseModel, Exception> updateHandler,
                           @NotNull List<OptionModel> versions) {
    this.versions = versions;
    this.updateHandler = updateHandler;
  }

  public boolean remove(String key) {
    return infoItemMap.remove(key) != null;
  }

  @Getter
  public static class Info implements NotificationInfoLineBuilder {

    private final String key;
    private final Icon icon;
    private String info;
    private String textColor;

    private String link;
    private String linkType;

    private Icon settingIcon;
    private UIInputEntity settingButton;

    private String tooltip;
    private UIInputEntity rightAction;

    public Info(String key, String info, Icon icon) {
      this.key = key;
      this.info = info;
      this.icon = icon;
    }

    @Override
    public @NotNull NotificationInfoLineBuilder setTextColor(@Nullable String color) {
      this.textColor = color;
      return this;
    }

    @Override
    public @NotNull NotificationInfoLineBuilder setRightText(@Nullable String text, @Nullable Icon icon, @Nullable String color) {
      UIInfoItemBuilderImpl builder = new UIInfoItemBuilderImpl("i", 0, text, UIInfoItemBuilder.InfoType.Text);
      builder.setIcon(icon);
      builder.setColor(color);
      builder.setHeight(20);
      this.rightAction = builder.buildEntity();
      return this;
    }

    @Override
    public @NotNull NotificationInfoLineBuilder setTooltip(@Nullable String tooltip) {
      this.tooltip = tooltip;
      return this;
    }

    @Override
    public @NotNull NotificationButtonBuilder setRightButton(@Nullable Icon buttonIcon, @Nullable String buttonText,
                                                             @Nullable UIActionHandler handler) {
      UIButtonItemBuilderImpl builder = new UIButtonItemBuilderImpl(UIItemType.Button, this.key, buttonIcon, 0, handler);
      builder.setText(Objects.toString(buttonText, ""));
      builder.setHeight(20);
      this.rightAction = builder.buildEntity();
      return new NotificationButtonBuilder() {
        @Override
        public NotificationButtonBuilder setConfirmMessage(@Nullable String value) {
          ((UIButtonItemBuilderImpl) rightAction).setConfirmMessage(value);
          return this;
        }

        @Override
        public NotificationButtonBuilder setDialogBackgroundColor(@Nullable String value) {
          ((UIButtonItemBuilderImpl) rightAction).setConfirmMessageDialogColor(value);
          return this;
        }

        @Override
        public NotificationButtonBuilder setDialogTitle(@Nullable String value) {
          ((UIButtonItemBuilderImpl) rightAction).setConfirmMessageDialogTitle(value);
          return this;
        }

        @Override
        public NotificationButtonBuilder setDialogIcon(@Nullable Icon icon) {
          ((UIButtonItemBuilderImpl) rightAction).setConfirmMessageDialogIcon(icon);
          return this;
        }
      };
    }

    @Override
    public @NotNull NotificationInfoLineBuilder setRightToggleButton(boolean value, @Nullable UIActionHandler handler) {
      UICheckboxItemBuilderImpl builder = new UICheckboxItemBuilderImpl(this.key, 0, handler, value);
      this.rightAction = builder.buildEntity();
      return this;
    }

    @Override
    public @NotNull NotificationInfoLineBuilder setRightSettingsButton(@NotNull Icon buttonIcon,
                                                                       @NotNull Consumer<UILayoutBuilder> assembler) {
      UIStickyDialogItemBuilderImpl builder = new UIStickyDialogItemBuilderImpl("actions");
      assembler.accept(builder);
      settingIcon = buttonIcon;
      settingButton = builder.buildEntity();
      return this;
    }

    @Override
    public @NotNull NotificationInfoLineBuilder setAsLink(@NotNull BaseEntity entity) {
      link = entity.getEntityID();
      linkType = UIFieldUtils.getClassEntityNavLink(entity.getTitle(), entity.getClass());
      return this;
    }

    public void updateTextInfo(String info) {
      this.info = info;
    }
  }
}
