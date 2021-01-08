package org.touchhome.app.model.entity.widget.impl.display;

import lombok.Getter;
import lombok.Setter;
import org.touchhome.bundle.api.entity.widget.WidgetBaseEntity;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.ui.field.UIField;

import javax.persistence.*;
import java.util.Set;

@Entity
@Getter
@Setter
public class WidgetDisplayEntity extends WidgetBaseEntity<WidgetDisplayEntity> {

    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, mappedBy = "widgetDisplayEntity")
    @UIField(order = 30, onlyEdit = true)
    @OrderBy("priority asc")
    private Set<WidgetDisplaySeriesEntity> series;

    @Override
    public String getImage() {
        return "fas fa-tv";
    }

    @Override
    public boolean updateRelations(EntityContext entityContext) {
        return validateSeries(series, entityContext);
    }
}
