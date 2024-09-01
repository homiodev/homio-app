package org.homio.app.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.api.converter.JSONConverter;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.HasJsonData;
import org.homio.api.model.Icon;
import org.homio.api.model.JSON;
import org.homio.api.ui.field.selection.SelectionConfiguration;
import org.homio.app.model.entity.user.UserGuestEntity;
import org.homio.app.workspace.WorkspaceService;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@Getter
@Setter
@Entity
@Accessors(chain = true)
@NoArgsConstructor
public final class WorkspaceEntity extends BaseEntity implements
        HasJsonData,
        SelectionConfiguration {

    public static final String PREFIX = "space_";

    @Column(length = 10_485_760)
    private String content;

    @Getter
    @Setter
    @Column(length = 10_000)
    @Convert(converter = JSONConverter.class)
    @NotNull
    private JSON jsonData = new JSON();

    private String icon;
    private String iconColor;
    private boolean locked;

    public WorkspaceEntity(String entityID, String name) {
        setEntityID(entityID);
        setName(name);
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    public void validate() {
        if (getName() == null || getName().length() < 2 || getName().length() > 10) {
            throw new IllegalStateException("Workspace tab name must be between 2..10 characters");
        }
    }

    @Override
    public @NotNull Icon getSelectionIcon() {
        return new Icon(icon, iconColor);
    }

    @Override
    protected long getChildEntityHashCode() {
        int result = content != null ? content.hashCode() : 0;
        result = 31 * result + jsonData.toString().hashCode();
        return result;
    }

    @Override
    protected void assembleMissingMandatoryFields(@NotNull Set<String> fields) {

    }

    @Override
    public boolean isDisableDelete() {
        return super.isDisableDelete() || isLocked() || !context().getBean(WorkspaceService.class).isEmpty(content);
    }

    @Override
    public @NotNull String getEntityPrefix() {
        return PREFIX;
    }
}
