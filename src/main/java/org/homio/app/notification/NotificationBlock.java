package org.homio.app.notification;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.homio.bundle.api.model.ActionResponseModel;
import org.homio.bundle.api.model.Status;
import org.homio.bundle.api.ui.action.UIActionHandler;
import org.homio.bundle.api.ui.field.ProgressBar;
import org.homio.bundle.api.ui.field.action.v1.UIInputEntity;

@Getter
@Setter
@RequiredArgsConstructor
public class NotificationBlock {

    private final String entityID;
    private final String name;
    private final String icon;
    private final String color;

    private String version;

    private Collection<UIInputEntity> actions;

    private Collection<UIInputEntity> contextMenuActions;

    private Status status;

    private String statusColor;

    private boolean updating;

    private List<String> versions;

    private String link;
    private String linkType;

    @JsonIgnore
    private final Map<String, Info> infoItemMap = new ConcurrentHashMap<>();

    @JsonIgnore
    private BiFunction<ProgressBar, String, ActionResponseModel> updateHandler;

    @JsonIgnore
    private Runnable fireOnFetchHandler;

    @JsonIgnore
    private String email;

    public Collection<Info> getInfoItems() {
        return infoItemMap.values();
    }

    public void addInfo(String key, String info, String color, String icon, String iconColor, String buttonIcon, String buttonText,
        String confirmMessage, UIActionHandler handler) {
        Info infoItem = new Info(info, color, icon, iconColor, buttonIcon, buttonText, confirmMessage, handler);
        if (handler != null) {
            infoItem.actionEntityID = String.valueOf(Objects.hash(info, icon, buttonIcon, buttonText));
        }
        infoItemMap.put(key, infoItem);
    }

    public void setUpdatable(BiFunction<ProgressBar, String, ActionResponseModel> updateHandler, List<String> versions) {
        this.versions = versions;
        this.updateHandler = updateHandler;
    }

    public boolean remove(String key) {
        return infoItemMap.remove(key) != null;
    }

    @Getter
    @RequiredArgsConstructor
    public static class Info {

        private final String info;
        private final String color;
        private final String icon;
        private final String iconColor;
        private final String buttonIcon;
        private final String buttonText;
        private final String confirmMessage;
        @JsonIgnore
        private final UIActionHandler handler;
        public String actionEntityID;
    }
}
