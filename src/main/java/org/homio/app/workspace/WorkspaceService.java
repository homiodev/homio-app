package org.homio.app.workspace;

import static org.homio.api.util.Constants.PRIMARY_DEVICE;

import com.pivovarit.function.ThrowingRunnable;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.fs.Scratch3FSBlocks;
import org.homio.addon.hardware.Scratch3HardwareBlocks;
import org.homio.addon.http.Scratch3NetworkBlocks;
import org.homio.addon.media.Scratch3ImageEditBlocks;
import org.homio.addon.media.Scratch3MediaBlocks;
import org.homio.addon.media.Scratch3TextToSpeachBlocks;
import org.homio.addon.ui.Scratch3UIBlocks;
import org.homio.addon.weather.Scratch3WeatherBlocks;
import org.homio.api.AddonEntrypoint;
import org.homio.api.Context;
import org.homio.api.exception.ServerException;
import org.homio.api.util.CommonUtils;
import org.homio.api.workspace.WorkspaceBlock;
import org.homio.api.workspace.WorkspaceEventListener;
import org.homio.api.workspace.scratch.Scratch3Block;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.homio.app.manager.AddonService;
import org.homio.app.model.entity.WorkspaceEntity;
import org.homio.app.setting.workspace.WorkspaceClearButtonSetting;
import org.homio.app.spring.ContextRefreshed;
import org.homio.app.workspace.block.Scratch3Space;
import org.homio.app.workspace.block.core.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class WorkspaceService implements ContextRefreshed {

  private static final Pattern ID_PATTERN = Pattern.compile("[\\w-_]*");

  private static final List<Class<?>> systemScratches =
      Arrays.asList(
          Scratch3ProceduresArgumentBlocks.class,
          Scratch3ProceduresBlocks.class,
          Scratch3ControlBlocks.class,
          Scratch3MiscBlocks.class,
          Scratch3DataBlocks.class,
          Scratch3EventsBlocks.class,
          Scratch3OperatorBlocks.class,
          Scratch3DeviceBlocks.class,
          Scratch3MutatorBlocks.class);

  private static final List<Class<?>> inlineScratches =
      Arrays.asList(
          Scratch3MediaBlocks.class,
          Scratch3NetworkBlocks.class,
          Scratch3HardwareBlocks.class,
          Scratch3WeatherBlocks.class,
          Scratch3UIBlocks.class,
          Scratch3FSBlocks.class,
          Scratch3ImageEditBlocks.class,
          Scratch3TextToSpeachBlocks.class);

  private final Duration TIME_WAIT_OLD_WORKSPACE = Duration.ofSeconds(3);
  private final Set<String> ONCE_EXECUTION_BLOCKS =
      new HashSet<>(Arrays.asList("boolean_link", "group_variable_link"));
  // tab <-> list of top blocks
  private final Map<String, WorkspaceTabHolder> tabs = new HashMap<>();
  @Getter private final Set<Scratch3ExtensionImpl> extensions = new HashSet<>();
  // constructor parameters
  private final Context context;
  private final AddonService addonService;
  private Collection<WorkspaceEventListener> workspaceEventListeners;
  private Map<String, Scratch3ExtensionBlocks> scratch3Blocks;

  @Override
  public void onContextRefresh(Context context) {
    scratch3Blocks =
        this.context.getBeansOfType(Scratch3ExtensionBlocks.class).stream()
            .collect(Collectors.toMap(Scratch3ExtensionBlocks::getId, s -> s));
    workspaceEventListeners = this.context.getBeansOfType(WorkspaceEventListener.class);

    loadExtensions();
    loadWorkspace();
  }

  public boolean isEmpty(String content) {
    if (StringUtils.isEmpty(content)) {
      return true;
    }
    JSONObject target = new JSONObject(content).getJSONObject("target");
    for (String key : new String[] {"comments", "blocks"}) {
      if (target.has(key) && !target.getJSONObject(key).keySet().isEmpty()) {
        return false;
      }
    }
    return true;
  }

  public WorkspaceBlock getWorkspaceBlockById(String id) {
    for (WorkspaceTabHolder workspaceTabHolder : this.tabs.values()) {
      if (workspaceTabHolder.blocks.containsKey(id)) {
        return workspaceTabHolder.blocks.get(id);
      }
    }
    return null;
  }

  public void fireAllLock(Consumer<LockManagerImpl> handler) {
    for (WorkspaceTabHolder workspaceTabHolder : tabs.values()) {
      handler.accept(workspaceTabHolder.lockManager);
    }
  }

  public void registerScratch3Extension(Scratch3ExtensionBlocks scratch3ExtensionBlock) {
    initScratch3ExtensionBlocks(scratch3ExtensionBlock);
  }

  @SneakyThrows
  private synchronized void reloadWorkspace(WorkspaceEntity workspaceTab) {
    log.debug("Reloading workspace <{}>...", workspaceTab.getName());

    WorkspaceTabHolder workspaceTabHolder = tabs.remove(workspaceTab.getEntityID());
    if (workspaceTabHolder != null) {
      releaseWorkspaceEntity(workspaceTab, workspaceTabHolder);
      // wait to finish all nested processes if workspace started before
      log.info(
          "Wait workspace {}, {} to able to finish old one",
          workspaceTab.getTitle(),
          TIME_WAIT_OLD_WORKSPACE);
      Thread.sleep(TIME_WAIT_OLD_WORKSPACE.toMillis());
    }

    workspaceTabHolder =
        new WorkspaceTabHolder(workspaceTab.getEntityID(), context, scratch3Blocks);
    tabs.put(workspaceTab.getEntityID(), workspaceTabHolder);

    if (StringUtils.isNotEmpty(workspaceTab.getContent())) {
      try {
        parseWorkspace(workspaceTab, workspaceTabHolder);
        var topWorkspaces = workspaceTabHolder.blocks.values().stream()
                .filter(workspaceBlock -> workspaceBlock.isTopLevel() && !workspaceBlock.isShadow())
                        .toList();
        Predicate<WorkspaceBlock> procFilter = new Predicate<WorkspaceBlock>() {
          @Override
          public boolean test(WorkspaceBlock w) {
            return "procedures".equals(w.getExtensionId()) && "definition".equals(w.getOpcode());
          }
        };
        var procedureBlocks = topWorkspaces.stream().filter(procFilter).toList();
        var nonProcedureBlocks = topWorkspaces.stream().filter(w -> !procFilter.test(w)).toList();
        if(!procedureBlocks.isEmpty()) {
          procedureBlocks.parallelStream().forEach(this::fireTopWorkspaceBlock);
          // Allow procedure blocks to fire and lock for listen values
          Thread.sleep(100);
        }
          for (WorkspaceBlockImpl workspaceBlock : nonProcedureBlocks) {
            if (ONCE_EXECUTION_BLOCKS.contains(workspaceBlock.getOpcode())) {
              executeOnce(workspaceBlock);
            } else {
              fireTopWorkspaceBlock(workspaceBlock);
            }
          }
      } catch (Exception ex) {
        log.error("Unable to initialize workspace: {}", ex.getMessage(), ex);
        context.ui().toastr().error("Unable to initialize workspace: " + ex.getMessage(), ex);
      }
    }
  }

  private void fireTopWorkspaceBlock(WorkspaceBlock workspaceBlock) {
    this.context
            .bgp()
            .builder("workspace-" + workspaceBlock.getId())
            .tap(workspaceBlock::setThreadContext)
            .execute(createWorkspaceThread(workspaceBlock));
  }

  private ThrowingRunnable<Exception> createWorkspaceThread(WorkspaceBlock workspaceBlock) {
    return () -> {
      String name = workspaceBlock.getId();
      log.info("Workspace start thread: <{}>", name);
      try {
        ((WorkspaceBlockImpl) workspaceBlock).handleOrEvaluate();
      } catch (Exception ex) {
        log.warn(
            "Error in workspace thread: <{}>, <{}>", name, CommonUtils.getErrorMessage(ex), ex);
        context.ui().toastr().error("Error in workspace", ex);
      }
      log.info("Workspace thread finished: <{}>", name);
    };
  }

  private void releaseWorkspaceEntity(
      WorkspaceEntity workspaceTab, WorkspaceTabHolder oldWorkspaceTabHolder) {
    oldWorkspaceTabHolder.lockManager.release();

    for (WorkspaceEventListener workspaceEventListener : workspaceEventListeners) {
      workspaceEventListener.release(workspaceTab.getEntityID());
    }

    for (WorkspaceBlockImpl workspaceBlock : oldWorkspaceTabHolder.blocks.values()) {
      workspaceBlock.release();
    }
  }

  private void executeOnce(WorkspaceBlock workspaceBlock) {
    try {
      log.debug("Execute single block: <{}>", workspaceBlock);
      workspaceBlock.handle();
    } catch (Exception ex) {
      log.error("Error while execute single block: <{}>", workspaceBlock, ex);
    }
  }

  private void parseWorkspace(WorkspaceEntity workspaceTab, WorkspaceTabHolder workspaceTabHolder) {
    JSONObject jsonObject = new JSONObject(workspaceTab.getContent());
    JSONObject target = jsonObject.getJSONObject("target");

    JSONObject blocks = target.getJSONObject("blocks");

    for (String blockId : blocks.keySet()) {
      JSONObject block = blocks.optJSONObject(blockId);
      if (block == null) {
        continue;
      }

      if (!workspaceTabHolder.blocks.containsKey(blockId)) {
        workspaceTabHolder.blocks.put(blockId, new WorkspaceBlockImpl(blockId, workspaceTabHolder));
      }

      WorkspaceBlockImpl workspaceBlock = workspaceTabHolder.blocks.get(blockId);
      workspaceBlock.setShadow(block.optBoolean("shadow"));
      workspaceBlock.setTopLevel(block.getBoolean("topLevel"));
      workspaceBlock.setOpcode(block.getString("opcode"));
      workspaceBlock.setParent(getOrCreateWorkspaceBlock(workspaceTabHolder, block, "parent"));
      workspaceBlock.setNext(getOrCreateWorkspaceBlock(workspaceTabHolder, block, "next"));
      var mutation = block.optJSONObject("mutation");
      if(mutation != null) {
        workspaceBlock.setProcedureCode(mutation.getString("proccode"));
        List<String> ids = new JSONArray(mutation.getString("argumentids"))
                .toList().stream()
                .map(Object::toString)
                .collect(Collectors.toList());

        workspaceBlock.setProcedureArgumentIds(ids);
      }

      JSONObject fields = block.optJSONObject("fields");
      if (fields != null) {
        for (String fieldKey : fields.keySet()) {
          workspaceBlock.getFields().put(fieldKey, fields.getJSONArray(fieldKey));
        }
      }
      JSONObject inputs = block.optJSONObject("inputs");
      if (inputs != null) {
        for (String inputsKey : inputs.keySet()) {
          workspaceBlock.getInputs().put(inputsKey, inputs.getJSONArray(inputsKey));
        }
      }
    }
  }

  private WorkspaceBlockImpl getOrCreateWorkspaceBlock(
      WorkspaceTabHolder workspaceTabHolder, JSONObject block, String key) {
    if (block.has(key) && !block.isNull(key)) {
      workspaceTabHolder.blocks.putIfAbsent(
          block.getString(key), new WorkspaceBlockImpl(block.getString(key), workspaceTabHolder));
      return workspaceTabHolder.blocks.get(block.getString(key));
    }
    return null;
  }

  private void loadWorkspace() {
    try {
      reloadWorkspaces();
    } catch (Exception ex) {
      log.error("Unable to load workspace. Looks like workspace has incorrect value", ex);
    }
    context
        .event()
        .addEntityUpdateListener(
            WorkspaceEntity.class, "workspace-change-listener", this::reloadWorkspace);
    context
        .event()
        .addEntityRemovedListener(
            WorkspaceEntity.class,
            "workspace-remove-listener",
            entity -> tabs.remove(entity.getEntityID()));

    // listen for clear workspace
    context
        .setting()
        .listenValue(
            WorkspaceClearButtonSetting.class,
            "wm-clear-workspace",
            () ->
                context
                    .db()
                    .findAll(WorkspaceEntity.class)
                    .forEach(entity -> context.db().save(entity.setContent(""))));
  }

  private void reloadWorkspaces() {
    List<WorkspaceEntity> workspaceTabs = context.db().findAll(WorkspaceEntity.class);
    if (workspaceTabs.isEmpty()) {
      WorkspaceEntity mainWorkspace = context.db().get(WorkspaceEntity.class, PRIMARY_DEVICE);
      if (mainWorkspace == null) {
        WorkspaceEntity main = new WorkspaceEntity(PRIMARY_DEVICE, "main");
        main.setLocked(true);
        context.db().save(main);
      }
    } else {
      for (WorkspaceEntity workspaceTab : workspaceTabs) {
        reloadWorkspace(workspaceTab);
      }
    }
  }

  private void loadExtensions() {
    for (Scratch3ExtensionBlocks scratch3ExtensionBlock :
        context.getBeansOfType(Scratch3ExtensionBlocks.class)) {
      initScratch3ExtensionBlocks(scratch3ExtensionBlock);
    }
  }

  private void initScratch3ExtensionBlocks(Scratch3ExtensionBlocks scratch3ExtensionBlock) {
    scratch3ExtensionBlock.init();

    if (!ID_PATTERN.matcher(scratch3ExtensionBlock.getId()).matches()) {
      throw new IllegalArgumentException(
          "Wrong Scratch3Extension: <"
              + scratch3ExtensionBlock.getId()
              + ">. Must contains [a-z] or '-'");
    }

    if (!systemScratches.contains(scratch3ExtensionBlock.getClass())) {
      AddonEntrypoint addonEntrypoint =
          addonService.findAddonEntrypoint(scratch3ExtensionBlock.getId());
      if (addonEntrypoint == null && scratch3ExtensionBlock.getId().contains("-")) {
        String tryId =
            scratch3ExtensionBlock
                .getId()
                .substring(0, scratch3ExtensionBlock.getId().indexOf("-"));
        addonEntrypoint = addonService.findAddonEntrypoint(tryId);
      }
      int order = Integer.MAX_VALUE;
      if (addonEntrypoint == null) {
        if (!inlineScratches.contains(scratch3ExtensionBlock.getClass())) {
          throw new ServerException(
              "Unable to find addon context with id: " + scratch3ExtensionBlock.getId());
        }
      }
      Scratch3ExtensionImpl scratch3ExtensionImpl =
          new Scratch3ExtensionImpl(scratch3ExtensionBlock, order);

      if (!extensions.contains(scratch3ExtensionImpl)) {
        insertScratch3Spaces(scratch3ExtensionBlock);
      }
      extensions.add(scratch3ExtensionImpl);
    }
  }

  private void insertScratch3Spaces(Scratch3ExtensionBlocks scratch3ExtensionBlock) {
    ListIterator scratch3BlockListIterator = scratch3ExtensionBlock.getBlocks().listIterator();
    while (scratch3BlockListIterator.hasNext()) {
      Scratch3Block scratch3Block = (Scratch3Block) scratch3BlockListIterator.next();
      if (scratch3Block.getSpaceCount() > 0) {
        scratch3BlockListIterator.add(new Scratch3Space(scratch3Block.getSpaceCount()));
      }
    }
  }

  @Getter
  @RequiredArgsConstructor
  public static class WorkspaceTabHolder {

    private final String tabId;
    private final @Accessors(fluent = true) Context context;
    private final Map<String, Scratch3ExtensionBlocks> scratch3Blocks;
    private final LockManagerImpl lockManager;
    private final Map<String, WorkspaceBlockImpl> blocks = new HashMap<>();

    public WorkspaceTabHolder(
        String tabId, Context context, Map<String, Scratch3ExtensionBlocks> scratch3Blocks) {
      this.tabId = tabId;
      this.scratch3Blocks = scratch3Blocks;
      this.context = context;
      this.lockManager = new LockManagerImpl(tabId);
    }
  }
}
