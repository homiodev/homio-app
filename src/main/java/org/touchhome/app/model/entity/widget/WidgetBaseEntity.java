package org.touchhome.app.model.entity.widget;

import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.json.JSONObject;
import org.touchhome.app.model.entity.widget.impl.DataSourceUtil;
import org.touchhome.app.model.entity.widget.impl.HasSingleValueDataSource;
import org.touchhome.app.model.entity.widget.impl.chart.HasChartDataSource;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.converter.JSONObjectConverter;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.model.HasPosition;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;

@Getter
@Setter
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@UISidebarMenu(icon = "fas fa-tachometer-alt", bg = "#107d6b", overridePath = "widgets")
@Accessors(chain = true)
@NoArgsConstructor
public abstract class WidgetBaseEntity<T extends WidgetBaseEntity> extends BaseEntity<T>
    implements HasPosition<WidgetBaseEntity>, HasJsonData {

  @ManyToOne(fetch = FetchType.LAZY)
  private WidgetTabEntity widgetTabEntity;
  @Getter
  private int xb = 0;
  @Getter
  private int yb = 0;
  @Getter
  private int bw = 1;
  @Getter
  private int bh = 1;
  @Lob
  @Column(length = 1000_000)
  @Convert(converter = JSONObjectConverter.class)
  private JSONObject jsonData = new JSONObject();
  @Lob
  @Column(length = 100_000)
  private byte[] backgroundImage;

  /**
   * Uses for grouping widget by type on UI
   */
  public WidgetGroup getGroup() {
    return null;
  }

  @UIField(order = 1000)
  @UIFieldGroup(value = "UI", order = 10, borderColor = "#009688")
  public boolean isAdjustFontSize() {
    return getJsonData("adjfs", Boolean.TRUE);
  }

  public void setAdjustFontSize(boolean value) {
    setJsonData("adjfs", value);
  }

  public String getFieldFetchType() {
    return getJsonData("fieldFetchType", (String) null);
  }

  public T setFieldFetchType(String value) {
    jsonData.put("fieldFetchType", value);
    return (T) this;
  }

  @Override
  @UIFieldIgnore
  public String getName() {
    return super.getName();
  }

  public abstract String getImage();

  public boolean updateRelations(EntityContext entityContext) {
    return false;
  }

  protected boolean invalidateWrongEntity(EntityContext entityContext, Object item) {
    boolean updated = false;
    if (item instanceof HasSingleValueDataSource) {
      HasSingleValueDataSource source = (HasSingleValueDataSource) item;
      String valueDataSource = source.getValueDataSource();
      if (isNotEmpty(valueDataSource) && isEntityNotExists(entityContext, valueDataSource)) {
        updated = true;
        source.setValueDataSource(null);
      }

      if (isNotEmpty(source.getSetValueDataSource()) &&
          isEntityNotExists(entityContext, source.getSetValueDataSource())) {
        updated = true;
        source.setSetValueDataSource(null);
      }
    }
    if (item instanceof HasChartDataSource) {
      HasChartDataSource source = (HasChartDataSource) item;
      if (isNotEmpty(source.getChartDataSource()) && isEntityNotExists(entityContext, source.getChartDataSource())) {
        updated = true;
        ((HasChartDataSource) item).setChartDataSource(null);
      }
    }
    return updated;
  }

  private boolean isEntityNotExists(EntityContext entityContext, String source) {
    DataSourceUtil.DataSourceContext dsContext = DataSourceUtil.getSource(entityContext, source);
    return dsContext.getSource() == null;
  }

  @UIField(order = 2)
  @UIFieldGroup("Update")
  public Boolean getListenSourceUpdates() {
    return getJsonData("lsu", Boolean.TRUE);
  }

  public T setListenSourceUpdates(Boolean value) {
    setJsonData("lsu", value);
    return (T) this;
  }

  @UIField(order = 10)
  @UIFieldGroup("Update")
  public Boolean getShowLastUpdateTimer() {
    return getJsonData("slut", Boolean.FALSE);
  }

  public T setShowLastUpdateTimer(Boolean value) {
    setJsonData("slut", value);
    return (T) this;
  }

  @UIField(order = 21)
  @UIFieldGroup("UI")
  @UIFieldColorPicker(allowThreshold = true)
  public String getBackground() {
    return getJsonData("bg", "transparent");
  }

  public void setBackground(String value) {
    setJsonData("bg", value);
  }
}
