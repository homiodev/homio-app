package org.homio.app.model.entity.widget;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Set;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.entity.BaseEntity;
import org.homio.bundle.api.exception.ServerException;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
@Entity
public final class WidgetTabEntity extends BaseEntity<WidgetTabEntity> {

    public static final String PREFIX = "wtab_";
    public static final String GENERAL_WIDGET_TAB_NAME = PREFIX + "main";

    public static void ensureMainTabExists(EntityContextImpl entityContext) {
        if (entityContext.getEntity(GENERAL_WIDGET_TAB_NAME) == null) {
            entityContext.save(new WidgetTabEntity().setEntityID(GENERAL_WIDGET_TAB_NAME).setName("MainTab"));
        }
    }

    @Getter
    @JsonIgnore
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "widgetTabEntity")
    private Set<WidgetBaseEntity> widgetBaseEntities;

    @Override
    public int compareTo(@NotNull BaseEntity o) {
        return this.getCreationTime().compareTo(o.getCreationTime());
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    protected void validate() {
        if (getName() == null || getName().length() < 2 || getName().length() > 10) {
            throw new ServerException("Widget tab name must be between 2..10 characters");
        }
    }

    @Override
    public boolean isDisableDelete() {
        return this.getEntityID().equals(GENERAL_WIDGET_TAB_NAME);
    }

    @Override
    public void beforeDelete(EntityContext entityContext) {
        if (!widgetBaseEntities.isEmpty()) {
            throw new ServerException("W.ERROR.REMOVE_NON_EMPTY_TAB");
        }
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }
}
