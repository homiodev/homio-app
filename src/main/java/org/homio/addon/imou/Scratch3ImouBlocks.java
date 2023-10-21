package org.homio.addon.imou;

import static org.homio.addon.imou.ImouEntrypoint.IMOU_COLOR;
import static org.springframework.http.MediaType.IMAGE_JPEG_VALUE;

import lombok.Getter;
import org.homio.api.Context;
import org.homio.api.state.RawType;
import org.homio.api.workspace.scratch.Scratch3BaseDeviceBlocks;
import org.springframework.stereotype.Component;

@Getter
@Component
public class Scratch3ImouBlocks extends Scratch3BaseDeviceBlocks {

    public Scratch3ImouBlocks(Context context, ImouEntrypoint imouEntrypoint) {
        super(IMOU_COLOR, context, imouEntrypoint, ImouDeviceEntity.PREFIX);

        blockReporter(52, "img", "snapshot [DEVICE]",
            workspaceBlock -> {
                String ieeeAddress = workspaceBlock.getMenuValue(DEVICE, deviceMenu);
                ImouDeviceEntity entity = context.db().getEntityRequire(ieeeAddress);
                return new RawType(entity.getService().getSnapshot(), IMAGE_JPEG_VALUE);
            },
            block -> {
                block.addArgument(DEVICE, this.getDeviceMenu());
                block.overrideColor("#307596");
            });
    }
}
