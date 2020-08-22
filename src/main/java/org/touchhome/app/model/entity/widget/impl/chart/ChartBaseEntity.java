package org.touchhome.app.model.entity.widget.impl.chart;

import lombok.Getter;
import lombok.Setter;
import org.touchhome.app.model.entity.widget.impl.WidgetBaseEntity;
import org.touchhome.bundle.api.ui.field.UIField;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class ChartBaseEntity<T extends WidgetBaseEntity> extends WidgetBaseEntity<T> {

    @UIField(order = 12)
    @Enumerated(EnumType.STRING)
    private ChartPeriod chartPeriod = ChartPeriod.All;

    @UIField(order = 27, showInContextMenu = true)
    private Boolean showLegend = Boolean.TRUE;

    @UIField(order = 33, showInContextMenu = true)
    private Boolean showButtons = Boolean.FALSE;

    @UIField(order = 34)
    private Boolean animations = Boolean.FALSE;

    @UIField(order = 35)
    private String labelFormatting = "#LABEL#";

    @UIField(order = 36)
    private String valueFormatting = "#VALUE#";

    @UIField(order = 16)
    @Enumerated(EnumType.STRING)
    private UpdateInterval updateInterval = UpdateInterval.Never;

    @UIField(order = 40)
    private String legendTitle = "Legend";

    @UIField(order = 40)
    @Enumerated(EnumType.STRING)
    private LegendPosition legendPosition = LegendPosition.Right;

    public enum LegendPosition {
        Right,
        Below
    }
}
