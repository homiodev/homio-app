package org.homio.app.model.entity.widget.impl.gauge;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import org.homio.api.ContextWidget;
import org.homio.api.entity.widget.ability.HasGetStatusValue;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldColorPicker;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.api.ui.field.UIFieldIgnoreParent;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.UIFieldStringTemplate;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;
import org.homio.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.homio.app.model.entity.widget.UIEditReloadWidget;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.app.model.entity.widget.attributes.HasIcon;
import org.homio.app.model.entity.widget.attributes.HasSingleValueDataSource;
import org.homio.app.model.entity.widget.attributes.HasValueConverter;
import org.homio.app.model.entity.widget.attributes.HasValueTemplate;

import static org.homio.api.ContextWidget.GaugeSeriesType;
import static org.homio.api.ContextWidget.GaugeLineWidgetSeriesBuilder.GaugeLineType;

@Entity
public class WidgetGaugeSeriesEntity extends WidgetSeriesEntity<WidgetGaugeEntity>
  implements HasSingleValueDataSource, HasIcon, HasValueTemplate, HasValueConverter {

  @Override
  public String getDefaultName() {
    return null;
  }

  @Override
  protected String getSeriesPrefix() {
    return "gauge";
  }

  @Override
  public void beforePersist() {
    HasIcon.randomColor(this);
  }

  @UIField(order = 1)
  public GaugeSeriesType getSeriesType() {
    return getJsonDataEnum("ugv", GaugeSeriesType.CustomValue);
  }

  public void setSeriesType(GaugeSeriesType value) {
    setJsonDataEnum("ugv", value);
  }

  @UIField(order = 20)
  @UIFieldGroup("VALUE")
  @UIFieldShowOnCondition("return context.get('seriesType') == 'Line'")
  public boolean getVerticalLine() {
    return getJsonData("uvl", false);
  }

  public void setVerticalLine(boolean value) {
    setJsonData("uvl", value);
  }

  @UIField(order = 21)
  @UIFieldGroup("VALUE")
  @UIFieldShowOnCondition("return context.get('seriesType') == 'Line'")
  public GaugeLineType getLineType() {
    return getJsonDataEnum("lt", GaugeLineType.RectLine);
  }

  public void setLineType(GaugeLineType value) {
    setJsonDataEnum("lt", value);
  }

  @UIField(order = 22)
  @UIFieldGroup("VALUE")
  @UIFieldShowOnCondition("return context.get('seriesType') == 'Line'")
  @UIFieldSlider(min = 10, max = 100)
  public int getLineWidth() {
    return getJsonData("lw", 50);
  }

  public void setLineWidth(int value) {
    setJsonData("lw", value);
  }

  @UIField(order = 23)
  @UIFieldGroup("VALUE")
  @UIFieldShowOnCondition("return context.get('seriesType') == 'Line'")
  @UIFieldSlider(min = 1, max = 30)
  public int getLineThickness() {
    return getJsonData("lth", 2);
  }

  public void setLineThickness(int value) {
    setJsonData("lth", value);
  }

  @UIField(order = 24)
  @UIFieldGroup("VALUE")
  @UIFieldShowOnCondition("return context.get('seriesType') == 'Line'")
  @UIFieldColorPicker
  public String getLineColor() {
    return getJsonData("lc", "#AAAAAA");
  }

  public void setLineColor(String value) {
    setJsonData("lc", value);
  }

  @UIField(order = 25)
  @UIFieldGroup("VALUE")
  @UIFieldShowOnCondition("return context.get('seriesType') == 'Line'")
  @UIFieldSlider(min = 0, max = 100)
  public int getLineBorderRadius() {
    return getJsonData("lbr", 0);
  }

  public void setLineBorderRadius(int value) {
    setJsonData("lbr", value);
  }

  @Override
  @UIField(order = 10)
  @UIFieldEntityByClassSelection(HasGetStatusValue.class)
  @UIFieldGroup(value = "VALUE", order = 1)
  @UIEditReloadWidget
  @UIFieldShowOnCondition("return context.get('seriesType') == 'CustomValue'")
  @UIFieldIgnoreParent
  public String getValueDataSource() {
    return getJsonData("vds");
  }

  @UIField(order = 1)
  @UIFieldSlider(min = -50, max = 50)
  @UIFieldGroup(value = "POSITION", order = 20, borderColor = "#2E9E48")
  public int getVerticalPosition() {
    return getJsonData("pos", 0);
  }

  public void setVerticalPosition(int value) {
    setJsonData("pos", value);
  }

  @UIField(order = 2)
  @UIFieldSlider(min = -50, max = 50)
  @UIFieldGroup("POSITION")
  public int getHorizontalPosition() {
    return getJsonData("shift", 0);
  }

  public void setHorizontalPosition(int value) {
    setJsonData("shift", value);
  }

  @Override
  @UIFieldShowOnCondition("return context.get('seriesType') != 'Line'")
  public String getIcon() {
    return HasIcon.super.getIcon();
  }

  @Override
  @UIFieldShowOnCondition("return context.get('seriesType') != 'Line'")
  public String getIconColor() {
    return HasIcon.super.getIconColor();
  }

  @Override
  @UIFieldShowOnCondition("return context.get('seriesType') != 'Line'")
  public UIFieldStringTemplate.StringTemplate getValueTemplate() {
    return HasValueTemplate.super.getValueTemplate();
  }

  @Override
  @UIField(order = 1)
  public String getName() {
    return super.getName();
  }

  @Override
  @JsonIgnore
  @UIFieldIgnore
  public int getValueConverterInterval() {
    return HasValueConverter.super.getValueConverterInterval();
  }

  @Override
  @UIFieldShowOnCondition("return context.get('seriesType') != 'Line'")
  public String getValueConverter() {
    return HasValueConverter.super.getValueConverter();
  }
}
