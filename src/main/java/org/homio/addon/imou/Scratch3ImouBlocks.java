package org.homio.addon.imou;

import lombok.Getter;
import org.homio.api.EntityContext;
import org.homio.api.workspace.scratch.Scratch3BaseDeviceBlocks;
import org.springframework.stereotype.Component;

@Getter
@Component
public class Scratch3ImouBlocks extends Scratch3BaseDeviceBlocks {

    public Scratch3ImouBlocks(EntityContext entityContext, ImouEntrypoint imouEntrypoint) {
        super("#BF2A63", entityContext, imouEntrypoint);
    }
}
