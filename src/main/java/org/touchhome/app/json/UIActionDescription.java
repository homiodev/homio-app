package org.touchhome.app.json;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.json.JSONObject;

@Getter
@Setter
@Accessors(chain = true)
public class UIActionDescription {
    private Type type;
    private String name;
    private JSONObject metadata;
    private String responseAction;

    public enum Type {
        header, method
    }
}
