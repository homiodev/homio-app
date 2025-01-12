package org.homio.app.model.entity.widget.impl.toggle;

import jakarta.persistence.Entity;
import org.homio.api.ui.UI;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.app.model.entity.widget.attributes.HasIcon;
import org.homio.app.model.entity.widget.attributes.HasName;
import org.homio.app.model.entity.widget.attributes.HasSingleValueDataSource;

@Entity
public class WidgetToggleSeriesEntity extends WidgetSeriesEntity<WidgetToggleEntity>
  implements HasSingleValueDataSource, HasIcon, HasName, HasToggle {

  @Override
  protected String getSeriesPrefix() {
    return "toggle";
  }

  @Override
  public String getDefaultName() {
    return null;
  }

  @Override
  public void beforePersist() {
    HasIcon.randomColor(this);
    if (!getJsonData().has("color")) {
      setToggleColor(UI.Color.random());
    }
    if (getOnValues().isEmpty()) {
      setOnValues("true%s1".formatted(LIST_DELIMITER));
    }
  }
}
