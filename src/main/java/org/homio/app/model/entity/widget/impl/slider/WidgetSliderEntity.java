package org.homio.app.model.entity.widget.impl.slider;

import jakarta.persistence.Entity;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldLayout;
import org.homio.app.model.entity.widget.UIFieldOptionFontSize;
import org.homio.app.model.entity.widget.WidgetEntityAndSeries;
import org.homio.app.model.entity.widget.attributes.HasLayout;
import org.homio.app.model.entity.widget.attributes.HasMargin;
import org.homio.app.model.entity.widget.attributes.HasName;
import org.jetbrains.annotations.NotNull;

@Entity
public class WidgetSliderEntity
  extends WidgetEntityAndSeries<WidgetSliderEntity, WidgetSliderSeriesEntity>
  implements HasLayout, HasName, HasMargin {

  @UIField(order = 1)
  @UIFieldGroup(order = 3, value = "NAME")
  @UIFieldOptionFontSize
  public String getName() {
    return super.getName();
  }

  @UIField(order = 2)
  @UIFieldGroup("SLIDER")
  public Boolean isVertical() {
    return getJsonData("vt", Boolean.FALSE);
  }

  @UIField(order = 3)
  @UIFieldGroup("SLIDER")
  public Boolean isVerticalInvert() {
    return getJsonData("vti", Boolean.FALSE);
  }

  @UIField(order = 4)
  @UIFieldGroup("SLIDER")
  public Boolean getThumbLabel() {
    return getJsonData("tl", Boolean.TRUE);
  }

  public void setThumbLabel(Boolean value) {
    setJsonData("tl", value);
  }

  @UIField(order = 6)
  @UIFieldGroup("SLIDER")
  public Boolean isUpdateOnMove() {
    return getJsonData("uom", Boolean.FALSE);
  }

  @Override
  public @NotNull String getImage() {
    return "fas fa-sliders-h";
  }

  @Override
  protected @NotNull String getWidgetPrefix() {
    return "slider";
  }

  public void setVertical(Boolean value) {
    setJsonData("vt", value);
  }

  public void setVerticalInvert(Boolean value) {
    setJsonData("vti", value);
  }

  public void setUpdateOnMove(Boolean value) {
    setJsonData("uom", value);
  }

  @Override
  @UIField(order = 50)
  @UIFieldLayout(options = {"name", "value", "icon", "slider"})
  public String getLayout() {
    return getJsonData("layout", getDefaultLayout());
  }

  @Override
  public String getDefaultName() {
    return null;
  }

  private String getDefaultLayout() {
    return UIFieldLayout.LayoutBuilder
      .builder(15, 20, 50, 15)
      .addRow(rb ->
        rb.addCol("icon", UIFieldLayout.HorizontalAlign.center)
          .addCol("name", UIFieldLayout.HorizontalAlign.left)
          .addCol("slider", UIFieldLayout.HorizontalAlign.center)
          .addCol("value", UIFieldLayout.HorizontalAlign.center))
      .build();
  }

  @Override
  public void beforePersist() {
    setOverflow(Overflow.hidden);
  }
}
