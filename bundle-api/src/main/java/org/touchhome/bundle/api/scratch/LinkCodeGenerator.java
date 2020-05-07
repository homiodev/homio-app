package org.touchhome.bundle.api.scratch;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONArray;
import org.json.JSONObject;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.model.workspace.WorkspaceShareVariableEntity;
import org.touchhome.bundle.api.model.workspace.bool.WorkspaceBooleanEntity;
import org.touchhome.bundle.api.model.workspace.var.WorkspaceVariableEntity;
import org.touchhome.bundle.api.workspace.WorkspaceEntity;

import java.util.Map;

@RequiredArgsConstructor
class LinkCodeGenerator {

    private final String extension;
    private final String opcode;
    private final EntityContext entityContext;
    private final Map<String, Object> menuValues;
    private final Map<String, Scratch3Block.ArgumentTypeDescription> arguments;

    private Pair<String, String> generateShareVariables(String block, EntityContext entityContext, String varGroup, String varName) {
        WorkspaceShareVariableEntity workspaceShareVariableEntity = entityContext.getEntity(WorkspaceShareVariableEntity.PREFIX + WorkspaceShareVariableEntity.NAME);
        JSONObject content = new JSONObject(StringUtils.defaultIfEmpty(workspaceShareVariableEntity.getContent(), "{}"));
        if (!content.has(block)) {
            content.put(block, new JSONObject());
        }
        JSONObject variables = content.getJSONObject(block);
        Pair<String, String> ids = generateVariable(varGroup, varName, variables);
        // save variables
        entityContext.save(workspaceShareVariableEntity.setContent(content.toString()));
        return ids;
    }

    private Pair<String, String> generateVariable(String varGroup, String varName, JSONObject variables) {
        String varGroupID = null;
        String varNameID = generateID();
        JSONObject varNameObj = new JSONObject().put("id", varNameID).put("name", varName);
        for (String varUUID : variables.keySet()) {
            JSONArray jsonArray = variables.optJSONArray(varUUID);
            if (jsonArray.getString(0).equals(varGroup)) {
                varGroupID = varUUID;
                jsonArray.getJSONArray(2).put(varNameObj);

            }
        }
        if (varGroupID == null) {
            varGroupID = generateID();
            variables.put(varGroupID, new JSONArray().put(varGroup).put(new JSONArray()).put(new JSONArray().put(varNameObj)));
        }

        return Pair.of(varGroupID, varNameID);
    }

    private String generateID() {
        return RandomStringUtils.random(20, true, true);
    }

    void generateFloatLink(String varGroup, String varName) {
        generateLink(varGroup, varName, WorkspaceVariableEntity.class, "group_variables", "group_variables_group", "data_group_variable_link");
    }

    void generateBooleanLink(String varGroup, String varName) {
        generateLink(varGroup, varName, WorkspaceBooleanEntity.class, "bool_variables", "bool_variables_group", "data_boolean_link");
    }

    private void generateLink(String varGroup, String varName, Class<? extends BaseEntity> varClass,
                              String variableKey, String variableGroupKey, String variableOpcode) {
        BaseEntity entity = entityContext.getEntityByName(varName, varClass);
        if (entity != null) {
            throw new IllegalArgumentException("Workspace variable already exists");
        }

        WorkspaceEntity workspaceEntity = entityContext.getEntityByName("links", WorkspaceEntity.class);
        if (workspaceEntity == null) {
            workspaceEntity = entityContext.save(new WorkspaceEntity().setName("links"));
        }
        JSONObject content = new JSONObject(StringUtils.defaultIfEmpty(workspaceEntity.getContent(), "{}"));
        JSONObject blocks = get("blocks", get("target", content));

        String variableId = generateID();
        String bodyID = generateID();
        blocks.put(bodyID, this.generateCode(blocks, variableId, bodyID));

        JSONObject variableObject = this.generateVariable(bodyID, varGroup, varName, calcPosition(blocks),
                variableKey, variableGroupKey, variableOpcode);

        blocks.put(variableId, variableObject);

        entityContext.save(workspaceEntity.setContent(content.toString()));
    }

    private int calcPosition(JSONObject blocks) {
        int y = 0;
        for (String key : blocks.keySet()) {
            y = Math.max(y, blocks.getJSONObject(key).optInt("y"));
        }
        y += 60;
        return y;
    }

    private JSONObject generateVariable(String sourceID, String varGroup, String varName, int y,
                                        String nameID, String nameGroupID, String linkName) {
        Pair<String, String> varIds = generateShareVariables(nameID, entityContext, varGroup, varName);

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("opcode", linkName).put("topLevel", true).put("shadow", false).put("x", 0).put("y", y);
        jsonObject.put("inputs", new JSONObject().put("SOURCE", new JSONArray().put(2).put(sourceID)));
        jsonObject.put("fields", new JSONObject()
                .put(nameGroupID, new JSONArray().put(varName).put(varIds.getValue()))
                .put(nameID, new JSONArray().put(varGroup).put(varIds.getKey()))
        );
        return jsonObject;
    }

    private JSONObject generateCode(JSONObject blocks, String parent, String bodyID) {
        return new JSONObject()
                .put("opcode", this.extension + "_" + this.opcode)
                .put("parent", parent)
                .put("shadow", false)
                .put("topLevel", false)
                .put("inputs", generateInputs(blocks, bodyID));
    }

    private JSONObject generateInputs(JSONObject blocks, String parentBodyID) {
        JSONObject jsonObject = new JSONObject();
        for (Map.Entry<String, Scratch3Block.ArgumentTypeDescription> entry : arguments.entrySet()) {
            String inputID = generateID();
            jsonObject.put(entry.getKey(), new JSONArray().put(1).put(inputID));
            JSONObject menuJSON = generateMenu(entry.getValue(), parentBodyID);
            blocks.put(inputID, menuJSON);
        }
        return jsonObject;
    }

    private JSONObject generateMenu(Scratch3Block.ArgumentTypeDescription value, String parentBodyID) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("opcode", extension + "_menu_" + value.getMenuBlock().getName());
        jsonObject.put("parent", parentBodyID);
        jsonObject.put("shadow", true).put("topLevel", false);
        Object mv = menuValues.get(value.getMenu());
        if (mv == null) {
            throw new IllegalStateException("Unable to find menu value");
        }
        jsonObject.put("fields", new JSONObject().put(value.getMenu(), new JSONArray().put(mv)));
        return jsonObject;
    }

    private JSONObject get(String name, JSONObject parent) {
        if (!parent.has(name)) {
            parent.put(name, new JSONObject());
        }
        return parent.getJSONObject(name);
    }
}
