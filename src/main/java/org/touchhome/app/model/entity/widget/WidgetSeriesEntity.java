package org.touchhome.app.model.entity.widget;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Set;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.touchhome.bundle.api.converter.JSONObjectConverter;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.ui.field.selection.dynamic.HasDynamicParameterFields;

@Setter
@Getter
@Accessors(chain = true)
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public abstract class WidgetSeriesEntity<T extends WidgetBaseEntityAndSeries> extends BaseEntity<WidgetSeriesEntity>
    implements HasDynamicParameterFields, Comparable<WidgetSeriesEntity>, HasJsonData {

  private int priority;

  @JsonIgnore
  @ManyToOne(fetch = FetchType.LAZY, targetEntity = WidgetBaseEntityAndSeries.class)
  private T widgetEntity;

  @Lob
  @Column(length = 1000_000)
  @Convert(converter = JSONObjectConverter.class)
  private JSONObject jsonData = new JSONObject();

  @Override
  public JSONObject getDynamicParameterFieldsHolder() {
    return getJsonData().optJSONObject("dsp");
  }

  @Override
  public void setDynamicParameterFieldsHolder(JSONObject value) {
    setJsonData("dsp", value);
  }

  @Override
  public void getAllRelatedEntities(Set<BaseEntity> set) {
    set.add(widgetEntity);
  }

  @Override
  public int compareTo(@NotNull WidgetSeriesEntity entity) {
    return Integer.compare(this.priority, entity.priority);
  }
}
