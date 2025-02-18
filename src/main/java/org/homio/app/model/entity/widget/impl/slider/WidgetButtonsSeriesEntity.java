package org.homio.app.model.entity.widget.impl.slider;

import jakarta.persistence.Entity;
import org.homio.api.ui.field.UIField;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.app.model.entity.widget.attributes.HasIcon;
import org.homio.app.model.entity.widget.attributes.HasName;
import org.homio.app.model.entity.widget.attributes.HasSetSingleValueDataSource;
import org.homio.app.model.entity.widget.attributes.HasSingleValueDataSource;

@Entity
public class WidgetButtonsSeriesEntity
  extends WidgetSeriesEntity<WidgetButtonsEntity>
  implements HasSingleValueDataSource,
  HasSetSingleValueDataSource,
  HasIcon,
  HasName {

  @UIField(order = 3)
  public String getActiveSendValue() {
    return getJsonData("onValue", "1");
  }

  public void setActiveSendValue(String value) {
    setJsonData("onValue", value);
  }

  @Override
  protected String getSeriesPrefix() {
    return "buttons";
  }

  @Override
  public String getDefaultName() {
    return null;
  }

  @Override
  public void beforePersist() {
    HasIcon.randomColor(this);
  }
}
