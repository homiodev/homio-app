package org.homio.app.model.entity.widget;

import org.homio.api.entity.BaseEntity;
import org.homio.api.model.HasEntityIdentifier;

/**
 * Very custom purpose to filter Options for widgets. I.e slider widget requires WorkspaceVariable of type
 * Float only, etc...
 */
public interface HasOptionsForEntityByClassFilter {
    boolean isExclude(Class<? extends HasEntityIdentifier> sourceClassType, BaseEntity baseEntity);
}
