package org.touchhome.app.workspace;

import com.pivovarit.function.ThrowingRunnable;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.touchhome.app.repository.device.WorkspaceRepository;
import org.touchhome.app.setting.system.SystemClearWorkspaceButtonSetting;
import org.touchhome.bundle.api.BeanPostConstruct;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.EntityContextBGP;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.workspace.WorkspaceBlock;
import org.touchhome.bundle.api.workspace.WorkspaceEntity;
import org.touchhome.bundle.api.workspace.WorkspaceEventListener;
import org.touchhome.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.touchhome.common.util.CommonUtils;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Log4j2
@Component
@RequiredArgsConstructor
public class WorkspaceManager implements BeanPostConstruct {

    private final Set<String> ONCE_EXECUTION_BLOCKS = new HashSet<>(Arrays.asList("boolean_link", "group_variable_link"));
    private final BroadcastLockManagerImpl broadcastLockManager;
    private final EntityContext entityContext;

    private Collection<WorkspaceEventListener> workspaceEventListeners;
    private Map<String, Scratch3ExtensionBlocks> scratch3Blocks;
    private final Map<String, TabHolder> tabs = new HashMap<>();

    @Override
    public void onContextUpdate(EntityContext entityContext) {
        scratch3Blocks = entityContext.getBeansOfType(Scratch3ExtensionBlocks.class).stream()
                .collect(Collectors.toMap(Scratch3ExtensionBlocks::getId, s -> s));
        workspaceEventListeners = entityContext.getBeansOfType(WorkspaceEventListener.class);
    }

    private void reloadWorkspace(WorkspaceEntity workspaceEntity) {
        log.debug("Reloading workspace <{}>...", workspaceEntity.getName());
        boolean workspaceStartedBefore = tabs.putIfAbsent(workspaceEntity.getEntityID(), new TabHolder()) != null;

        TabHolder tabHolder = releaseWorkspaceEntity(workspaceEntity);

        tabHolder.tab2Services.clear();
        tabHolder.tab2WorkspaceBlocks.clear();

        if (StringUtils.isNotEmpty(workspaceEntity.getContent())) {
            try {
                // wait to finish all nested processes if workspace started before
                if (workspaceStartedBefore) {
                    log.info("Wait workspace <{}> to able to finish old one", workspaceEntity.getTitle());
                    Thread.sleep(3000);
                }

                tabHolder.tab2WorkspaceBlocks = parseWorkspace(workspaceEntity);
                tabHolder.tab2WorkspaceBlocks.values().stream()
                        .filter(workspaceBlock -> workspaceBlock.isTopLevel() && !workspaceBlock.isShadow())
                        .forEach(workspaceBlock -> {
                            if (ONCE_EXECUTION_BLOCKS.contains(workspaceBlock.getOpcode())) {
                                executeOnce(workspaceBlock);
                            } else {
                                EntityContextBGP.ThreadContext<?> threadContext = this.entityContext.bgp().run(
                                        "workspace-" + workspaceBlock.getId(),
                                        createWorkspaceThread(workspaceBlock, workspaceEntity), true);
                                threadContext.setDescription(
                                        "Tab[" + workspaceEntity.getName() + "]. " + workspaceBlock.getDescription());
                                workspaceBlock.setStateHandler(threadContext::setState);
                                tabHolder.tab2Services.add(threadContext);
                            }
                        });
            } catch (Exception ex) {
                log.error("Unable to initialize workspace: " + ex.getMessage(), ex);
                entityContext.ui().sendErrorMessage("Unable to initialize workspace: " + ex.getMessage(), ex);
            }
        }
    }

    private ThrowingRunnable<Exception> createWorkspaceThread(WorkspaceBlock workspaceBlock, WorkspaceEntity workspaceEntity) {
        return () -> {
            String oldName = Thread.currentThread().getName();
            String name = workspaceBlock.getId();
            log.debug("Workspace start thread: <{}>", name);
            try {
                Thread.currentThread().setName(workspaceEntity.getEntityID());
                ((WorkspaceBlockImpl) workspaceBlock).handleOrEvaluate();
            } catch (Exception ex) {
                log.warn("Error in workspace thread: <{}>, <{}>", name, CommonUtils.getErrorMessage(ex), ex);
                entityContext.ui().sendErrorMessage("Error in workspace", ex);
            } finally {
                Thread.currentThread().setName(oldName);
            }
            log.debug("Workspace thread finished: <{}>", name);
        };
    }

    private TabHolder releaseWorkspaceEntity(WorkspaceEntity workspaceEntity) {
        TabHolder tabHolder = tabs.get(workspaceEntity.getEntityID());
        broadcastLockManager.release(workspaceEntity.getEntityID());

        for (WorkspaceEventListener workspaceEventListener : workspaceEventListeners) {
            workspaceEventListener.release(workspaceEntity.getEntityID());
        }

        for (WorkspaceBlock workspaceBlock : tabHolder.tab2WorkspaceBlocks.values()) {
            ((WorkspaceBlockImpl) workspaceBlock).release();
        }
        for (EntityContextBGP.ThreadContext threadContext : tabHolder.tab2Services) {
            this.entityContext.bgp().cancelThread(threadContext.getName());
        }
        return tabHolder;
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

    /*private void reloadVariable(WorkspaceShareVariableEntity entity) {
        log.debug("Reloading workspace variables...");
        JSONObject target = new JSONObject(StringUtils.defaultIfEmpty(entity.getContent(), "{}"));

        // broadcasts
        updateWorkspaceObjects(target.optJSONObject("broadcasts"), WorkspaceBroadcastEntity.class, (id, name, array) ->
                saveOrUpdateEntity(WorkspaceBroadcastEntity::new, id, name, WorkspaceBroadcastEntity.PREFIX));

        // group variables
        JSONObject list = target.optJSONObject("group_variables");
        if (list != null) {
            for (String id : list.keySet()) {
                JSONArray array = list.optJSONArray(id);
                String name = array == null ? list.getString(id) : array.getString(0);
                if (!name.isEmpty()) {
                    createGroupVariables(name, array);
                }
            }
        }
    }*/

    private Map<String, WorkspaceBlock> parseWorkspace(WorkspaceEntity workspaceEntity) {
        JSONObject jsonObject = new JSONObject(workspaceEntity.getContent());
        JSONObject target = jsonObject.getJSONObject("target");

        JSONObject blocks = target.getJSONObject("blocks");
        Map<String, WorkspaceBlock> workspaceMap = new HashMap<>();

        for (String blockId : blocks.keySet()) {
            JSONObject block = blocks.optJSONObject(blockId);
            if (block == null) {
                continue;
            }

            if (!workspaceMap.containsKey(blockId)) {
                workspaceMap.put(blockId, new WorkspaceBlockImpl(blockId, workspaceMap, scratch3Blocks, entityContext));
            }

            WorkspaceBlockImpl workspaceBlock = (WorkspaceBlockImpl) workspaceMap.get(blockId);
            workspaceBlock.setShadow(block.optBoolean("shadow"));
            workspaceBlock.setTopLevel(block.getBoolean("topLevel"));
            workspaceBlock.setOpcode(block.getString("opcode"));
            workspaceBlock.setParent(getOrCreateWorkspaceBlock(workspaceMap, block, "parent"));
            workspaceBlock.setNext(getOrCreateWorkspaceBlock(workspaceMap, block, "next"));

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

        return workspaceMap;
    }

    /*private void createGroupVariables(String group, JSONArray jsonArray) {
        List<String> existedEntities = entityContext.findAll(WorkspaceGroupVariable.class).stream().map(BaseEntity::getEntityID)
                .collect(Collectors.toList());
        JSONArray variablesHolder = jsonArray.optJSONArray(2);
        if (variablesHolder != null) {
            for (int i = 0; i < variablesHolder.length(); i++) {
                JSONObject jsonObject1 = variablesHolder.getJSONObject(i);

                String variableId = jsonObject1.getString("id_");
                String variableName = jsonObject1.getString("name");

                WorkspaceGroupVariable entity = entityContext.getEntity(WorkspaceGroupVariable.PREFIX + variableId);
                if (entity == null) {
                    entity = entityContext.save(
                            new WorkspaceGroupVariable().setVariableGroup(group).computeEntityID(() -> variableId)
                                    .setName(variableName));
                } else if (!Objects.equals(entity.getName(), variableName) || !Objects.equals(entity.getVariableGroup(), group)) {
                    entity = entityContext.save(entity.setName(variableName).setVariableGroup(group));
                }
                existedEntities.remove(entity.getEntityID());
            }
        }
        for (String existedEntity : existedEntities) {
            entityContext.delete(existedEntity);
        }
    }*/

    /*private Map<BaseEntity, JSONArray> updateWorkspaceObjects(JSONObject list, Class<? extends BaseEntity> entityType,
                                                              CreateVariableHandler createVariableHandler) {
        Set<String> entities = new HashSet<>();
        Map<BaseEntity, JSONArray> res = new HashMap<>();
        if (list != null) {
            for (String id : list.keySet()) {
                JSONArray array = list.optJSONArray(id);
                String name = array == null ? list.getString(id) : array.getString(0);
                if (!name.isEmpty()) {
                    BaseEntity baseEntity = createVariableHandler.createVariables(id, name, array);
                    if (baseEntity != null) {
                        res.put(baseEntity, array);
                        entities.add(baseEntity.getEntityID());
                    }
                }
            }
        }
        // remove deleted items
        for (BaseEntity entity : entityContext.findAll(entityType)) {
            if (!entities.contains(entity.getEntityID())) {
                entityContext.delete(entity);
            }
        }
        return res;
    }*/

    /*private interface CreateVariableHandler {
        BaseEntity createVariables(String id, String name, JSONArray array);
    }*/

    /*private BaseEntity saveOrUpdateEntity(Supplier<BaseEntity> entitySupplier, String id, String name, String repositoryPrefix) {
        BaseEntity entity = entityContext.getEntity(repositoryPrefix + id);
        if (entity == null) {
            entity = entityContext.save(entitySupplier.get().computeEntityID(() -> id).setName(name));
        } else if (entity.getName() == null || !entity.getName().equals(name)) { // update name if changed
            if (name != null) {
                entity = entityContext.save(entity.setName(name));
            }
        }
        return entity;
    }*/

    private WorkspaceBlock getOrCreateWorkspaceBlock(Map<String, WorkspaceBlock> workspaceMap, JSONObject block, String key) {
        if (block.has(key) && !block.isNull(key)) {
            workspaceMap.putIfAbsent(block.getString(key),
                    new WorkspaceBlockImpl(block.getString(key), workspaceMap, scratch3Blocks, entityContext));
            return workspaceMap.get(block.getString(key));
        }
        return null;
    }

    public void loadWorkspace() {
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

    /*private void reloadVariables() {
        WorkspaceShareVariableEntity entity =
                entityContext.getEntity(SHARE_VARIABLES);
        if (entity == null) {
            entity = entityContext.save(
                    new WorkspaceShareVariableEntity().computeEntityID(() -> WorkspaceShareVariableEntity.NAME));
        }
        reloadVariable(entity);
    }*/

    private void reloadWorkspaces() {
        List<WorkspaceEntity> list = entityContext.findAll(WorkspaceEntity.class);
        if (list.isEmpty()) {
            WorkspaceEntity mainWorkspace =
                    entityContext.getEntity(WorkspaceEntity.PREFIX + WorkspaceRepository.GENERAL_WORKSPACE_TAB_NAME);
            if (mainWorkspace == null) {
                entityContext.save(new WorkspaceEntity().computeEntityID(() -> WorkspaceRepository.GENERAL_WORKSPACE_TAB_NAME));
            }
        } else {
            for (WorkspaceEntity workspaceEntity : list) {
                reloadWorkspace(workspaceEntity);
            }
        }
    }

    public WorkspaceBlock getWorkspaceBlockById(String id) {
        for (TabHolder tabHolder : this.tabs.values()) {
            if (tabHolder.tab2WorkspaceBlocks.containsKey(id)) {
                return tabHolder.tab2WorkspaceBlocks.get(id);
            }
        }
        return null;
    }

    private static class TabHolder {
        private List<EntityContextBGP.ThreadContext> tab2Services = new ArrayList<>();
        private Map<String, WorkspaceBlock> tab2WorkspaceBlocks = new HashMap<>();
    }
}
