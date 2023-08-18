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
import org.homio.api.model.Icon;
import org.homio.api.model.JSON;
import org.homio.api.util.Lang;
import org.homio.app.manager.common.EntityContextImpl;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
@Entity
public final class WidgetTabEntity extends BaseEntity implements HasOrder {

    public static final String PREFIX = "tab_";
    public static final String MAIN_TAB_ID = PREFIX + "main";
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
        if (entityContext.getEntity(MAIN_TAB_ID) == null) {
            String name = Lang.getServerMessage("MAIN_TAB_NAME");
            WidgetTabEntity mainTab = new WidgetTabEntity();
            mainTab.setEntityID(MAIN_TAB_ID);
            mainTab.setName(name);
            mainTab.setOrder(0);
            entityContext.save(mainTab);
        }
    }

    @Override
    public int compareTo(@NotNull BaseEntity o) {
        return super.compareTo(o);
    }

    public Icon getIcon() {
        return getJsonData("icon", Icon.class);
    }

    public WidgetTabEntity setIcon(Icon icon) {
        setJsonDataObject("icon", icon);
        return this;
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
        return this.getEntityID().equals(MAIN_TAB_ID);
    }

    @Override
    public void beforeDelete(@NotNull EntityContext entityContext) {
        if (!widgetBaseEntities.isEmpty()) {
            throw new IllegalArgumentException("W.ERROR.REMOVE_NON_EMPTY_TAB");
        }
        if (this.getEntityID().equals(MAIN_TAB_ID)) {
            throw new IllegalArgumentException("W.ERROR.REMOVE_MAIN_TAB");
        }
    }

    @Override
    public @NotNull String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    protected void validate() {
        if (getName() == null || getName().length() < 2 || getName().length() > 16) {
            throw new ServerException("ERROR.WRONG_TAB_NAME");
        }
    }
}
