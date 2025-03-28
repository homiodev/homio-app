package org.homio.app.builder.ui;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.api.entity.BaseEntity;
import org.homio.api.ui.field.action.v1.item.UIInfoItemBuilder;
import org.homio.app.utils.UIFieldUtils;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
@Accessors(chain = true)
public class UIInfoItemBuilderImpl extends UIBaseEntityItemBuilderImpl<UIInfoItemBuilder, String>
  implements UIInfoItemBuilder {

  private final InfoType infoType;
  private String link;
  private String linkType;
  private int height;

  public UIInfoItemBuilderImpl(String entityID, int order, String value, UIInfoItemBuilder.InfoType infoType) {
    super(UIItemType.Text, entityID, order);
    this.infoType = infoType;
    setValue(value);
  }

  @Override
  public UIInfoItemBuilder linkToEntity(@Nullable BaseEntity entity) {
    if (entity != null) {
      link = entity.getEntityID();
      linkType = UIFieldUtils.getClassEntityNavLink(getEntityID(), entity.getClass());
    }
    return this;
  }
}
