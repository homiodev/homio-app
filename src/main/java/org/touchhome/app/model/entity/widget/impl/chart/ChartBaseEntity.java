package org.touchhome.app.model.entity.widget.impl.chart;

import lombok.Getter;
import lombok.Setter;
import org.touchhome.bundle.api.entity.widget.WidgetBaseEntity;
import org.touchhome.bundle.api.ui.field.UIField;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

@Getter
@Setter
@Entity
public abstract class ChartBaseEntity<T extends WidgetBaseEntity> extends WidgetBaseEntity<T> {

    @UIField(order = 20)
    @Enumerated(EnumType.STRING)
    private UpdateInterval updateInterval = UpdateInterval.Never;

    @UIField(order = 21)
    private String legendTitle = "Legend";

    @UIField(order = 22)
    @Enumerated(EnumType.STRING)
    private LegendPosition legendPosition = LegendPosition.right;

    @UIField(order = 23, showInContextMenu = true)
    private Boolean showLegend = Boolean.TRUE;

    @UIField(order = 24)
    private Boolean tooltipDisabled = Boolean.FALSE;

    @UIField(order = 25)
    private Boolean animations = Boolean.FALSE;

    public enum LegendPosition {
        right,
        below
    }
}
