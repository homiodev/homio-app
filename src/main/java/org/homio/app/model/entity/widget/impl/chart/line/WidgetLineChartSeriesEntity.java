package org.homio.app.model.entity.widget.impl.chart.line;

import jakarta.persistence.Entity;
import org.homio.api.entity.widget.ability.HasTimeValueSeries;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldIgnoreParent;
import org.homio.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.app.model.entity.widget.impl.chart.HasChartDataSource;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@Entity
public class WidgetLineChartSeriesEntity extends WidgetSeriesEntity<WidgetLineChartEntity>
  implements HasChartDataSource {

  @Override
  public String getDefaultName() {
    return null;
  }

  @UIField(order = 1, required = true)
  @UIFieldEntityByClassSelection(HasTimeValueSeries.class)
  @UIFieldGroup(order = 50, value = "CHART", borderColor = "#9C27B0")
  @UIFieldIgnoreParent
  public String getChartDataSource() {
    return getJsonData("chartDS");
  }

  @Override
  public void beforePersist() {
    HasChartDataSource.randomColor(this);
  }

  @Override
  protected String getSeriesPrefix() {
    return "line";
  }

  @Override
  protected void assembleMissingMandatoryFields(@NotNull Set<String> fields) {
    if (getChartDataSource().isEmpty()) {
      fields.add("chartDataSource");
    }
  }
}
