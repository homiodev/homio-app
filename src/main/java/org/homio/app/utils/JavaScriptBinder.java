package org.homio.app.utils;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.Logger;
import org.homio.api.EntityContext;
import org.homio.app.model.entity.ScriptEntity;

@RequiredArgsConstructor
public enum JavaScriptBinder {
    script(ScriptEntity.class, "Current script entity"),
    log(Logger.class, "Script logger"),
    entityContext(EntityContext.class, "Common service factory to access to any object"),
    params(JsonNode.class, "Parameters saved in script's js param"),
    context(JsonNode.class, "Workspace previous value");

    public final Class managerClass;
    public final String help;
}
