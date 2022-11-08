package org.touchhome.app.workspace;

import com.pivovarit.function.ThrowingRunnable;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.touchhome.app.repository.device.WorkspaceRepository;
import org.touchhome.app.setting.system.SystemClearWorkspaceButtonSetting;
import org.touchhome.app.spring.ContextRefreshed;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.workspace.WorkspaceBlock;
import org.touchhome.bundle.api.workspace.WorkspaceEntity;
import org.touchhome.bundle.api.workspace.WorkspaceEventListener;
import org.touchhome.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.touchhome.common.util.CommonUtils;

@Log4j2
@Component
@RequiredArgsConstructor
public class WorkspaceManager implements ContextRefreshed {

  private final Duration TIME_WAIT_OLD_WORKSPACE = Duration.ofSeconds(3);
  private final Set<String> ONCE_EXECUTION_BLOCKS = new HashSet<>(Arrays.asList("boolean_link", "group_variable_link"));
  private final EntityContext entityContext;

  private Collection<WorkspaceEventListener> workspaceEventListeners;
  private Map<String, Scratch3ExtensionBlocks> scratch3Blocks;
  // tab <-> list of top blocks
  private final Map<String, WorkspaceTabHolder> tabs = new HashMap<>();

  @Override
  public void onContextRefresh() {
    scratch3Blocks = entityContext.getBeansOfType(Scratch3ExtensionBlocks.class).stream()
        .collect(Collectors.toMap(Scratch3ExtensionBlocks::getId, s -> s));
    workspaceEventListeners = entityContext.getBeansOfType(WorkspaceEventListener.class);

    loadWorkspace();
  }

  @SneakyThrows
  private synchronized void reloadWorkspace(WorkspaceEntity workspaceTab) {
    log.debug("Reloading workspace <{}>...", workspaceTab.getName());

    WorkspaceTabHolder workspaceTabHolder = tabs.remove(workspaceTab.getEntityID());
    if (workspaceTabHolder != null) {
      releaseWorkspaceEntity(workspaceTab, workspaceTabHolder);
      // wait to finish all nested processes if workspace started before
      log.info("Wait workspace {}, {} to able to finish old one", workspaceTab.getTitle(), TIME_WAIT_OLD_WORKSPACE);
      Thread.sleep(TIME_WAIT_OLD_WORKSPACE.toMillis());
    }

    workspaceTabHolder = new WorkspaceTabHolder(workspaceTab.getEntityID(), entityContext, scratch3Blocks);
    tabs.put(workspaceTab.getEntityID(), workspaceTabHolder);

    if (StringUtils.isNotEmpty(workspaceTab.getContent())) {
      try {
        parseWorkspace(workspaceTab, workspaceTabHolder);
        workspaceTabHolder.blocks.values().stream()
            .filter(workspaceBlock -> workspaceBlock.isTopLevel() && !workspaceBlock.isShadow())
            .forEach(workspaceBlock -> {
              if (ONCE_EXECUTION_BLOCKS.contains(workspaceBlock.getOpcode())) {
                executeOnce(workspaceBlock);
              } else {
                this.entityContext.bgp().builder("workspace-" + workspaceBlock.getId())
                    .tap(workspaceBlock::setThreadContext)
                    .execute(createWorkspaceThread(workspaceBlock));
              }
            });
      } catch (Exception ex) {
        log.error("Unable to initialize workspace: " + ex.getMessage(), ex);
        entityContext.ui().sendErrorMessage("Unable to initialize workspace: " + ex.getMessage(), ex);
      }
    }
  }

  private ThrowingRunnable<Exception> createWorkspaceThread(WorkspaceBlock workspaceBlock) {
    return () -> {
      String name = workspaceBlock.getId();
      log.info("Workspace start thread: <{}>", name);
      try {
        ((WorkspaceBlockImpl) workspaceBlock).handleOrEvaluate();
      } catch (Exception ex) {
        log.warn("Error in workspace thread: <{}>, <{}>", name, CommonUtils.getErrorMessage(ex), ex);
        entityContext.ui().sendErrorMessage("Error in workspace", ex);
      }
      log.info("Workspace thread finished: <{}>", name);
    };
  }

  private void releaseWorkspaceEntity(WorkspaceEntity workspaceTab, WorkspaceTabHolder oldWorkspaceTabHolder) {
    oldWorkspaceTabHolder.broadcastLockManager.release();

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

  public boolean isEmpty(String content) {
    if (StringUtils.isEmpty(content)) {
      return true;
    }
    JSONObject target = new JSONObject(content).getJSONObject("target");
    for (String key : new String[]{"comments", "blocks"}) {
      if (target.has(key) && !target.getJSONObject(key).keySet().isEmpty()) {
        return false;
      }
    }
    return true;
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
        workspaceTabHolder.blocks.put(blockId,
            new WorkspaceBlockImpl(blockId, workspaceTabHolder));
      }

      WorkspaceBlockImpl workspaceBlock = workspaceTabHolder.blocks.get(blockId);
      workspaceBlock.setShadow(block.optBoolean("shadow"));
      workspaceBlock.setTopLevel(block.getBoolean("topLevel"));
      workspaceBlock.setOpcode(block.getString("opcode"));
      workspaceBlock.setParent(getOrCreateWorkspaceBlock(workspaceTabHolder, block, "parent"));
      workspaceBlock.setNext(getOrCreateWorkspaceBlock(workspaceTabHolder, block, "next"));

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

  private WorkspaceBlockImpl getOrCreateWorkspaceBlock(WorkspaceTabHolder workspaceTabHolder, JSONObject block,
      String key) {
    if (block.has(key) && !block.isNull(key)) {
      workspaceTabHolder.blocks.putIfAbsent(block.getString(key),
          new WorkspaceBlockImpl(block.getString(key), workspaceTabHolder));
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
    entityContext.event().addEntityUpdateListener(WorkspaceEntity.class,
        "workspace-change-listener", this::reloadWorkspace);
    entityContext.event().addEntityRemovedListener(WorkspaceEntity.class,
        "workspace-remove-listener", entity -> tabs.remove(entity.getEntityID()));

    // listen for clear workspace
    entityContext.setting().listenValue(SystemClearWorkspaceButtonSetting.class, "wm-clear-workspace", () ->
        entityContext.findAll(WorkspaceEntity.class).forEach(entity -> entityContext.save(entity.setContent(""))));
  }

  private void reloadWorkspaces() {
    List<WorkspaceEntity> workspaceTabs = entityContext.findAll(WorkspaceEntity.class);
    if (workspaceTabs.isEmpty()) {
      WorkspaceEntity mainWorkspace =
          entityContext.getEntity(WorkspaceEntity.PREFIX + WorkspaceRepository.GENERAL_WORKSPACE_TAB_NAME);
      if (mainWorkspace == null) {
        entityContext.save(new WorkspaceEntity().computeEntityID(() -> WorkspaceRepository.GENERAL_WORKSPACE_TAB_NAME));
      }
    } else {
      for (WorkspaceEntity workspaceTab : workspaceTabs) {
        reloadWorkspace(workspaceTab);
      }
    }
  }

  public WorkspaceBlock getWorkspaceBlockById(String id) {
    for (WorkspaceTabHolder workspaceTabHolder : this.tabs.values()) {
      if (workspaceTabHolder.blocks.containsKey(id)) {
        return workspaceTabHolder.blocks.get(id);
      }
    }
    return null;
  }

  public void fireAllBroadcastLock(Consumer<BroadcastLockManagerImpl> handler) {
    for (WorkspaceTabHolder workspaceTabHolder : tabs.values()) {
      handler.accept(workspaceTabHolder.broadcastLockManager);
    }
  }

  @Getter
  @RequiredArgsConstructor
  public static class WorkspaceTabHolder {

    private final String tabId;
    private final EntityContext entityContext;
    private final Map<String, Scratch3ExtensionBlocks> scratch3Blocks;
    private final BroadcastLockManagerImpl broadcastLockManager;
    private final Map<String, WorkspaceBlockImpl> blocks = new HashMap<>();

    public WorkspaceTabHolder(String tabId, EntityContext entityContext, Map<String, Scratch3ExtensionBlocks> scratch3Blocks) {
      this.tabId = tabId;
      this.scratch3Blocks = scratch3Blocks;
      this.entityContext = entityContext;
      this.broadcastLockManager = new BroadcastLockManagerImpl(tabId);
    }
  }
}
