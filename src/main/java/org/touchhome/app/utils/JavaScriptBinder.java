package org.touchhome.app.utils;

import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.touchhome.app.model.entity.ScriptEntity;
import org.touchhome.bundle.api.EntityContext;

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
