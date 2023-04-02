package org.homio.app.utils;

import org.apache.logging.log4j.Logger;
import org.homio.app.model.entity.ScriptEntity;
import org.homio.bundle.api.EntityContext;
import org.json.JSONObject;

public enum JavaScriptBinder {
    script(ScriptEntity.class),
    log(Logger.class),
    entityContext(EntityContext.class),
    params(JSONObject.class);

    public Class managerClass;

    JavaScriptBinder(Class managerClass) {
        this.managerClass = managerClass;
    }
}
