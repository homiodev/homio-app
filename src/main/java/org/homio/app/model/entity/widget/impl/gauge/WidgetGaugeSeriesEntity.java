package org.homio.app.model.entity.widget.impl.gauge;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import org.homio.api.entity.widget.ability.HasGetStatusValue;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;
import org.homio.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.app.model.entity.widget.attributes.HasIcon;
import org.homio.app.model.entity.widget.attributes.HasSingleValueDataSource;
import org.homio.app.model.entity.widget.attributes.HasValueTemplate;

@Entity
public class WidgetGaugeSeriesEntity extends WidgetSeriesEntity<WidgetGaugeEntity>
        implements HasSingleValueDataSource, HasIcon, HasValueTemplate {

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    protected String getSeriesPrefix() {
        return "gauge";
    }

    @Override
    public void beforePersist() {
        HasIcon.randomColor(this);
    }

    @UIField(order = 1)
    public boolean getUseGaugeValue() {
        return getJsonData("ugv", Boolean.TRUE);
    }

    public void setUseGaugeValue(boolean value) {
        setJsonData("ugv", value);
    }

    @Override
    @UIFieldGroup("")
    @UIField(order = 10, required = true)
    @UIFieldEntityByClassSelection(HasGetStatusValue.class)
    @UIFieldShowOnCondition("return context.get('useGaugeValue') == 'false'")
    public String getValueDataSource() {
        return getJsonData("vds");
    }

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public String getName() {
        return super.getName();
    }
}
