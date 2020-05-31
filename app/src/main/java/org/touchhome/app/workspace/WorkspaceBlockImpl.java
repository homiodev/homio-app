package org.touchhome.app.workspace;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Level;
import org.json.JSONArray;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.model.workspace.WorkspaceStandaloneVariableEntity;
import org.touchhome.bundle.api.util.NotificationType;
import org.touchhome.bundle.api.scratch.*;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.touchhome.bundle.api.util.NotificationType.danger;

@Setter
@Log4j2
public class WorkspaceBlockImpl implements WorkspaceBlock {
    private final Map<String, WorkspaceBlock> allBlocks;
    private final Map<String, Scratch3ExtensionBlocks> scratch3Blocks;
    @Getter
    private final String id;
    @Getter
    private EntityContext entityContext;
    private String extensionId;

    @Getter
    private String opcode;

    @Getter
    private WorkspaceBlock next;

    private WorkspaceBlock parent;

    @Getter
    private Map<String, JSONArray> inputs = new HashMap<>();

    @Getter
    private Map<String, JSONArray> fields = new HashMap<>();

    @Getter
    private boolean shadow;

    @Getter
    private boolean topLevel;

    @Setter
    private Consumer<String> stateHandler;

    WorkspaceBlockImpl(String id, Map<String, WorkspaceBlock> allBlocks, Map<String, Scratch3ExtensionBlocks> scratch3Blocks, EntityContext entityContext) {
        this.id = id;
        this.allBlocks = allBlocks;
        this.scratch3Blocks = scratch3Blocks;
        this.entityContext = entityContext;
    }

    @Override
    public void logError(String message, Object... params) {
        log(Level.ERROR, message, params);
        String msg = log.getMessageFactory().newMessage(message, params).getFormattedMessage();
        this.entityContext.sendNotification(msg, "", NotificationType.danger);
    }

    @Override
    public void logErrorAndThrow(String message, Object... params) {
        logError(message, params);
        throw new IllegalStateException(message);
    }

    @Override
    public void logWarn(String message, Object... params) {
        log(Level.WARN, message, params);
    }

    @Override
    public void logInfo(String message, Object... params) {
        log(Level.INFO, message, params);
    }

    private void log(Level level, String message, Object... params) {
        log.log(level, "[" + this.extensionId + " -> " + this.opcode + "] - " + message, params);
    }

    void setOpcode(String opcode) {
        this.extensionId = opcode.contains("_") ? opcode.substring(0, opcode.indexOf("_")) : "";
        this.opcode = opcode.contains("_") ? opcode.substring(opcode.indexOf("_") + 1) : opcode;
    }

    @Override
    public <P> P getMenuValue(String key, MenuBlock menuBlock, Class<P> type) {
        String menuId = this.inputs.get(key).getString(1);
        WorkspaceBlock refWorkspaceBlock = allBlocks.get(menuId);
        String fieldValue = refWorkspaceBlock.getField(menuBlock.getName());
        if (Enum.class.isAssignableFrom(type)) {
            for (P p : type.getEnumConstants()) {
                if (((Enum<?>) p).name().equals(fieldValue)) {
                    return p;
                }
            }
        } else if (String.class.isAssignableFrom(type)) {
            return (P) fieldValue;
        } else if (BaseEntity.class.isAssignableFrom(type)) {
            return (P) entityContext.getEntity(fieldValue);
        }
        logErrorAndThrow("Unable to handle menu value with type: " + type.getSimpleName());
        return null;
    }

    @Override
    public String findField(Predicate<String> predicate) {
        return fields.keySet().stream().filter(predicate).findAny().orElse(null);
    }

    @Override
    public String getField(String fieldName) {
        return this.fields.get(fieldName).getString(0);
    }

    @Override
    public String getFieldId(String fieldName) {
        return this.fields.get(fieldName).getString(1);
    }

    @Override
    public boolean hasField(String fieldName) {
        return this.fields.containsKey(fieldName);
    }

    @Override
    public void handle() {
        this.handleInternal(scratch3Block -> {
            scratch3Block.getHandler().handle(this);
            if (this.next != null && scratch3Block.getBlockType() != BlockType.hat) {
                this.next.handle();
            }
            return null;
        });
    }

    @Override
    public Object evaluate() {
        return this.handleInternal(scratch3Block -> scratch3Block.getEvaluateHandler().handle(this));
    }

    private Object handleInternal(Function<Scratch3Block, Object> function) {
        return function.apply(getScratch3Block());
    }

    private Scratch3Block getScratch3Block() {
        Scratch3ExtensionBlocks scratch3ExtensionBlocks = scratch3Blocks.get(extensionId);
        if (scratch3ExtensionBlocks == null) {
            logErrorAndThrow(sendScratch3ExtensionNotFound(extensionId));
        } else {
            Scratch3Block scratch3Block = scratch3ExtensionBlocks.getBlocksMap().get(opcode);
            if (scratch3Block == null) {
                logErrorAndThrow(sendScratch3BlockNotFound(extensionId, opcode));
            }
            return scratch3Block;
        }
        // actually unreachable code
        throw new RuntimeException();
    }

    @Override
    public Integer getInputInteger(String key) {
        return getInputFloat(key).intValue();
    }

    @Override
    public Float getInputFloat(String key) {
        String value = getInputString(key);
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ex) {
            logErrorAndThrow("Unable parse value <" + key + "> to double for block: <" + this.opcode + ">. Actual value: <" + value + ">.");
            return null;
        }
    }

    @Override
    public String getInputString(String key) {
        return String.valueOf(getInput(key, true));
    }

    @Override
    public boolean getInputBoolean(String key) {
        return (boolean) this.allBlocks.get(cast(getInput(key, false))).evaluate();
    }

    @Override
    public WorkspaceBlock getInputWorkspaceBlock(String key) {
        return this.allBlocks.get(cast(getInput(key, false)));
    }

    private String cast(Object object) {
        return (String) object;
    }

    @Override
    public Object getInput(String key, boolean fetchValue) {
        JSONArray objects = this.inputs.get(key);
        JSONArray array;

        switch (objects.getInt(0)) {
            case 5: // direct value
                return objects.getString(1);
            case 3: // ref to another block
                String ref;
                // sometimes it may be array, not plain string
                array = objects.optJSONArray(1);
                if (array != null) {
                    PrimitiveRef primitiveRef = PrimitiveRef.values()[array.getInt(0)];
                    if (fetchValue) {
                        return primitiveRef.fetchValue(array, entityContext);
                    } else {
                        return primitiveRef.getRef(array).toString();
                    }
                } else {
                    ref = objects.getString(1);
                    return fetchValue ? String.valueOf(this.allBlocks.get(ref).evaluate()) : ref;
                }
            case 1:
                array = objects.optJSONArray(1);
                if (array != null) {
                    return PrimitiveRef.values()[array.getInt(0)].getRef(array);
                }
                return PrimitiveRef.values()[objects.getInt(0)].getRef(objects);
            case 2: // just a reference
                String reference = objects.getString(1);
                return fetchValue ? allBlocks.get(reference).evaluate() : reference;
            default:
                logErrorAndThrow("Unable to fetch/parse integer value from input with key: " + key);
                return null;
        }
    }

    @Override
    public boolean hasInput(String key) {
        JSONArray objects = this.inputs.get(key);
        if (objects == null) {
            return false;
        }
        switch (objects.getInt(0)) {
            case 5:
                return true;
            case 3:
                return true;
            case 1:
                return !objects.isNull(1);
            case 2:
                return true;
            default:
                logErrorAndThrow("Unable to fetch/parse integer value from input with key: " + key);
                return false;
        }
    }

    @Override
    public String getDescription() {
        return this.opcode;
    }

    @Override
    public void setState(String state) {
        this.stateHandler.accept(state);
    }

    @Override
    public void release() {

    }

    private String sendScratch3ExtensionNotFound(String extensionId) {
        String msg = "No scratch extension <" + extensionId + "> found";
        entityContext.sendNotification("WORKSPACE.SCRATCH_NOT_FOUND", extensionId, danger);
        return msg;
    }

    private String sendScratch3BlockNotFound(String extensionId, String opcode) {
        String msg = "No scratch block <" + opcode + "> found in extension <" + extensionId + ">";
        entityContext.sendNotification("WORKSPACE.SCRATCH_BLOCK_NOT_FOUND", opcode, danger);
        return msg;
    }

    @Override
    public String toString() {
        return "WorkspaceBlockImpl{" +
                "id='" + id + '\'' +
                ", extensionId='" + extensionId + '\'' +
                ", opcode='" + opcode + '\'' +
                '}';
    }

    public void linkBoolean(String variableId) {
        Scratch3Block scratch3Block = getScratch3Block();
        if (scratch3Block.getAllowLinkBoolean() == null) {
            logErrorAndThrow("Unable to link boolean variable to scratch block: " + scratch3Block.getOpcode());
        }
        scratch3Block.getAllowLinkBoolean().accept(variableId, this);
    }

    public void linkVariable(String variableId) {
        Scratch3Block scratch3Block = getScratch3Block();
        if (scratch3Block.getAllowLinkVariable() == null) {
            logErrorAndThrow("Unable to link boolean variable to scratch block: " + scratch3Block.getOpcode());
        }
        scratch3Block.getAllowLinkVariable().accept(variableId, this);
    }

    @AllArgsConstructor
    @NoArgsConstructor
    private enum PrimitiveRef {
        UNDEFINED,
        INPUT_SAME_BLOCK_SHADOW,
        INPUT_BLOCK_NO_SHADOW,
        INPUT_DIFF_BLOCK_SHADOW,
        MATH_NUM_PRIMITIVE,
        POSITIVE_NUM_PRIMITIVE,
        WHOLE_NUM_PRIMITIVE,
        INTEGER_NUM_PRIMITIVE,
        ANGLE_NUM_PRIMITIVE,
        COLOR_PICKER_PRIMITIVE,
        TEXT_PRIMITIVE,
        BROADCAST_PRIMITIVE(array -> array.get(2), (array, entityContext) -> {
            return array.get(2);
        }),
        VAR_PRIMITIVE(array -> array.get(2), (array, entityContext) -> {
            WorkspaceStandaloneVariableEntity entity = entityContext.getEntity(WorkspaceStandaloneVariableEntity.PREFIX + array.get(2));
            if (entity == null) {
                throw new IllegalArgumentException("Unable to find variable with name: " + array.get(1));
            }
            return StringUtils.defaultIfEmpty(String.valueOf(entity.getValue()), "0");
        }),
        LIST_PRIMITIVE;

        private Function<JSONArray, Object> refFn = array -> array.getString(1);

        private BiFunction<JSONArray, EntityContext, Object> valueFn = (array, entityContext) -> array.getString(1);

        public Object getRef(JSONArray array) {
            return refFn.apply(array);
        }

        public Object fetchValue(JSONArray array, EntityContext entityContext) {
            return valueFn.apply(array, entityContext);
        }
    }
}
