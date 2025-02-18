package org.homio.app.model.entity.widget.impl.simple;

import jakarta.persistence.Entity;
import org.homio.app.model.entity.widget.WidgetEntity;
import org.homio.app.model.entity.widget.WidgetGroup;
import org.homio.app.model.entity.widget.attributes.HasActionOnClick;
import org.homio.app.model.entity.widget.attributes.HasAlign;
import org.homio.app.model.entity.widget.attributes.HasIcon;
import org.homio.app.model.entity.widget.attributes.HasMargin;
import org.homio.app.model.entity.widget.attributes.HasSingleValueDataSource;
import org.homio.app.model.entity.widget.attributes.HasValueConverter;
import org.homio.app.model.entity.widget.attributes.HasValueTemplate;
import org.jetbrains.annotations.NotNull;

@Entity
public class WidgetSimpleValueEntity extends WidgetEntity<WidgetSimpleValueEntity>
  implements
  HasIcon,
  HasActionOnClick,
  HasSingleValueDataSource,
  HasValueTemplate,
  HasMargin,
  HasAlign,
  HasValueConverter {

  @Override
  public WidgetGroup getGroup() {
    return WidgetGroup.Simple;
  }

  @Override
  public @NotNull String getImage() {
    return "fab fa-pix";
  }

  @Override
  protected @NotNull String getWidgetPrefix() {
    return "sim-val";
  }

  @Override
  public String getDefaultName() {
    return null;
  }
}
