package org.touchhome.app.model.entity.widget.impl.button;

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
public class WidgetButtonsEntity extends WidgetBaseEntity<WidgetButtonsEntity> {
    @OrderBy("priority asc")
    @UIField(order = 30, onlyEdit = true)
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, mappedBy = "widgetButtonsEntity")
    private Set<WidgetButtonSeriesEntity> series;

    @UIField(order = 31, showInContextMenu = true)
    private Boolean vertical = Boolean.FALSE;

    @Override
    public String getImage() {
        return "fa fa-stop-circle";
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
}
