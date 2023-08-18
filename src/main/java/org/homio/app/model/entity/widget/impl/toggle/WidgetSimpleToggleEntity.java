package org.homio.app.model.entity.widget.impl.toggle;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import org.homio.api.EntityContextWidget.ToggleType;
import org.homio.api.exception.ProhibitedExecution;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.app.model.entity.widget.WidgetBaseEntity;
import org.homio.app.model.entity.widget.attributes.HasAlign;
import org.homio.app.model.entity.widget.attributes.HasPadding;
import org.homio.app.model.entity.widget.attributes.HasSingleValueDataSource;
import org.homio.app.model.entity.widget.attributes.HasSourceServerUpdates;
import org.jetbrains.annotations.NotNull;

@Entity
public class WidgetSimpleToggleEntity extends WidgetBaseEntity<WidgetSimpleToggleEntity>
        implements HasSourceServerUpdates, HasSingleValueDataSource, HasToggle, HasAlign, HasPadding {

    @Override
    public boolean isVisible() {
        return false;
    }

    @Override
    public @NotNull String getImage() {
        return "";
    }

    @Override
    protected @NotNull String getWidgetPrefix() {
        return "sim-toggle";
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
