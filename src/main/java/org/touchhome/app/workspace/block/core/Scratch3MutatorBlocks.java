package org.touchhome.app.workspace.block.core;

import com.jayway.jsonpath.JsonPath;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.touchhome.app.manager.ScriptService;
import org.touchhome.app.model.CompileScriptContext;
import org.touchhome.app.model.entity.ScriptEntity;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.workspace.WorkspaceBlock;
import org.touchhome.bundle.api.workspace.WorkspaceEventListener;
import org.touchhome.bundle.api.workspace.scratch.BlockType;
import org.touchhome.bundle.api.workspace.scratch.Scratch3Block;
import org.touchhome.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;

import java.util.HashMap;
import java.util.Map;

@Getter
@Component
public class Scratch3MutatorBlocks extends Scratch3ExtensionBlocks implements WorkspaceEventListener {

    private final Map<Integer, CompileScriptContext> compileScriptContextMap = new HashMap<>();
    private final ScriptService scriptService;

    private final Scratch3Block joinStringBlock;
    private final Scratch3Block jsonReduce;
    private final Scratch3Block mapBlock;

    public Scratch3MutatorBlocks(EntityContext entityContext, ScriptService scriptService) {
        super("mutator", entityContext);
        this.scriptService = scriptService;

        // Blocks
        this.joinStringBlock = Scratch3Block.ofEvaluate("join", BlockType.reporter, this::joinStringEvaluate);
        this.jsonReduce = Scratch3Block.ofEvaluate("json_reduce", BlockType.reporter, this::jsonReduceEvaluate);
        this.mapBlock = Scratch3Block.ofEvaluate("map", BlockType.reporter, this::mapEvaluate);
    }

    public static JSONObject reduceJSON(String json, String query) {
        if (StringUtils.isNotEmpty(query)) {
            Object filteredObject = JsonPath.read(json, query);
            if (filteredObject instanceof Map) {
                return new JSONObject((Map) filteredObject);
            }
            return (JSONObject) filteredObject;
        }
        return new JSONObject(json);
    }

    @Override
    public void release(String id) {
        this.compileScriptContextMap.clear();
    }

    @SneakyThrows
    private Object mapEvaluate(WorkspaceBlock workspaceBlock) {
        String source = workspaceBlock.getInputString("SOURCE");
        String map = workspaceBlock.getInputString("MAP");

        CompileScriptContext compileScriptContext = this.compileScriptContextMap.computeIfAbsent(map.hashCode(), integer -> {
            String code = map;
            if (ScriptEntity.getFunctionWithName(code, "run") == null) {
                code = "function run() { " + code + " }";
            }
            ScriptEntity scriptEntity = new ScriptEntity().setJavaScript(code);
            return scriptService.createCompiledScript(scriptEntity, null);
        });
        compileScriptContext.getEngine().put("input", source);
        return scriptService.runJavaScript(compileScriptContext);
    }

    private Object jsonReduceEvaluate(WorkspaceBlock workspaceBlock) {
        String json = workspaceBlock.getInputString("JSON");
        String query = workspaceBlock.getInputString("REDUCE");
        return reduceJSON(json, query);
    }

    private String joinStringEvaluate(WorkspaceBlock workspaceBlock) {
        return workspaceBlock.getInputString("STRING1") + workspaceBlock.getInputString("STRING2");
    }
}
