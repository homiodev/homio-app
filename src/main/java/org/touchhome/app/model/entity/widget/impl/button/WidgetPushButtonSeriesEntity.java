package org.touchhome.app.model.entity.widget.impl.button;

import javax.persistence.Entity;
import org.touchhome.app.model.entity.widget.UIEditReloadWidget;
import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.HasIcon;
import org.touchhome.app.model.entity.widget.impl.HasName;
import org.touchhome.app.model.entity.widget.impl.HasSingleValueDataSource;
import org.touchhome.app.model.entity.widget.impl.HasStyle;
import org.touchhome.app.model.entity.widget.impl.HasValueConverter;
import org.touchhome.app.model.entity.widget.impl.HasValueTemplate;
import org.touchhome.app.model.entity.widget.impl.chart.HasChartDataSource;
import org.touchhome.bundle.api.entity.widget.ability.HasAggregateValueFromSeries;
import org.touchhome.bundle.api.entity.widget.ability.HasGetStatusValue;
import org.touchhome.bundle.api.entity.widget.ability.HasSetStatusValue;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.selection.UIFieldBeanSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;

@Entity
public class WidgetPushButtonSeriesEntity extends WidgetSeriesEntity<WidgetPushButtonEntity>
    implements HasChartDataSource,
    HasSingleValueDataSource,
    HasIcon,
    HasValueTemplate,
    HasName,
    HasValueConverter,
    HasStyle {

    public static final String PREFIX = "wgsbs_";

    @Override
    @UIField(order = 1, required = true, label = "widget.pushValueDataSource")
    @UIFieldEntityByClassSelection(HasSetStatusValue.class)
    @UIFieldBeanSelection(value = HasSetStatusValue.class, lazyLoading = true)
    @UIFieldGroup(value = "Action Data source", order = 1)
    public String getSetValueDataSource() {
        return HasSingleValueDataSource.super.getSetValueDataSource();
    }

    @Override
    @UIField(order = 10) // Override and remove require
    @UIFieldBeanSelection(value = HasGetStatusValue.class, lazyLoading = true)
    @UIFieldBeanSelection(value = HasAggregateValueFromSeries.class, lazyLoading = true)
    @UIFieldEntityByClassSelection(HasGetStatusValue.class)
    @UIFieldEntityByClassSelection(HasAggregateValueFromSeries.class)
    @UIFieldGroup(value = "Value", order = 10)
    @UIEditReloadWidget
    public String getValueDataSource() {
        return HasSingleValueDataSource.super.getValueDataSource();
    }

    @UIField(order = 2, required = true)
    @UIFieldGroup(value = "Action Data source")
    public String getValueToPush() {
        return getJsonData("valToPush");
    }

    public void setValueToPush(String value) {
        setJsonData("valToPush", value);
    }

    @UIField(order = 1, isRevert = true)
    @UIFieldGroup("UI")
    @UIFieldColorPicker(allowThreshold = true, animateColorCondition = true)
    public String getButtonColor() {
        return getJsonData("btnClr", UI.Color.WHITE);
    }

    public WidgetPushButtonSeriesEntity setButtonColor(String value) {
        setJsonData("btnClr", value);
        return this;
    }

    @UIField(order = 2)
    @UIFieldGroup("UI")
    public String getConfirmMessage() {
        return getJsonData("confirm", "");
    }

    public void setConfirmMessage(String value) {
        setJsonData("confirm", value);
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    protected void beforePersist() {
        if (!getJsonData().has("btnClr")) {
            setButtonColor(UI.Color.random());
        }
        HasChartDataSource.randomColor(this);
        HasIcon.randomColor(this);
    }
}
