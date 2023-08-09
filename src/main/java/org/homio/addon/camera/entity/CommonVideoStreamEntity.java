package org.homio.addon.camera.entity;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.addon.camera.service.BaseVideoService;
import org.homio.addon.camera.service.CommonVideoService;
import org.homio.api.EntityContext;
import org.homio.api.model.Icon;
import org.homio.api.ui.field.UIField;
import org.jetbrains.annotations.NotNull;

@Setter
@Getter
@Entity
@Accessors(chain = true)
public class CommonVideoStreamEntity extends BaseVideoEntity<CommonVideoStreamEntity, CommonVideoService>
    implements AbilityToStreamHLSOverFFMPEG<CommonVideoStreamEntity> {

    @Override
    @UIField(order = 5, label = "url", inlineEdit = true, required = true)
    public String getIeeeAddress() {
        return super.getIeeeAddress();
    }

    @Override
    public String getFolderName() {
        return "video";
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    public String toString() {
        return getIeeeAddress();
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "vstream";
    }

    @Override
    protected void beforePersist() {
        setSnapshotOutOptions("-update 1~~~-frames:v 1");
        setServerPort(BaseVideoService.findFreeBootstrapServerPort());
    }

    @Override
    public Icon getEntityIcon() {
        return new Icon("fas fa-film", "#4E783D");
    }

    @Override
    public @NotNull Class<CommonVideoService> getEntityServiceItemClass() {
        return CommonVideoService.class;
    }

    @Override
    public CommonVideoService createService(@NotNull EntityContext entityContext) {
        return new CommonVideoService(entityContext, this);
    }

    @Override
    public long getVideoParametersHashCode() {
        return super.getVideoParametersHashCode() +
            (getIeeeAddress() == null ? 0 : getIeeeAddress().hashCode()) +
            getJsonDataHashCode("extraOpts", "hlsListSize", "vcodec", "acodec", "hls_scale");
    }
}
