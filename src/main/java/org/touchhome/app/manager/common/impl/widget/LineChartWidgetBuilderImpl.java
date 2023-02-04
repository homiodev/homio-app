package org.touchhome.app.manager.common.impl.widget;

import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.model.entity.widget.impl.chart.line.WidgetLineChartEntity;
import org.touchhome.bundle.api.EntityContextWidget.LineChartWidgetBuilder;

public final class LineChartWidgetBuilderImpl extends WidgetBaseBuilderImpl<LineChartWidgetBuilder, WidgetLineChartEntity>
    implements LineChartWidgetBuilder {

    public LineChartWidgetBuilderImpl(WidgetLineChartEntity widget, EntityContextImpl entityContext) {
        super(widget, entityContext);
    }

    @Override
    public LineChartWidgetBuilder showAxisX(boolean on) {
        widget.setShowAxisX(on);
        return this;
    }

    @Override
    public LineChartWidgetBuilder showAxisY(boolean on) {
        widget.setShowAxisY(on);
        return this;
    }

    @Override
    public LineChartWidgetBuilder axisLabelX(String name) {
        widget.setAxisLabelX(name);
        return this;
    }

    @Override
    public LineChartWidgetBuilder axisLabelY(String name) {
        widget.setAxisLabelY(name);
        return this;
    }
}
