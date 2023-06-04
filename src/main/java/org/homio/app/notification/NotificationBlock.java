package org.homio.app.notification;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.homio.api.EntityContextUI.NotificationInfoLineBuilder;
import org.homio.api.entity.BaseEntity;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.model.Status;
import org.homio.api.ui.action.UIActionHandler;
import org.homio.api.ui.field.ProgressBar;
import org.homio.api.ui.field.action.v1.UIInputEntity;
import org.homio.app.utils.UIFieldUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
@RequiredArgsConstructor
public class NotificationBlock {

    private final @NotNull String entityID;
    private final String name;
    private final String icon;
    private final String color;

    private String version;

    private @Nullable Collection<UIInputEntity> contextMenuActions;
    private @Nullable Status status;
    private @Nullable String statusColor;
    private boolean updating;
    private @Nullable List<String> versions;
    private @Nullable String link;
    private @Nullable String linkType;

    @JsonIgnore
    private final @NotNull Map<String, Info> infoItemMap = new ConcurrentHashMap<>();

    @JsonIgnore
    private @NotNull Map<String, Collection<UIInputEntity>> keyValueActions = new HashMap<>();

    @JsonIgnore
    private @Nullable BiFunction<ProgressBar, String, ActionResponseModel> updateHandler;

    @JsonIgnore
    private Runnable fireOnFetchHandler;

    @JsonIgnore
    private String email;

    public Collection<UIInputEntity> getActions() {
        if (keyValueActions.isEmpty()) {
            return null;
        }
        Collection<UIInputEntity> actions = new ArrayList<>();
        for (Collection<UIInputEntity> item : keyValueActions.values()) {
            actions.addAll(item);
        }
        return actions;
    }

    public Collection<Info> getInfoItems() {
        return infoItemMap.values();
    }

    public Info addInfoLine(@NotNull String key, @Nullable String info, @Nullable Icon icon) {
        Info infoItem = new Info(key, info, icon);
        infoItemMap.put(key, infoItem);
        return infoItem;
    }

    public void setUpdatable(BiFunction<ProgressBar, String, ActionResponseModel> updateHandler, List<String> versions) {
        this.versions = versions;
        this.updateHandler = updateHandler;
    }

    public boolean remove(String key) {
        return infoItemMap.remove(key) != null;
    }

    @Getter
    public static class Info implements NotificationInfoLineBuilder {

        private final String key;
        private String info;
        private final Icon icon;

        private String textColor;
        private String borderColor;

        private String link;
        private String linkType;


        private String status;
        private Icon statusIcon;
        private String statusColor;

        private Icon buttonIcon;
        private String buttonText;
        private String confirmMessage;
        @JsonIgnore private UIActionHandler handler;
        private String actionEntityID;

        public Info(String key, String info, Icon icon) {
            this.key = key;
            this.info = info;
            this.icon = icon;
        }

        @Override
        public NotificationInfoLineBuilder setTextColor(@Nullable String color) {
            this.textColor = color;
            return this;
        }

        @Override
        public NotificationInfoLineBuilder setRightText(@NotNull String text, @Nullable Icon icon, @Nullable String color) {
            this.status = text;
            this.statusIcon = icon;
            this.statusColor = color;
            return this;
        }

        @Override
        public NotificationInfoLineBuilder setBorderColor(@Nullable String color) {
            this.borderColor = color;
            return this;
        }

        @Override
        public NotificationInfoLineBuilder setRightButton(@Nullable Icon buttonIcon, @Nullable String buttonText, @Nullable String confirmMessage,
            @Nullable UIActionHandler handler) {
            this.buttonIcon = buttonIcon;
            this.buttonText = buttonText;
            this.confirmMessage = confirmMessage;
            this.handler = handler;

            this.actionEntityID = key;
            return this;
        }

        @Override
        public NotificationInfoLineBuilder setAsLink(BaseEntity entity) {
            link = entity.getEntityID();
            linkType = UIFieldUtils.getClassEntityNavLink(entity.getTitle(), entity.getClass());
            return this;
        }

        public String getBorderColor() {
            if (this.borderColor == null) {
                return this.statusColor;
            }
            return borderColor;
        }

        public void updateTextInfo(String info) {
            this.info = info;
        }
    }
}
