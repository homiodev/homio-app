package org.touchhome.app.manager.common.v1.item;

import lombok.Getter;
import org.touchhome.app.manager.common.v1.UIBaseEntityItemBuilderImpl;
import org.touchhome.app.manager.common.v1.UIItemType;
import org.touchhome.bundle.api.ui.field.action.v1.item.UIInfoItemBuilder;

@Getter
public class UIInfoItemBuilderImpl extends UIBaseEntityItemBuilderImpl<UIInfoItemBuilder, String>
    implements UIInfoItemBuilder {

  private final InfoType infoType;

  public UIInfoItemBuilderImpl(String entityID, int order, String value, UIInfoItemBuilder.InfoType infoType) {
    super(UIItemType.Info, entityID, order, null);
    this.infoType = infoType;
    setValue(value);
  }
}
