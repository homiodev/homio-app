package org.touchhome.app.model.entity.widget.impl.slider;

import lombok.Getter;
import lombok.Setter;
import org.touchhome.app.model.entity.widget.HorizontalPosition;
import org.touchhome.app.model.entity.widget.VerticalPosition;
import org.touchhome.app.model.entity.widget.impl.WidgetBaseEntity;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldType;

import javax.persistence.*;
import java.util.Set;

@Entity
@Getter
@Setter
public class WidgetSliderEntity extends WidgetBaseEntity<WidgetSliderEntity> {

    @UIField(order = 31, showInContextMenu = true)
    private Boolean vertical = Boolean.FALSE;

    @UIField(order = 33, showInContextMenu = true)
    private Boolean showValue = Boolean.TRUE;

    @UIField(order = 34)
    private Boolean thumbLabel = Boolean.TRUE;

    @UIField(order = 35, type = UIFieldType.Color)
    private String labelColor = "#e65100";

    @UIField(order = 40)
    private VerticalPosition verticalPosition = VerticalPosition.Bottom;

    @UIField(order = 40)
    private HorizontalPosition horizontalPosition = HorizontalPosition.Right;

    @OrderBy("priority asc")
    @UIField(order = 30, onlyEdit = true)
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, mappedBy = "widgetSliderEntity")
    private Set<WidgetSliderSeriesEntity> series;

    @Override
    public String getImage() {
        return "fas fa-sliders-h";
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
