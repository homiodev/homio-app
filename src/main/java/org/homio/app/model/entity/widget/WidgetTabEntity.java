package org.homio.app.model.entity.widget;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.homio.api.EntityContext;
import org.homio.api.converter.JSONConverter;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.HasOrder;
import org.homio.api.exception.ServerException;
import org.homio.api.model.JSON;
import org.homio.app.manager.common.EntityContextImpl;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
@Entity
public final class WidgetTabEntity extends BaseEntity<WidgetTabEntity> implements HasOrder {

    public static final String PREFIX = "tab_";
    public static final String GENERAL_WIDGET_TAB_NAME = PREFIX + "main";
    @Getter
    @JsonIgnore
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "widgetTabEntity")
    private Set<WidgetBaseEntity> widgetBaseEntities;

    @Getter
    @Setter
    @Column(length = 10_000)
    @Convert(converter = JSONConverter.class)
    @NotNull
    private JSON jsonData = new JSON();

    @Override
    public boolean enableUiOrdering() {
        return true;
    }

    public static void ensureMainTabExists(EntityContextImpl entityContext) {
        if (entityContext.getEntity(GENERAL_WIDGET_TAB_NAME) == null) {
            entityContext.save(new WidgetTabEntity().setEntityID(GENERAL_WIDGET_TAB_NAME).setName("MainTab"));
        }
    }

    @Override
    public int compareTo(@NotNull BaseEntity o) {
        return super.compareTo(o);
    }

    @Override
    protected int getChildEntityHashCode() {
        return 0;
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    public boolean isDisableDelete() {
        return this.getEntityID().equals(GENERAL_WIDGET_TAB_NAME);
    }

    @Override
    public void beforeDelete(@NotNull EntityContext entityContext) {
        if (!widgetBaseEntities.isEmpty()) {
            throw new ServerException("ERROR.REMOVE_NON_EMPTY_TAB");
        }
    }

    @Override
    public @NotNull String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    protected void validate() {
        if (getName() == null || getName().length() < 2 || getName().length() > 10) {
            throw new ServerException("ERROR.WRONG_TAB_NAME");
        }
    }
}
