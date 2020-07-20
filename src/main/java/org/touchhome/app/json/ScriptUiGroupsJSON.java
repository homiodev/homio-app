package org.touchhome.app.json;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.touchhome.bundle.api.model.BaseEntity;

import java.util.ArrayList;
import java.util.List;

public class ScriptUiGroupsJSON {

    private List<Group> groups = new ArrayList<>();

    public Group addGroup(String title) {
        Group group = new Group(title, this);
        groups.add(group);
        return group;
    }

    public List<Group> getGroups() {
        return groups;
    }

    public int getTotalCount() {
        return groups.stream().mapToInt(Group::getItemCount).sum();
    }

    public class ScriptUI {
        public String entityID;
        public String help;

        public ScriptUI(BaseEntity baseEntity) {
            this.help = baseEntity.getTitle();
            this.entityID = baseEntity.getEntityID();
        }
    }

    public class Group {
        public String baseCode;
        @JsonIgnore
        private ScriptUiGroupsJSON scriptUiGroupsJSON;
        private String title;
        private List<ScriptUI> scriptUIList = new ArrayList<>();

        public Group(String title, ScriptUiGroupsJSON scriptUiGroupsJSON) {
            this.title = title;
            this.scriptUiGroupsJSON = scriptUiGroupsJSON;
        }

        public ScriptUI add(BaseEntity baseEntity) {
            ScriptUI scriptUI = new ScriptUI(baseEntity);
            scriptUIList.add(scriptUI);
            return scriptUI;
        }

        public String getTitle() {
            return title;
        }

        public int getItemCount() {
            return scriptUIList.size();
        }

        public List<ScriptUI> getItems() {
            return scriptUIList;
        }

        public int getHeight() {
            return (int) (getItemCount() / (float) scriptUiGroupsJSON.getTotalCount() * 100);
        }
    }
}
