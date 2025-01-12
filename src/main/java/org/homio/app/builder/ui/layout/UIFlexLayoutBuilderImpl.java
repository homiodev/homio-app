package org.homio.app.builder.ui.layout;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.homio.api.ui.UIActionHandler;
import org.homio.api.ui.field.action.v1.UIEntityBuilder;
import org.homio.api.ui.field.action.v1.UIInputEntity;
import org.homio.api.ui.field.action.v1.layout.UIFlexLayoutBuilder;
import org.homio.app.builder.ui.UIBaseEntityItemBuilderImpl;
import org.homio.app.builder.ui.UIBaseLayoutBuilderImpl;
import org.homio.app.builder.ui.UIItemType;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public class UIFlexLayoutBuilderImpl extends UIBaseLayoutBuilderImpl
  implements UIFlexLayoutBuilder, UIInputEntity {

  private final String entityID;
  private final String itemType = UIItemType.Flex.name();
  private final int order;

  private String title;
  private String titleColor;

  @Override
  public UIInputEntity buildEntity() {
    return this;
  }

  // for serialization!
  public List<UIInputEntity> getChildren() {
    return getUiEntityBuilders(false).stream()
      .map(UIEntityBuilder::buildEntity)
      .sorted(Comparator.comparingInt(UIInputEntity::getOrder))
      .collect(Collectors.toList());
  }

  @Override
  public UIFlexLayoutBuilder setTitle(String title, @Nullable String titleColor) {
    this.title = title;
    this.titleColor = titleColor;
    return this;
  }

  @Override
  public UIActionHandler findAction(String key) {
    for (UIEntityBuilder entityBuilder : getUiEntityBuilders(true)) {
      if (entityBuilder.getEntityID().equals(key)
          && entityBuilder instanceof UIBaseEntityItemBuilderImpl) {
        return ((UIBaseEntityItemBuilderImpl<?, ?>) entityBuilder).getActionHandler();
      }
    }
    return null;
  }
}
