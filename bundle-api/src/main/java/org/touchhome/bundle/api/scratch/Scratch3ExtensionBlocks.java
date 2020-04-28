package org.touchhome.bundle.api.scratch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.BaseEntity;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.*;
import java.util.function.Consumer;

@Getter
@SuppressWarnings("SpringJavaConstructorAutowiringInspection")
public class Scratch3ExtensionBlocks {

    public static final String EVENT = "EVENT";

    protected final EntityContext entityContext;
    private final String id;
    private final List<Scratch3Block> blocks = new ArrayList<>();
    private final Map<String, MenuBlock> menus = new HashMap<>();
    private final Map<String, Scratch3Block> blocksMap = new HashMap<>();
    @Setter
    private String name;
    private String blockIconURI;
    private Scratch3Color scratch3Color;

    @SneakyThrows
    public Scratch3ExtensionBlocks(String id, String color, EntityContext entityContext) {
        if (getClass().isAnnotationPresent(Scratch3Extension.class)) {
            this.id = getClass().getDeclaredAnnotation(Scratch3Extension.class).value();
        } else {
            this.id = id;
        }
        this.entityContext = entityContext;
        if (color != null) {
            URL resource = getClass().getClassLoader().getResource(this.id + ".png");
            if (resource == null) {
                throw new IllegalArgumentException("Unable to find Scratch3 image: " + this.id + ".png in classpath");
            }
            this.blockIconURI = "data:image/png;base64," + Base64.getEncoder().encodeToString(IOUtils.toByteArray(Objects.requireNonNull(resource)));
            this.scratch3Color = new Scratch3Color(color);
        }
    }

    public Scratch3ExtensionBlocks(String color, EntityContext entityContext) {
        this(null, color, entityContext);
    }

    public static void sendWorkspaceBooleanValueChangeValue(EntityContext entityContext, BaseEntity baseEntity, boolean value) {
        sendWorkspaceChangeValue(entityContext, baseEntity, "WorkspaceBooleanValue", node -> node.put("value", value));
    }

    public static void sendWorkspaceValueChangeValue(EntityContext entityContext, BaseEntity baseEntity, float value) {
        sendWorkspaceChangeValue(entityContext, baseEntity, "WorkspaceValue", node -> node.put("value", value));
    }

    public static void sendWorkspaceBackupValueChangeValue(EntityContext entityContext, BaseEntity baseEntity, float value) {
        sendWorkspaceChangeValue(entityContext, baseEntity, "WorkspaceBackupValue", node -> node.put("value", value));
    }

    private static void sendWorkspaceChangeValue(EntityContext entityContext, BaseEntity baseEntity, String type, Consumer<ObjectNode> fn) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = mapper.createObjectNode().put("block", baseEntity.getEntityID()).put("type", type);
        fn.accept(node);
        entityContext.sendNotification("-workspace-value", node);
    }

    @SneakyThrows
    public void postConstruct(Object... additionalExtensions) {
        assembleFields(this);
        for (Object additionalExtension : additionalExtensions) {
            assembleFields(additionalExtension);
        }

        Collections.sort(blocks);
        for (Scratch3Block block : blocks) {
            if (blocksMap.put(block.getOpcode(), block) != null) {
                throw new RuntimeException("Found multiple blocks with same opcode");
            }
        }
    }

    private void assembleFields(Object extensionObject) throws IllegalAccessException {
        for (Field field : FieldUtils.getAllFields(extensionObject.getClass())) {
            if (Scratch3Block.class.isAssignableFrom(field.getType())) {
                blocks.add((Scratch3Block) FieldUtils.readField(field, extensionObject, true));
            } else if (MenuBlock.class.isAssignableFrom(field.getType())) {
                MenuBlock menuBlock = (MenuBlock) FieldUtils.readField(field, extensionObject, true);
                menus.put(menuBlock.getName(), menuBlock);
            }
        }
    }
}
