package org.homio.app.model.entity.widget;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.ManyToOne;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.api.converter.JSONConverter;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.HasJsonData;
import org.homio.api.model.JSON;
import org.homio.api.ui.field.selection.dynamic.HasDynamicParameterFields;
import org.jetbrains.annotations.NotNull;

@Setter
@Getter
@Accessors(chain = true)
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public abstract class WidgetSeriesEntity<T extends WidgetBaseEntityAndSeries>
    extends BaseEntity<WidgetSeriesEntity>
    implements HasDynamicParameterFields, HasJsonData {

    private int priority;

    private static final String PREFIX = "series_";

    @Override
    public final @NotNull String getEntityPrefix() {
        return PREFIX + getSeriesPrefix() + "_";
    }

    protected abstract String getSeriesPrefix();

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, targetEntity = WidgetBaseEntityAndSeries.class)
    private T widgetEntity;

    @Column(length = 65535)
    @Convert(converter = JSONConverter.class)
    private JSON jsonData = new JSON();

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
}
