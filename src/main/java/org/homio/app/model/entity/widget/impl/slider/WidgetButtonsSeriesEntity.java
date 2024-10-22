package org.homio.app.model.entity.widget.impl.slider;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import org.apache.commons.lang3.NotImplementedException;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.widget.ability.HasGetStatusValue;
import org.homio.api.entity.widget.ability.HasSetStatusValue;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.*;
import org.homio.app.model.entity.widget.HasOptionsForEntityByClassFilter;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.app.model.entity.widget.attributes.*;

@Entity
public class WidgetButtonsSeriesEntity
        extends WidgetSeriesEntity<WidgetButtonsEntity>
        implements HasSingleValueDataSource,
        HasSetSingleValueDataSource,
        HasIcon,
        HasName {

    @UIField(order = 3)
    public String getActiveSendValue() {
        return getJsonData("onValue", "1");
    }

    public void setActiveSendValue(String value) {
        setJsonData("onValue", value);
    }

    @Override
    protected String getSeriesPrefix() {
        return "buttons";
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    public void beforePersist() {
        HasIcon.randomColor(this);
    }
}
