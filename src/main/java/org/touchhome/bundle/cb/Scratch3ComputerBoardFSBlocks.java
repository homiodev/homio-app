package org.touchhome.bundle.cb;

import lombok.Getter;
import org.springframework.stereotype.Component;
import org.touchhome.app.cb.ComputerBoardEntity;
import org.touchhome.bundle.api.BundleEntrypoint;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.storage.Scratch3BaseFileSystemExtensionBlocks;
import org.touchhome.bundle.cb.Scratch3ComputerBoardFSBlocks.FakeEntrypoint;

@Getter
@Component
public class Scratch3ComputerBoardFSBlocks extends Scratch3BaseFileSystemExtensionBlocks<FakeEntrypoint, ComputerBoardEntity> {

    public Scratch3ComputerBoardFSBlocks(EntityContext entityContext) {
        super("#93922C", entityContext, new FakeEntrypoint(), ComputerBoardEntity.class);
    }

    public static class FakeEntrypoint implements BundleEntrypoint {

        @Override
        public void init() {

        }

        @Override
        public String getBundleId() {
            return "local";
        }

        @Override
        public int order() {
            return 0;
        }
    }
}
