package org.homio.app.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.api.entity.BaseEntity;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
@Entity
@Accessors(chain = true)
public final class WorkspaceEntity extends BaseEntity<WorkspaceEntity> {

    public static final String PREFIX = "ws_";

    @Column(length = 10_485_760)
    private String content;

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
            throw new IllegalStateException("Workspace tab name must be between 2..10 characters");
        }
    }

    @Override
    public @NotNull String getEntityPrefix() {
        return PREFIX;
    }
}
