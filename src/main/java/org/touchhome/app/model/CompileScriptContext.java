package org.touchhome.app.model;

import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;

@Getter
@RequiredArgsConstructor
public class CompileScriptContext {

    private final CompiledScript compiledScript;
    private final String formattedJavaScript;
    private final JSONObject jsonParams;

    public ScriptEngine getEngine() {
        return compiledScript.getEngine();
    }
}
