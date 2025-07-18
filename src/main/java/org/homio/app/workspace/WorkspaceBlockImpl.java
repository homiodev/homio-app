package org.homio.app.workspace;

import com.pivovarit.function.ThrowingRunnable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Level;
import org.homio.api.Context;
import org.homio.api.ContextBGP;
import org.homio.api.entity.BaseEntity;
import org.homio.api.exception.ServerException;
import org.homio.api.state.RawType;
import org.homio.api.state.State;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.DataSourceUtil;
import org.homio.api.workspace.LockManager;
import org.homio.api.workspace.WorkspaceBlock;
import org.homio.api.workspace.scratch.BlockType;
import org.homio.api.workspace.scratch.MenuBlock;
import org.homio.api.workspace.scratch.Scratch3Block;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.homio.app.service.FileSystemService;
import org.homio.app.workspace.WorkspaceService.WorkspaceTabHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.homio.api.entity.HasJsonData.LEVEL_DELIMITER;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;

@Setter
@Log4j2
public class WorkspaceBlockImpl implements WorkspaceBlock {

  @Getter
  private final @NotNull String id;

  @Getter
  private final @NotNull WorkspaceTabHolder workspaceTabHolder;

  @Getter
  private String extensionId;

  @Getter
  private String opcode;

  @Getter
  private WorkspaceBlock next;

  @Getter
  private WorkspaceBlockImpl parent;

  @Getter
  private @NotNull Map<String, JSONArray> inputs = new HashMap<>();

  @Getter
  private @NotNull Map<String, JSONArray> fields = new HashMap<>();

  @Getter
  private boolean shadow;

  @Getter
  private boolean topLevel;

  private @NotNull Map<String, State> values = new HashMap<>();

  private AtomicReference<State> lastChildValue;

  private boolean destroy;

  private List<ThrowingRunnable> releaseListeners;

  private ContextBGP.ThreadContext<?> threadContext;

  @Getter
  private @Nullable String procedureCode;

  @Getter
  private @NotNull List<String> procedureArgumentIds = List.of();

  WorkspaceBlockImpl(@NotNull String id, @NotNull WorkspaceTabHolder workspaceTabHolder) {
    this.id = id;
    this.workspaceTabHolder = workspaceTabHolder;
  }

  public static Float objectToFloat(Object value, Float defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    if (value instanceof Number) {
      return ((Number) value).floatValue();
    }
    try {
      return NumberFormat.getInstance().parse(valueToStr(value, "0")).floatValue();
    } catch (ParseException ex) {
      log.error("Unable to convert value '{}' to float", value);
      return defaultValue;
    }
  }

  private static String valueToStr(Object content, String defaultValue) {
    if (content != null) {
      if (content instanceof State) {
        return ((State) content).stringValue();
      } else if (content instanceof byte[]) {
        return new String((byte[]) content);
      } else {
        return content.toString();
      }
    }
    return defaultValue;
  }

  @Override
  public void logError(String message, Object... params) {
    log(Level.ERROR, message, params);
    String msg = log.getMessageFactory().newMessage(message, params).getFormattedMessage();
    context().ui().toastr().error(msg);
  }

  @Override
  public void logErrorAndThrow(String message, Object... params) {
    String msg = log.getMessageFactory().newMessage(message, params).getFormattedMessage();
    log(Level.ERROR, msg);
    throw new ServerException(msg);
  }

  @Override
  public void logWarn(String message, Object... params) {
    String msg = log.getMessageFactory().newMessage(message, params).getFormattedMessage();
    log(Level.WARN, msg);
    context().ui().toastr().warn(msg);
  }

  @Override
  public void logInfo(String message, Object... params) {
    log(Level.INFO, message, params);
  }

  @Override
  public <P> List<P> getMenuValues(
    String key, MenuBlock menuBlock, Class<P> type, String delimiter) {
    String menuId = this.inputs.get(key).getString(1);
    WorkspaceBlock refWorkspaceBlock = workspaceTabHolder.getBlocks().get(menuId);
    String value = refWorkspaceBlock.getField(menuBlock.getName());
    List<String> items = Stream.of(value.split(delimiter)).collect(Collectors.toList());
    List<P> result = new ArrayList<>();
    if (Enum.class.isAssignableFrom(type)) {
      for (P p : type.getEnumConstants()) {
        if (items.contains(((Enum<?>) p).name())) {
          result.add(p);
        }
      }
      return result;
    } else if (String.class.isAssignableFrom(type)) {
      return items.stream().map(item -> (P) item).collect(Collectors.toList());
    } else if (Long.class.isAssignableFrom(type)) {
      return items.stream().map(item -> (P) Long.valueOf(item)).collect(Collectors.toList());
    } else if (BaseEntity.class.isAssignableFrom(type)) {
      return items.stream()
        .map(item -> (P) context().db().get(item))
        .collect(Collectors.toList());
    }
    logErrorAndThrow("Unable to handle menu value with type: " + type.getSimpleName());
    return null; // unreachable block
  }

  @Override
  public <P> P getMenuValue(String key, MenuBlock menuBlock, Class<P> type) {
    P value = getMenuValueInternal(key, menuBlock, type);
    if (menuBlock instanceof MenuBlock.ServerMenuBlock smb) {
      if (smb.isRequire()
          && (value == null
              || value.toString().isEmpty()
              || value.toString().equals("-"))) {
        logErrorAndThrow(smb.getFirstKey() + " menu value not found");
      }
    }
    return value;
  }

  @Override
  public Resource getFile(String key, MenuBlock menuBlock, boolean required) {
    WorkspaceBlock refBlock = getInputWorkspaceBlock(key);
    Path result = null;
    FileSystemService fileSystemService = context().getBean(FileSystemService.class);
    if (refBlock.hasField(menuBlock.getName())) {
      String menuValue = getMenuValue(key, menuBlock, String.class);
      String[] keys = menuValue.split(LEVEL_DELIMITER);
      int alias = Integer.parseInt(keys[1]);
      return fileSystemService.getFileSystem(keys[0], alias).getEntryResource(keys[2]);
    } else {
      Object evaluate = refBlock.evaluate();
      if (evaluate instanceof RawType rawType) {
        result = rawType.toPath();
      }
    }
    if (required && (result == null || !Files.isReadable(result))) {
      logErrorAndThrow("Unable to evaluate file for: <{}>", this.opcode);
    }
    return result != null ? new FileSystemResource(result.toFile()) : null;
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
  public boolean getFieldBoolean(String fieldName) {
    return this.fields.get(fieldName).getBoolean(0);
  }

  @Override
  public String getFieldId(String fieldName) {
    return this.fields.get(fieldName).optString(1);
  }

  @Override
  public boolean hasField(String fieldName) {
    return this.fields.containsKey(fieldName);
  }

  @SneakyThrows
  @Override
  public <T> T getSetting(Class<T> settingClass) {
    String ref = getInputString("SETTING");
    String content = workspaceTabHolder.getBlocks().get(ref).getField("TEXT");
    return OBJECT_MAPPER.readValue(content, settingClass);
  }

  @Override
  public void setValue(String key, State value) {
    if (key == null) {
      logErrorAndThrow("Trying set value for workspace block with null key");
    }
    this.values.put(key, value);
  }

  @Override
  public void handle() {
    this.handleInternal(
      scratch3Block -> {
        try {
          scratch3Block.getHandler().handle(this);
        } catch (Exception ex) {
          String err = "Workspace " + scratch3Block.getOpcode() + " scratch error\n" + CommonUtils.getErrorMessage(ex);
          context().ui().toastr().error(err, ex);
          log.error(err);
          return null;
        }
        if (this.next != null && scratch3Block.getBlockType() != BlockType.hat) {
          this.next.handle();
        }
        return null;
      });
  }

  public void handleOrEvaluate() {
    if (getScratch3Block().getHandler() != null) {
      this.handle();
    } else {
      this.evaluate();
    }
  }

  @Override
  public State evaluate() {
    return this.handleInternal(
      scratch3Block -> {
        try {
          return this.setValue(scratch3Block.getEvaluateHandler().handle(this));
        } catch (Exception ex) {
          context().ui().toastr().error("Workspace " + scratch3Block.getOpcode() + " scratch error", ex);
          throw new ServerException(ex);
        }
      });
  }

  @Override
  public void setActiveWorkspace() {
    getNearestLiveThread().setMetadata("activeWorkspaceId", id);
  }

  public Scratch3Block getScratch3Block() {
    Scratch3ExtensionBlocks scratch3ExtensionBlocks =
      workspaceTabHolder.getScratch3Blocks().get(extensionId);
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
    throw new ServerException("unreachable code");
  }

  @Override
  public Integer getInputInteger(String key) {
    return getInputFloat(key).intValue();
  }

  @Override
  public Float getInputFloat(String key, Float defaultValue) {
    return objectToFloat(getInput(key, true), defaultValue);
  }

  @SneakyThrows
  @Override
  public JSONObject getInputJSON(String key, JSONObject defaultValue) {
    Object item = getInput(key, true);
    if (item != null) {
      if (JSONObject.class.isAssignableFrom(item.getClass())) {
        return (JSONObject) item;
      } else if (item instanceof String) {
        return new JSONObject((String) item);
      } else {
        return new JSONObject(OBJECT_MAPPER.writeValueAsString(item));
      }
    }
    return defaultValue;
  }

  @Override
  public String getInputString(String key, String defaultValue) {
    return valueToStr(getInput(key, true), defaultValue);
  }

  @Override
  public State getValue(String key) {
    if (values.containsKey(key)) {
      return values.get(key);
    }
    return parent == null ? null : parent.getValue(key);
  }

  @Override
  public byte[] getInputByteArray(String key, byte[] defaultValue) {
    Object content = getInput(key, true);
    if (content != null) {
      if (content instanceof State) {
        return ((State) content).byteArrayValue();
      } else if (content instanceof byte[]) {
        return (byte[]) content;
      } else {
        return content.toString().getBytes(Charset.defaultCharset());
      }
    }
    return defaultValue;
  }

  @Override
  public boolean getInputBoolean(String key) {
    Object input = getInput(key, false);
    if (input instanceof Boolean) {
      return (boolean) input;
    }
    return workspaceTabHolder.getBlocks().get(cast(input)).evaluate().boolValue();
  }

  @Override
  public WorkspaceBlock getInputWorkspaceBlock(String key) {
    return workspaceTabHolder.getBlocks().get(cast(getInput(key, false)));
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
            return primitiveRef.fetchValue(array, context());
          } else {
            return primitiveRef.getRef(array).toString();
          }
        } else {
          ref = objects.getString(1);
          if (fetchValue) {
            Object evaluateValue = workspaceTabHolder.getBlocks().get(ref).evaluate();
            this.lastChildValue = new AtomicReference<>(State.of(evaluateValue));
            return evaluateValue;
          }
          return ref;
        }
      case 1:
        array = objects.optJSONArray(1);
        if (array != null) {
          return PrimitiveRef.values()[array.getInt(0)].getRef(array);
        }
        return PrimitiveRef.values()[objects.getInt(0)].getRef(objects);
      case 2: // just a reference
        String reference = objects.getString(1);
        return fetchValue
          ? workspaceTabHolder.getBlocks().get(reference).evaluate()
          : reference;
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
    return switch (objects.getInt(0)) {
      case 5, 3, 2 -> true;
      case 1 -> !objects.isNull(1);
      default -> {
        logErrorAndThrow("Unable to fetch/parse integer value from input with key: " + key);
        yield false;
      }
    };
  }

  @Override
  public String getDescription() {
    return this.opcode;
  }

  public LockManager getLockManager() {
    return workspaceTabHolder.getLockManager();
  }

  @Override
  public boolean isDestroyed() {
    return destroy;
  }

  public void release() {
    this.destroy = true;
    if (this.threadContext != null) {
      this.threadContext.cancel();
    }
    if (this.releaseListeners != null) {
      this.releaseListeners.forEach(
        t -> {
          try {
            t.run();
          } catch (Exception ex) {
            log.error("Error occurs while release listener: <%s>", ex);
          }
        });
    }
    if (this.parent != null) {
      this.parent.release();
    }
  }

  @Override
  public void onRelease(ThrowingRunnable listener) {
    if (this.releaseListeners == null) {
      this.releaseListeners = new ArrayList<>();
    }
    this.releaseListeners.add(listener);
  }

  @Override
  public String toString() {
    return "WorkspaceBlockImpl{id='%s', extensionId='%s', opcode='%s'}".formatted(id, extensionId, opcode);
  }

  public State getLastValue() {
    WorkspaceBlockImpl parent = this.parent;
    while (parent != null) {
      State lastValue = parent.getValue("value");
      if (lastValue != null || parent.lastChildValue != null) {
        return lastValue == null ? parent.lastChildValue.get() : lastValue;
      }
      parent = parent.parent;
    }
    return null;
  }

  public void addLock(@NotNull LockImpl lock) {
    lock.addSignalListener(
      value -> {
        if (value instanceof Collection<?> col && ((Collection<?>) value).size() > 1) {
          String key = null;
          for (Object item : col) {
            if (key == null) {
              key = (String) item;
            } else {
              this.setValue(key, State.of(item));
              key = null;
            }
          }
        }
        this.setValue(State.of(value));
      });
  }

  public void setThreadContext(@NotNull ContextBGP.ThreadContext<?> threadContext) {
    this.threadContext = threadContext;
    threadContext.setMetadata("workspace", id);
    threadContext.setMetadata("activeWorkspaceId", id);
  }

  public Context context() {
    return workspaceTabHolder.context();
  }

  void setOpcode(String opcode) {
    this.extensionId = opcode.contains("_") ? opcode.substring(0, opcode.indexOf("_")) : "";
    this.opcode = opcode.contains("_") ? opcode.substring(opcode.indexOf("_") + 1) : opcode;
  }

  private void log(Level level, String message, Object... params) {
    log.log(level, "[" + this.extensionId + " -> " + this.opcode + "] - " + message, params);
  }

  private <P> P getMenuValueInternal(String key, MenuBlock menuBlock, Class<P> type) {
    String menuId = this.inputs.get(key).getString(1);
    WorkspaceBlock refWorkspaceBlock = workspaceTabHolder.getBlocks().get(menuId);
    String fieldValue = refWorkspaceBlock.getField(menuBlock.getName());
    if (Enum.class.isAssignableFrom(type)) {
      for (P p : type.getEnumConstants()) {
        if (((Enum<?>) p).name().equals(fieldValue)) {
          return p;
        }
      }
    } else if (String.class.isAssignableFrom(type)) {
      return (P) fieldValue;
    } else if (Long.class.isAssignableFrom(type)) {
      return (P) Long.valueOf(fieldValue);
    } else if (BaseEntity.class.isAssignableFrom(type)) {
      return (P) context().db().get(fieldValue);
    }
    logErrorAndThrow("Unable to handle menu value with type: " + type.getSimpleName());
    return null; // unreachable block
  }

  private State handleInternal(Function<Scratch3Block, State> function) {
    setActiveWorkspace();
    return function.apply(getScratch3Block());
  }

  private ContextBGP.ThreadContext<?> getNearestLiveThread() {
    if (this.threadContext != null) {
      return this.threadContext;
    } else if (this.parent != null) {
      return this.parent.getNearestLiveThread();
    }
    throw new RuntimeException("Must be never calls");
  }

  private String cast(Object object) {
    return (String) object;
  }

  private String sendScratch3ExtensionNotFound(String extensionId) {
    String msg = "No scratch extension <" + extensionId + "> found";
    context().ui().toastr().error(msg, extensionId);
    return msg;
  }

  private String sendScratch3BlockNotFound(String extensionId, String opcode) {
    String msg = "No scratch block <" + opcode + "> found in extension <" + extensionId + ">";
    context().ui().toastr().error("W.ERROR.SCRATCH_BLOCK_NOT_FOUND", opcode);
    return msg;
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
    CHECKBOX_NUM_PRIMITIVE(
      array -> array.getBoolean(1),
      (array, context) -> {
        return array.get(2);
      }),
    COLOR_PICKER_PRIMITIVE,
    TEXT_PRIMITIVE,
    BROADCAST_PRIMITIVE(
      array -> array.get(2),
      (array, context) -> {
        return array.get(2);
      }),
    VAR_PRIMITIVE(array -> array.get(2), (array, context) -> {
      String varEntityID = DataSourceUtil.getSelection(array.get(2).toString()).getEntityValue();
      return State.of(context.var().getRawValue(varEntityID));
    }),
    LIST_PRIMITIVE,
    FONT_AWESOME_PRIMITIVE;

    private Function<JSONArray, Object> refFn = array -> array.getString(1);

    private BiFunction<JSONArray, Context, Object> valueFn =
      (array, context) -> array.getString(1);

    public Object getRef(JSONArray array) {
      return refFn.apply(array);
    }

    public Object fetchValue(JSONArray array, Context context) {
      return valueFn.apply(array, context);
    }
  }
}
