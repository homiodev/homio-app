package org.touchhome.app.model.entity.widget.impl.js;

import lombok.Getter;
import lombok.Setter;
import org.touchhome.app.model.entity.widget.impl.WidgetBaseEntity;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldCodeEditor;
import org.touchhome.bundle.api.ui.field.UIFieldType;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;

@Entity
@Getter
@Setter
public class WidgetJsEntity extends WidgetBaseEntity<WidgetJsEntity> {

    @Lob
    @Column(length = 1048576)
    @UIField(order = 13)
    @UIFieldCodeEditor(editorType = UIFieldCodeEditor.CodeEditorType.javascript)
    private String javaScript;

    @Lob
    @Column(length = 1048576)
    @UIField(order = 12, type = UIFieldType.Json)
    private String javaScriptParameters;

    @Override
    public String getImage() {
        return "fab fa-js-square";
    }

    @Override
    public boolean updateRelations(EntityContext entityContext) {
        return false;
    }
}
