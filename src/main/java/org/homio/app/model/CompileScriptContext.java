package org.homio.app.model;

import com.fasterxml.jackson.databind.JsonNode;

import javax.script.CompiledScript;
import javax.script.ScriptEngine;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class CompileScriptContext {

    private final CompiledScript compiledScript;
    private final String formattedJavaScript;
    private final JsonNode jsonParams;

    public ScriptEngine getEngine() {
        return compiledScript.getEngine();
    }
}
