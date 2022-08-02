package org.touchhome.app.model.entity.widget.impl.chart.line;

import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;

import javax.persistence.Entity;

@Entity
public class WidgetMiniCardChartSeriesEntity extends WidgetSeriesEntity<WidgetMiniCardChartEntity> {

    public static final String PREFIX = "wgslcs_";

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public String getDataSource() {
        return getJsonData("ds");
    }
}
