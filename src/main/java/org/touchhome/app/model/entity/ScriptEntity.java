package org.touchhome.app.model.entity;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.ClassUtils;
import org.touchhome.app.thread.js.AbstractJSBackgroundProcessService;
import org.touchhome.app.thread.js.impl.ScriptJSBackgroundProcess;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.thread.BackgroundProcessStatus;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldCodeEditor;
import org.touchhome.bundle.api.ui.field.UIFieldType;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Getter
@Setter
@Entity
@UISidebarMenu(icon = "fab fa-js-square", order = 1, bg = "#9e7d18", allowCreateNewItems = true)
public class ScriptEntity extends BaseEntity<ScriptEntity> {

    private String requiredParams;

    @Column(nullable = false)
    private BackgroundProcessStatus backgroundProcessStatus = BackgroundProcessStatus.NEVER_RUN;

    @UIField(order = 13, type = UIFieldType.Json)
    private String javaScriptParameters = "{}";

    @Getter
    @Setter
    @UIField(order = 16)
    private boolean autoStart = false;

    private String error;

    private String backgroundProcessClass = ScriptJSBackgroundProcess.class.getName();

    @Lob
    @Column(length = 1048576)
    @UIField(order = 30)
    @UIFieldCodeEditor(editorType = UIFieldCodeEditor.CodeEditorType.javascript)
    private String javaScript;

    public BackgroundProcessStatus getBackgroundProcessStatus() {
        return backgroundProcessStatus;
    }

    public boolean setScriptStatus(BackgroundProcessStatus backgroundProcessStatus) {
        if (!Objects.equals(backgroundProcessStatus, this.backgroundProcessStatus)) {
            if (this.backgroundProcessStatus != null) {
                this.backgroundProcessStatus.assertFollowStatus(backgroundProcessStatus);
            }
            this.backgroundProcessStatus = backgroundProcessStatus;
            return true;
        }
        return false;
    }

    public String getBackgroundProcessServiceID() {
        if (backgroundProcessClass == null) {
            return null;
        }
        return backgroundProcessClass.substring(backgroundProcessClass.lastIndexOf(".") + 1) + getEntityID();
    }

    public AbstractJSBackgroundProcessService createBackgroundProcessService(EntityContext entityContext) throws Exception {
        Class<? extends AbstractJSBackgroundProcessService> aClass = (Class<? extends AbstractJSBackgroundProcessService>) ClassUtils.getClass(backgroundProcessClass);
        return aClass.getConstructor(getClass(), EntityContext.class).newInstance(this, entityContext);
    }

    public Set<String> getFunctionsWithPrefix(String prefix) {
        Set<String> functions = new HashSet<>();
        int i = javaScript.indexOf("function " + prefix, 0);
        while (i >= 0) {
            int endIndex = i + 1;
            int countOfBrakets = 0;
            while (javaScript.length() > endIndex) {
                char at = javaScript.charAt(endIndex);

                if (at == '}' && countOfBrakets == 1) {
                    endIndex++;
                    break;
                }

                if (at == '{') {
                    countOfBrakets++;
                } else if (at == '}') {
                    countOfBrakets--;
                }
                endIndex++;
            }
            functions.add(javaScript.substring(i, endIndex));
            i = javaScript.indexOf("function " + prefix, i + 1);
        }
        return functions;
    }

    public String getFunctionWithName(String name) {
        Set<String> functionsWithPrefix = getFunctionsWithPrefix(name);
        return functionsWithPrefix.isEmpty() ? null : functionsWithPrefix.iterator().next();
    }
}
