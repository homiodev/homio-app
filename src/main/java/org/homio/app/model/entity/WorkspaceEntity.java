package org.homio.app.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.api.converter.JSONConverter;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.HasJsonData;
import org.homio.api.model.JSON;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
@Entity
@Accessors(chain = true)
public final class WorkspaceEntity extends BaseEntity<WorkspaceEntity> implements HasJsonData {

    public static final String PREFIX = "space_";

    @Column(length = 10_485_760)
    private String content;

    @Getter
    @Setter
    @Column(length = 10_000)
    @Convert(converter = JSONConverter.class)
    @NotNull
    private JSON jsonData = new JSON();

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    protected void validate() {
        if (getName() == null || getName().length() < 2 || getName().length() > 10) {
            throw new IllegalStateException("Workspace tab name must be between 2..10 characters");
        }
    }

    @Override
    protected int getChildEntityHashCode() {
        int result = content != null ? content.hashCode() : 0;
        result = 31 * result + jsonData.hashCode();
        return result;
    }

    @Override
    public @NotNull String getEntityPrefix() {
        return PREFIX;
    }
}
