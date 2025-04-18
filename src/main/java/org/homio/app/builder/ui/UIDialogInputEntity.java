package org.homio.app.builder.ui;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.homio.api.ui.field.action.v1.UIInputEntity;

import java.util.List;

@Getter
@AllArgsConstructor
public class UIDialogInputEntity implements UIInputEntity {

  private final String entityID;
  private final int order;
  private final String itemType;
  private final String title;
  private final String icon;
  private final String iconColor;
  private final String style;
  private final Integer width;
  private final List<UIInputEntity> children;
}
