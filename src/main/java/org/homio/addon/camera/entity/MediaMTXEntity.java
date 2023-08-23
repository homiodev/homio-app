package org.homio.addon.camera.entity;

import static org.homio.api.util.Constants.PRIMARY_DEVICE;

import jakarta.persistence.Entity;
import java.nio.file.Files;
import lombok.extern.log4j.Log4j2;
import org.homio.api.EntityContext;
import org.homio.api.entity.log.HasEntityLog;
import org.homio.api.entity.types.MediaEntity;
import org.homio.api.model.Icon;
import org.homio.api.repository.GitHubProject;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.jetbrains.annotations.NotNull;

@Log4j2
@Entity
@UISidebarChildren(icon = "fas fa-square-rss", color = "#308BB3", allowCreateItem = false)
public final class MediaMTXEntity extends MediaEntity implements HasEntityLog {

    private static final GitHubProject mediamtxGitHub = GitHubProject.of("bluenviron", "mediamtx");

    public static MediaMTXEntity ensureEntityExists(EntityContext entityContext) {
        MediaMTXEntity entity = entityContext.getEntity(MediaMTXEntity.class, PRIMARY_DEVICE);
        if (entity == null) {
            entity = new MediaMTXEntity();
            entity.setEntityID(PRIMARY_DEVICE);
            entity.setJsonData("dis_del", true);
            entityContext.save(entity);
        }
        if (!Files.exists(mediamtxGitHub.getLocalProjectPath())) {
            entityContext.event().runOnceOnInternetUp("download-mediamtx", () -> {
                String version = mediamtxGitHub.getLastReleaseVersion();
                if (version != null) {
                    mediamtxGitHub.downloadReleaseAndInstall(entityContext, version, (progress, message, error) -> {
                        log.info(message);
                    });
                }
            });
        }
        return entity;
    }

    @Override
    public String toString() {
        return "MediaMTX" + getTitle();
    }

    @Override
    public String getDefaultName() {
        return "MediaMTX server";
    }

    @Override
    public void logBuilder(@NotNull EntityLogBuilder builder) {
        builder.logToSeparateFile(true);
        builder.addTopicFilterByEntityID(MediaMTXEntity.class);
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "mediamtx";
    }

    @Override
    public @NotNull Icon getEntityIcon() {
        return new Icon("fas fa-square-rss", "#308BB3");
    }

    @Override
    public void assembleActions(UIInputBuilder uiInputBuilder) {

    }
}
