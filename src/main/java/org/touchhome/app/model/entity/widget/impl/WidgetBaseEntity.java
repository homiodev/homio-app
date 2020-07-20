package org.touchhome.app.model.entity.widget.impl;

import lombok.Getter;
import lombok.Setter;
import org.touchhome.app.model.entity.widget.HasDataSource;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.ui.field.UIField;

import javax.persistence.*;
import java.util.Set;

@Getter
@Setter
@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@UISidebarMenu(icon = "fas fa-tachometer-alt", bg = "#107d6b")
public abstract class WidgetBaseEntity<T extends WidgetBaseEntity> extends BaseEntity<T> {

    @ManyToOne(fetch = FetchType.LAZY)
    // @UIField(order = 11, type = UIFieldType.Selection, typeMetaData = "{'title':'widgetTabEntity.title', 'path':'ownerPlace.entityID'}")
    private WidgetTabEntity widgetTabEntity;

    @Override
    @UIField(order = 3, transparent = true)
    public String getDescription() {
        return super.getDescription();
    }

    public abstract String getImage();

    public abstract boolean updateRelations(EntityContext entityContext);

    protected boolean validateSeries(Set<? extends BaseEntity> series, EntityContext entityContext) {
        boolean updated = false;
        for (BaseEntity item : series) {
            String dataSource = ((HasDataSource) item).getDataSource();
            if (dataSource != null) {
                BaseEntity entity = entityContext.getEntity(dataSource);
                if (entity == null) {
                    updated = true;
                    ((HasDataSource) item).setDataSource(null);
                }
            }
        }
        return updated;
    }

    @Override
    protected void beforePersist() {
        if (widgetTabEntity == null) {
            throw new IllegalStateException("Unable to save widget without attach to tab");
        }
    }

    @Override
    protected void beforeUpdate() {
        super.beforeUpdate();
    }
}
