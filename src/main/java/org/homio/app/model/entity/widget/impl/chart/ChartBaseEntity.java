package org.homio.app.model.entity.widget.impl.chart;

import jakarta.persistence.Entity;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldColorPicker;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.app.model.entity.widget.WidgetEntityAndSeries;
import org.homio.app.model.entity.widget.WidgetGroup;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;

@Entity
public abstract class ChartBaseEntity<T extends WidgetEntityAndSeries, S extends WidgetSeriesEntity<T>>
  extends WidgetEntityAndSeries<T, S> implements HasLegend {

  @Override
  public WidgetGroup getGroup() {
    return WidgetGroup.Chart;
  }

  @Override
  @UIField(order = 10)
  public String getTitle() {
    return super.getName();
  }

  public void setTitle(String value) {
    super.setName(value);
  }

  @UIField(order = 1)
  @UIFieldGroup("DATA_LABELS")
  public boolean getShowDataLabels() {
    return getJsonData("sdl", false);
  }

  public void setShowDataLabels(boolean value) {
    setJsonData("sdl", value);
  }

  @UIField(order = 2)
  @UIFieldGroup("DATA_LABELS")
  @UIFieldColorPicker
  public String getDataLabelsColor() {
    return getJsonData("dlc", "#ADB5BD");
  }

  public void setDataLabelsColor(String value) {
    setJsonData("dlc", value);
  }

  @UIField(order = 100)
  public Boolean getAnimations() {
    return getJsonData("am", Boolean.FALSE);
  }

  public T setAnimations(Boolean value) {
    setJsonData("am", value);
    return (T) this;
  }

  @UIField(order = 8)
  @UIFieldGroup("CHART_UI")
  public boolean getShowChartFullScreenButton() {
    return getJsonData("sfsb", Boolean.FALSE);
  }

  public void setShowChartFullScreenButton(boolean value) {
    setJsonData("sfsb", value);
  }

    /*@UIField(order = 9)
    @UIFieldSlider(min = 10, max = 600)
    @UIFieldGroup("CHART_UI")
    public int getFetchDataFromServerInterval() {
        return getJsonData("fsfsi", 60);
    }

    public void setFetchDataFromServerInterval(int value) {
        setJsonData("fsfsi", value);
    }*/

  @UIField(order = 10)
  @UIFieldGroup("CHART_UI")
  @UIFieldColorPicker(allowThreshold = true)
  public String getChartBackground() {
    return getJsonData("chartBG", "transparent");
  }

  public void setChartBackground(String value) {
    setJsonData("chartBG", value);
  }

  @Override
  public void beforePersist() {
    setOverflow(Overflow.hidden);
  }
}
