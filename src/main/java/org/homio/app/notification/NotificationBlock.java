package org.homio.app.notification;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
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
@RequiredArgsConstructor
public class NotificationBlock {

    private final String entityID;
    private final String name;
    private final String icon;
    private final String color;
    @JsonIgnore
    private final String email;

    @Setter
    private String version;

    @Setter
    private Collection<UIInputEntity> actions;
    @Setter
    private Collection<UIInputEntity> contextMenuActions;

    private Status status;
    private String statusColor;

    private final List<Info> infoItems = new ArrayList<>();

    private List<String> versions;
    private BiFunction<ProgressBar, String, ActionResponseModel> updateHandler;
    @Setter
    private boolean updating;

    public void setStatus(Status status) {
        this.status = status;
        this.statusColor = status == null ? null : status.getColor();
    }

    public void addInfo(String info, String color, String icon, String iconColor, String buttonIcon, String buttonText,
        String confirmMessage, UIActionHandler handler) {
        Info infoItem = new Info(info, color, icon, iconColor, buttonIcon, buttonText, confirmMessage, handler);
        if (handler != null) {
            infoItem.actionEntityID = String.valueOf(Objects.hash(info, icon, buttonIcon, buttonText));
        }
        infoItems.add(infoItem);
    }

    public void setUpdatable(BiFunction<ProgressBar, String, ActionResponseModel> updateHandler, List<String> versions) {
        this.versions = versions;
        this.updateHandler = updateHandler;
    }

    public void remove(String info) {
        infoItems.removeIf(item -> info.equals(item.getInfo()));
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
