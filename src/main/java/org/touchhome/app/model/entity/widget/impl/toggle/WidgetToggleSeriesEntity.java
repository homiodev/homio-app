package org.touchhome.app.model.entity.widget.impl.toggle;

import javax.persistence.Entity;
import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.app.model.entity.widget.attributes.HasIcon;
import org.touchhome.app.model.entity.widget.attributes.HasName;
import org.touchhome.app.model.entity.widget.attributes.HasSingleValueDataSource;
import org.touchhome.bundle.api.ui.UI;

@Entity
public class WidgetToggleSeriesEntity extends WidgetSeriesEntity<WidgetToggleEntity>
    implements HasSingleValueDataSource, HasIcon, HasName, HasToggle {

    public static final String PREFIX = "wgttgs_";

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
        HasIcon.randomColor(this);
        if (!getJsonData().has("color")) {
            setColor(UI.Color.random());
        }
        if (getOnValues().isEmpty()) {
            setOnValues("true~~~1");
        }
    }
}
