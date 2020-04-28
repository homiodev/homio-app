package org.touchhome.bundle.api.model.workspace.bool;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.bundle.api.model.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;

@Entity
@Getter
@Accessors(chain = true)
public class WorkspaceBooleanEntity extends BaseEntity<WorkspaceBooleanEntity> {

    public static final String PREFIX = "wbo_";

    @Setter
    @Column(nullable = false)
    private Boolean value = Boolean.FALSE;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    private WorkspaceBooleanGroupEntity workspaceBooleanGroupEntity;

    @Override
    public String getTitle() {
        return "Var: " + getName() + " / group [" + workspaceBooleanGroupEntity.getName() + "]";
    }

    public WorkspaceBooleanEntity inverseValue() {
        this.value = !this.value;
        return this;
    }
}
