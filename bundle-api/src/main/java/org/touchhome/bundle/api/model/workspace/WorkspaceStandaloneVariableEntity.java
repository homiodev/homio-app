package org.touchhome.bundle.api.model.workspace;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.bundle.api.model.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;

@Entity
@Getter
@Accessors(chain = true)
public class WorkspaceStandaloneVariableEntity extends BaseEntity<WorkspaceStandaloneVariableEntity> {

    public static final String PREFIX = "wsv_";

    @Setter
    @Column(nullable = false)
    private float value;

    @Override
    public String getTitle() {
        return "Variable: " + getName();
    }
}
