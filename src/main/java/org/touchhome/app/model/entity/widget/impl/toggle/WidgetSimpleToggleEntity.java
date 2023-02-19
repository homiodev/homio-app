package org.touchhome.app.model.entity.widget.impl.toggle;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.persistence.Entity;
import org.touchhome.app.model.entity.widget.WidgetBaseEntity;
import org.touchhome.app.model.entity.widget.attributes.HasAlign;
import org.touchhome.app.model.entity.widget.attributes.HasPadding;
import org.touchhome.app.model.entity.widget.attributes.HasSingleValueDataSource;
import org.touchhome.app.model.entity.widget.attributes.HasSourceServerUpdates;
import org.touchhome.bundle.api.EntityContextWidget.ToggleType;
import org.touchhome.bundle.api.exception.ProhibitedExecution;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;

@Entity
public class WidgetSimpleToggleEntity extends WidgetBaseEntity<WidgetSimpleToggleEntity>
    implements HasSourceServerUpdates, HasSingleValueDataSource, HasToggle, HasAlign, HasPadding {

    public static final String PREFIX = "wgtstgl_";

    @Override
    public boolean isVisible() {
        return false;
    }

    @Override
    public String getImage() {
        return null;
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    // @UIField(order = 32)
    public ToggleType getDisplayType() {
        return getJsonDataEnum("displayType", ToggleType.Slide);
    }

    public void setDisplayType(ToggleType value) {
        setJsonData("displayType", value);
    }

    @Override
    @UIFieldIgnore
    @JsonIgnore
    public Boolean getShowLastUpdateTimer() {
        throw new ProhibitedExecution();
    }

    @Override
    protected void beforePersist() {
        if (!getJsonData().has("color")) {
            setColor(UI.Color.random());
        }
        if (getOnValues().isEmpty()) {
            setOnValues("true~~~1");
        }
    }
}
