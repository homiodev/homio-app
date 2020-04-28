package org.touchhome.bundle.api.model.workspace.bool;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.bundle.api.model.BaseEntity;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import java.util.Set;

@Entity
@Getter
@Accessors(chain = true)
public class WorkspaceBooleanGroupEntity extends BaseEntity<WorkspaceBooleanGroupEntity> {

    public static final String PREFIX = "wbog_";

    @Setter
    @OneToMany(fetch = FetchType.EAGER, mappedBy = "workspaceBooleanGroupEntity", cascade = CascadeType.ALL)
    private Set<WorkspaceBooleanEntity> workspaceBooleanEntities;
}
