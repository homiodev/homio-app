package org.homio.app.utils;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.logging.log4j.Logger;
import org.homio.app.model.entity.ScriptEntity;
import org.homio.bundle.api.EntityContext;

public enum JavaScriptBinder {
    script(ScriptEntity.class),
    log(Logger.class),
    entityContext(EntityContext.class),
    params(JsonNode.class),
    context(Object.class);

    public Class managerClass;

    JavaScriptBinder(Class managerClass) {
        this.managerClass = managerClass;
    }
}
