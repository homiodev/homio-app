package org.touchhome.app.model.entity.widget.impl.toggle;

import lombok.Getter;
import lombok.Setter;
import org.touchhome.app.model.entity.widget.impl.WidgetBaseEntity;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.ui.field.UIField;

import javax.persistence.*;
import java.util.Set;

@Entity
@Getter
@Setter
public class WidgetToggleEntity extends WidgetBaseEntity<WidgetToggleEntity> {

    @OrderBy("priority asc")
    @UIField(order = 30, onlyEdit = true)
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, mappedBy = "widgetToggleEntity")
    private Set<WidgetToggleSeriesEntity> series;

    @UIField(order = 32)
    @Enumerated(EnumType.STRING)
    private ToggleType toggleType = ToggleType.Slide;

    @Override
    public String getImage() {
        return "fas fa-toggle-on";
    }

    @Override
    public boolean updateRelations(EntityContext entityContext) {
        return validateSeries(series, entityContext);
    }

    @Override
    public void copy() {
        super.copy();
        series.forEach(BaseEntity::copy);
    }

    enum ToggleType {
        Regular, Slide
    }
}
