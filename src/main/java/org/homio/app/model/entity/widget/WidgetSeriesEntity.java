package org.homio.app.model.entity.widget;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.api.converter.JSONConverter;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.HasJsonData;
import org.homio.api.model.JSON;
import org.homio.api.ui.field.selection.dynamic.HasDynamicParameterFields;
import org.homio.app.manager.common.ContextImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@Setter
@Getter
@Accessors(chain = true)
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Table(name = "widget_series")
public abstract class WidgetSeriesEntity<T extends WidgetEntityAndSeries>
  extends BaseEntity implements HasDynamicParameterFields, HasJsonData {

  private static final String PREFIX = "series_";
  private int priority;
  @JsonIgnore
  @ManyToOne(fetch = FetchType.LAZY, targetEntity = WidgetEntityAndSeries.class)
  private T widgetEntity;
  @Column(length = 65535)
  @Convert(converter = JSONConverter.class)
  private JSON jsonData = new JSON();

  @Override
  public final @NotNull String getEntityPrefix() {
    return PREFIX + getSeriesPrefix() + "_";
  }

  protected abstract String getSeriesPrefix();

  @Override
  public void getAllRelatedEntities(Set<BaseEntity> set) {
    set.add(widgetEntity);
  }

  @Override
  public int compareTo(@NotNull BaseEntity o) {
    if (o instanceof WidgetSeriesEntity) {
      return Integer.compare(this.priority, ((WidgetSeriesEntity<?>) o).priority);
    }
    return super.compareTo(o);
  }

  @Override
  protected long getChildEntityHashCode() {
    return 0;
  }

  @Override
  public void afterUpdate() {
    removeEvents();
  }

  @Override
  public void afterDelete() {
    removeEvents();
  }

  private void removeEvents() {
    ((ContextImpl) context()).event().removeEvents(widgetEntity.getEntityID() + getEntityID());
    ((ContextImpl) context()).event().removeEvents(getEntityID());
  }
}
