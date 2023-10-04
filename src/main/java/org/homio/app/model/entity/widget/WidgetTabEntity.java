package org.homio.app.model.entity.widget;

import static org.homio.api.util.Constants.PRIMARY_DEVICE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.homio.api.converter.JSONConverter;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.HasOrder;
import org.homio.api.exception.ServerException;
import org.homio.api.model.Icon;
import org.homio.api.model.JSON;
import org.homio.api.ui.field.selection.SelectionConfiguration;
import org.homio.api.util.Lang;
import org.homio.app.manager.common.EntityContextImpl;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
@Entity
public final class WidgetTabEntity extends BaseEntity implements
    HasOrder,
    SelectionConfiguration {

    public static final String PREFIX = "tab_";
    public static final String MAIN_TAB_ID = PREFIX + "main";
    @JsonIgnore
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "widgetTabEntity")
    private Set<WidgetBaseEntity> widgetBaseEntities;

    @Column(length = 10_000)
    @Convert(converter = JSONConverter.class)
    @NotNull
    private JSON jsonData = new JSON();

    private String icon;

    private String iconColor;

    private boolean locked = false;

    private int horizontalBlocks = 8;

    private int verticalBlocks = 8;

    @Override
    public boolean enableUiOrdering() {
        return true;
    }

    public static void ensureMainTabExists(EntityContextImpl entityContext) {
        if (entityContext.getEntity(WidgetTabEntity.class, PRIMARY_DEVICE) == null) {
            String name = Lang.getServerMessage("MAIN_TAB_NAME");
            WidgetTabEntity mainTab = new WidgetTabEntity();
            mainTab.setEntityID(PRIMARY_DEVICE);
            mainTab.setName(name);
            mainTab.setLocked(true);
            mainTab.setOrder(0);
            entityContext.save(mainTab);
        }
    }

    @Override
    public int compareTo(@NotNull BaseEntity o) {
        return super.compareTo(o);
    }

    @Override
    public @NotNull Icon getSelectionIcon() {
        return new Icon(icon, iconColor);
    }

    @Override
    protected long getChildEntityHashCode() {
        return 0;
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    public boolean isDisableDelete() {
        return super.isDisableDelete() || isLocked() || !widgetBaseEntities.isEmpty();
    }

    @Override
    public @NotNull String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public void validate() {
        if (getName() == null || getName().length() < 2 || getName().length() > 16) {
            throw new ServerException("ERROR.WRONG_TAB_NAME");
        }
    }

    @Override
    public void afterDelete() {
        // shift all higher order <<
        for (WidgetTabEntity tabEntity : getEntityContext().findAll(WidgetTabEntity.class)) {
            if (tabEntity.getOrder() > getOrder()) {
                tabEntity.setOrder(tabEntity.getOrder() - 1);
                getEntityContext().save(tabEntity, false);
            }
        }
    }
}
