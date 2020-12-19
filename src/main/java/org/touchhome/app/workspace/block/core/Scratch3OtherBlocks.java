package org.touchhome.app.workspace.block.core;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.workspace.scratch.MenuBlock;
import org.touchhome.bundle.api.workspace.scratch.Scratch3Block;
import org.touchhome.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.touchhome.bundle.api.workspace.scratch.Scratch3OtherBlocksHolder;

import java.util.*;

@Log4j2
@Component
public class Scratch3OtherBlocks extends Scratch3ExtensionBlocks {

    private final Set<Scratch3Block> otherBlocks = new HashSet<>();
    private final Map<String, MenuBlock> otherMenus = new HashMap<>();

    public Scratch3OtherBlocks(EntityContext entityContext) {
        super("#24694A", entityContext, null, "other");
    }

    public void postConstruct() {
        otherBlocks.clear();
        otherMenus.clear();
        Collection<Scratch3OtherBlocksHolder> beans = entityContext.getBeansOfType(Scratch3OtherBlocksHolder.class);
        for (Scratch3OtherBlocksHolder block : beans) {
            otherBlocks.addAll(block.getScratch3Blocks());
            otherMenus.putAll(block.createScratch3Menus());
        }
        this.postConstruct(new ArrayList<>(otherBlocks), otherMenus);
    }
}
