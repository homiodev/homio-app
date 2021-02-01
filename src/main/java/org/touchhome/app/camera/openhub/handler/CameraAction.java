package org.touchhome.app.camera.openhub.handler;

import lombok.Getter;
import org.json.JSONObject;
import org.touchhome.bundle.api.ui.field.UIFieldType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Getter
public class CameraAction {
    private final String name;
    private final UIFieldType uiFieldType;
    private final Consumer<JSONObject> action;

    private CameraAction(String name, UIFieldType uiFieldType, Consumer<JSONObject> action) {
        this.name = name;
        this.uiFieldType = uiFieldType;
        this.action = action;
    }

    public static CameraActionsBuilder builder() {
        return new CameraActionsBuilder();
    }

    public static class CameraActionsBuilder {

        private List<CameraAction> actions = new ArrayList<>();

        public CameraActionsBuilder add(String name, UIFieldType uiFieldType, Consumer<JSONObject> action) {
            actions.add(new CameraAction(name, uiFieldType, action));
            return this;
        }

        public CameraActionsBuilder add(String name, UIFieldType uiFieldType, Consumer<JSONObject> action, Runnable getAction) {
            actions.add(new CameraAction(name, uiFieldType, action));
            return this;
        }

        public List<CameraAction> get() {
            return actions;
        }
    }
}
