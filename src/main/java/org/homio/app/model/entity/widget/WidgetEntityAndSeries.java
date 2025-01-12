package org.homio.app.model.entity.widget;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.homio.api.entity.validation.MaxItems;
import org.homio.api.exception.ServerException;
import org.homio.api.ui.field.UIField;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public abstract class WidgetEntityAndSeries<T extends WidgetEntityAndSeries, S extends WidgetSeriesEntity<T>>
  extends WidgetEntity<T> {

  @Getter
  @Setter
  @OrderBy("priority asc")
  @UIField(order = 30, hideInView = true)
  @MaxItems(10)
  @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "widgetEntity", targetEntity = WidgetSeriesEntity.class)
  private Set<S> series;

  @Override
  public void validate() {
    if (getWidgetTabEntity() == null) {
      throw new ServerException("ERROR.WIDGET_NO_TAB");
    }
  }

  @Override
  protected void assembleMissingMandatoryFields(@NotNull Set<String> fields) {
    if (series == null || series.isEmpty()) {
      fields.add("series");
    }
  }
}
