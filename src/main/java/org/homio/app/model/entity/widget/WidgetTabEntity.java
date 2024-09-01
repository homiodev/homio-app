package org.homio.app.model.entity.widget;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.LazyInitializationException;
import org.homio.api.converter.JSONConverter;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.HasOrder;
import org.homio.api.exception.ServerException;
import org.homio.api.model.Icon;
import org.homio.api.model.JSON;
import org.homio.api.ui.field.selection.SelectionConfiguration;
import org.homio.api.util.Lang;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.user.UserGuestEntity;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

import static org.homio.api.util.Constants.PRIMARY_DEVICE;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;

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
    private Set<WidgetEntity> widgetBaseEntities;

    @Column(length = 10_000)
    @Convert(converter = JSONConverter.class)
    @NotNull
    private JSON jsonData = new JSON();

    private String icon;

    private String iconColor;

    private boolean locked = false;

    @Override
    public boolean enableUiOrdering() {
        return true;
    }

    public static void ensureMainTabExists(ContextImpl context) {
        if (context.db().get(WidgetTabEntity.class, PRIMARY_DEVICE) == null) {
            String name = Lang.getServerMessage("MAIN_TAB_NAME");
            WidgetTabEntity mainTab = new WidgetTabEntity();
            mainTab.setEntityID(PRIMARY_DEVICE);
            mainTab.setName(name);
            mainTab.setIcon("fas fa-user");
            mainTab.setLocked(true);
            mainTab.setOrder(0);
            context.db().save(mainTab);
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

    public Set<ScreenLayout> getLayout() {
        return getJsonDataSet("wl", ScreenLayout.class);
    }

    @SneakyThrows
    public void addLayout(int hb, int vb, int sw, int sh) {
        Set<ScreenLayout> layouts = getLayout();
        layouts = layouts == null ? new HashSet<>() : layouts;
        ScreenLayout layout = new ScreenLayout(hb, vb, sw, sh);
        layouts.remove(layout);
        layouts.add(layout);
        setJsonData("wl", OBJECT_MAPPER.writeValueAsString(layouts));
    }

    @SneakyThrows
    public void addLayoutOptional(int sw, int sh) {
        Set<ScreenLayout> layouts = getLayout();
        layouts = layouts == null ? new HashSet<>() : layouts;
        ScreenLayout layout = new ScreenLayout(8, 8, sw, sh);
        if (!layouts.contains(layout)) {
            layouts.add(layout);
            setJsonData("wl", OBJECT_MAPPER.writeValueAsString(layouts));
        }
    }

    public ScreenLayout getLayoutOrDefault(int width, int height) {
        for (ScreenLayout layout : getLayout()) {
            if (layout.sw == width && layout.sh == height) {
                return layout;
            }
        }
        return new ScreenLayout(8, 8, width, height);
    }

    @Override
    protected long getChildEntityHashCode() {
        return 0;
    }

    @Override
    protected void assembleMissingMandatoryFields(@NotNull Set<String> fields) {

    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    public boolean isDisableDelete() {
        try {
            return super.isDisableDelete() || isLocked() || (widgetBaseEntities != null && !widgetBaseEntities.isEmpty());
        } catch (LazyInitializationException ex) {
            return true;
        }
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
        for (WidgetTabEntity tabEntity : context().db().findAll(WidgetTabEntity.class)) {
            if (tabEntity.getOrder() > getOrder()) {
                tabEntity.setOrder(tabEntity.getOrder() - 1);
                context().db().save(tabEntity, false);
            }
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScreenLayout {

        private int hb;
        private int vb;

        private int sw;
        private int sh;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ScreenLayout that = (ScreenLayout) o;

            if (sw != that.sw) {
                return false;
            }
            return sh == that.sh;
        }

        @Override
        public int hashCode() {
            int result = sw;
            result = 31 * result + sh;
            return result;
        }

        public String getKey() {
            return sw + "-" + sh + ":" + hb + "-" + vb;
        }
    }
}
