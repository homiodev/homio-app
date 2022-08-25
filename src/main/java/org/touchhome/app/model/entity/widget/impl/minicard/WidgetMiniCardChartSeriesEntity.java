package org.touchhome.app.model.entity.widget.impl.minicard;

import org.touchhome.app.model.entity.widget.impl.HasChartDataSource;
import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.ui.field.*;

import javax.persistence.Entity;

@Entity
public class WidgetMiniCardChartSeriesEntity extends WidgetSeriesEntity<WidgetMiniCardChartEntity>
        implements HasChartDataSource<WidgetMiniCardChartEntity> {

    public static final String PREFIX = "wgslcs_";

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    @UIFieldIgnore
    public String getName() {
        return super.getName();
    }
}
