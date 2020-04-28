package org.touchhome.bundle.api.scratch;

import org.json.JSONArray;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.BaseEntity;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

public interface WorkspaceBlock {
    void logError(String message, Object... params);

    void logErrorAndThrow(String message, Object... params);

    void logWarn(String message, Object... params);

    void logInfo(String message, Object... params);

    <P> P getMenuValue(String key, MenuBlock menuBlock, Class<P> type);

    Map<String, JSONArray> getInputs();

    String getOpcode();

    String findField(Predicate<String> predicate);

    String getField(String fieldName);

    String getFieldId(String fieldName);

    boolean hasField(String fieldName);

    void handle();

    Object evaluate();

    Integer getInputInteger(String key);

    Float getInputFloat(String key);

    String getInputString(String key);

    boolean getInputBoolean(String key);

    WorkspaceBlock getInputWorkspaceBlock(String key);

    Object getInput(String key, boolean fetchValue);

    boolean hasInput(String key);

    String getId();

    WorkspaceBlock getNext();

    boolean isTopLevel();

    boolean isShadow();

    String getDescription();

    void setStateHandler(Consumer<String> stateHandler);

    void setState(String state);

    void release();

    EntityContext getEntityContext();
}
