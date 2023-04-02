package org.homio.app.model.entity.widget;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Set;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;
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
    public void beforeDelete(EntityContext entityContext) {
        if (this.getEntityID().equals(GENERAL_WIDGET_TAB_NAME)) {
            throw new ServerException("ERROR.REMOVE_MAIN_TAB");
        }
        if (!widgetBaseEntities.isEmpty()) {
            throw new ServerException("ERROR.REMOVE_NON_EMPTY_TAB");
        }
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }
}
