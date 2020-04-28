package org.touchhome.bundle.api.link;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.touchhome.bundle.api.model.workspace.bool.WorkspaceBooleanEntity;
import org.touchhome.bundle.api.model.workspace.var.WorkspaceVariableEntity;

@Getter
@AllArgsConstructor
public enum DeviceChannelLinkType {
    Boolean(WorkspaceBooleanEntity.PREFIX),
    Float(WorkspaceVariableEntity.PREFIX),
    None("");

    private String entityPrefix;
}
