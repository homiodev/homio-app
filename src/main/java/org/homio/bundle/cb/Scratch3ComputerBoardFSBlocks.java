package org.homio.bundle.cb;

import lombok.Getter;
import org.homio.app.cb.ComputerBoardEntity;
import org.homio.bundle.api.BundleEntrypoint;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.entity.storage.Scratch3BaseFileSystemExtensionBlocks;
import org.homio.bundle.cb.Scratch3ComputerBoardFSBlocks.FakeEntrypoint;
import org.springframework.stereotype.Component;

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
