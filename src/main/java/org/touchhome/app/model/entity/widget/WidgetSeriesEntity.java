package org.touchhome.app.model.entity.widget;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Set;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.touchhome.bundle.api.converter.JSONConverter;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.model.JSON;
import org.touchhome.bundle.api.ui.field.selection.dynamic.HasDynamicParameterFields;

@Setter
@Getter
@Accessors(chain = true)
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public abstract class WidgetSeriesEntity<T extends WidgetBaseEntityAndSeries>
    extends BaseEntity<WidgetSeriesEntity>
    implements HasDynamicParameterFields, HasJsonData {

    private int priority;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, targetEntity = WidgetBaseEntityAndSeries.class)
    private T widgetEntity;

    @Column(length = 65535)
    @Convert(converter = JSONConverter.class)
    private JSON jsonData = new JSON();

    @Override
    public JSONObject getDynamicParameterFieldsHolder() {
        return getJsonData().optJSONObject("dsp");
    }

    @Override
    public void setDynamicParameterFieldsHolder(JSON value) {
        setJsonData("dsp", value);
    }

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
