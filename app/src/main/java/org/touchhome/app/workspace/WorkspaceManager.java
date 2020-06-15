package org.touchhome.app.workspace;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.touchhome.app.manager.BackgroundProcessManager;
import org.touchhome.app.model.workspace.WorkspaceBroadcastEntity;
import org.touchhome.app.repository.device.WorkspaceRepository;
import org.touchhome.app.repository.workspace.WorkspaceBroadcastRepository;
import org.touchhome.app.setting.system.SystemClearWorkspaceButtonSetting;
import org.touchhome.app.setting.system.SystemClearWorkspaceVariablesButtonSetting;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.model.workspace.WorkspaceShareVariableEntity;
import org.touchhome.bundle.api.model.workspace.WorkspaceStandaloneVariableEntity;
import org.touchhome.bundle.api.model.workspace.backup.WorkspaceBackupEntity;
import org.touchhome.bundle.api.model.workspace.backup.WorkspaceBackupGroupEntity;
import org.touchhome.bundle.api.model.workspace.bool.WorkspaceBooleanEntity;
import org.touchhome.bundle.api.model.workspace.bool.WorkspaceBooleanGroupEntity;
import org.touchhome.bundle.api.model.workspace.var.WorkspaceVariableEntity;
import org.touchhome.bundle.api.model.workspace.var.WorkspaceVariableGroupEntity;
import org.touchhome.bundle.api.scratch.Scratch3ExtensionBlocks;
import org.touchhome.bundle.api.scratch.WorkspaceBlock;
import org.touchhome.bundle.api.scratch.WorkspaceEventListener;
import org.touchhome.bundle.api.thread.BackgroundProcessStatus;
import org.touchhome.bundle.api.workspace.BroadcastLockManager;
import org.touchhome.bundle.api.workspace.WorkspaceEntity;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Log4j2
@Component
@RequiredArgsConstructor
public class WorkspaceManager {

    private final Set<String> ONCE_EXECUTION_BLOCKS = new HashSet<>(Arrays.asList("boolean_link", "group_variable_link"));
    private final BroadcastLockManager broadcastLockManager;
    private final EntityContext entityContext;
    private final BackgroundProcessManager backgroundProcessManager;
    private final List<Scratch3ExtensionBlocks> scratch3BlocksList;
    private final List<WorkspaceEventListener> workspaceEventListeners;

    private Map<String, Scratch3ExtensionBlocks> scratch3Blocks;

    private Map<String, TabHolder> tabs = new HashMap<>();

    @PostConstruct
    public void init() {
        scratch3Blocks = scratch3BlocksList.stream().collect(Collectors.toMap(Scratch3ExtensionBlocks::getId, s -> s));
    }

    private void reloadWorkspace(WorkspaceEntity workspaceEntity) {
        log.debug("Reloading workspace <{}>...", workspaceEntity.getName());
        tabs.putIfAbsent(workspaceEntity.getEntityID(), new TabHolder());
        TabHolder tabHolder = tabs.get(workspaceEntity.getEntityID());

        broadcastLockManager.release(workspaceEntity.getEntityID());

        for (WorkspaceEventListener workspaceEventListener : workspaceEventListeners) {
            workspaceEventListener.release(workspaceEntity.getEntityID());
        }

        for (WorkspaceBlock workspaceBlock : tabHolder.tab2WorkspaceBlocks.values()) {
            workspaceBlock.release();
        }
        for (WorkspaceBlockProcessService tab2Service : tabHolder.tab2Services) {
            backgroundProcessManager.cancelTask(tab2Service, BackgroundProcessStatus.STOP, null);
        }
        tabHolder.tab2Services.clear();
        tabHolder.tab2WorkspaceBlocks.clear();

        if (StringUtils.isNotEmpty(workspaceEntity.getContent())) {
            try {
                tabHolder.tab2WorkspaceBlocks = parseWorkspace(workspaceEntity);
                tabHolder.tab2WorkspaceBlocks.values().stream()
                        .filter(workspaceBlock -> workspaceBlock.isTopLevel() && !workspaceBlock.isShadow())
                        .forEach(workspaceBlock -> {
                            if (ONCE_EXECUTION_BLOCKS.contains(workspaceBlock.getOpcode())) {
                                executeOnce(workspaceBlock);
                            } else {
                                WorkspaceBlockProcessService service = new WorkspaceBlockProcessService(workspaceBlock, workspaceEntity);
                                tabHolder.tab2Services.add(service);
                                backgroundProcessManager.fireIfNeedRestart(service);
                            }
                        });
            } catch (Exception ex) {
                log.error("Unable to initialize workspace: " + ex.getMessage(), ex);
                entityContext.sendErrorMessage("Unable to initialize workspace: " + ex.getMessage(), ex);
            }
        }
    }

    private void executeOnce(WorkspaceBlock workspaceBlock) {
        try {
            log.debug("Execute single block: <{}>", workspaceBlock.getOpcode());
            workspaceBlock.handle();
        } catch (Exception ex) {
            log.error("Error while execute single block: <{}>", workspaceBlock.getOpcode(), ex);
        }
    }

    public boolean isEmpty(String content) {
        if (StringUtils.isEmpty(content)) {
            return true;
        }
        JSONObject target = new JSONObject(content).getJSONObject("target");
        for (String key : new String[]{"variables", "lists", "backup_lists", "bool_variables", "group_variables", "blocks"}) {
            if (!target.getJSONObject(key).keySet().isEmpty()) {
                return true;
            }
        }
        return true;
    }

    private void reloadVariable(WorkspaceShareVariableEntity entity) {
        log.debug("Reloading workspace variables...");
        JSONObject target = new JSONObject(StringUtils.defaultIfEmpty(entity.getContent(), "{}"));

        // single variables
        updateWorkspaceObjects(target.optJSONObject("variables"), WorkspaceStandaloneVariableEntity.PREFIX, WorkspaceStandaloneVariableEntity::new);

        // broadcasts
        updateWorkspaceObjects(target.optJSONObject("broadcasts"), WorkspaceBroadcastRepository.PREFIX, WorkspaceBroadcastEntity::new);

        // backup
        Map<BaseEntity, JSONArray> values = updateWorkspaceObjects(target.optJSONObject("backup_lists"), WorkspaceBackupGroupEntity.PREFIX, WorkspaceBackupGroupEntity::new);
        createSupplier(values, (baseEntity) -> new WorkspaceBackupEntity().setWorkspaceBackupGroupEntity((WorkspaceBackupGroupEntity) baseEntity), WorkspaceBackupEntity.PREFIX);


        // bool
        values = updateWorkspaceObjects(target.optJSONObject("bool_variables"), WorkspaceBooleanGroupEntity.PREFIX, WorkspaceBooleanGroupEntity::new);
        createSupplier(values, (baseEntity) -> new WorkspaceBooleanEntity().setWorkspaceBooleanGroupEntity((WorkspaceBooleanGroupEntity) baseEntity), WorkspaceBooleanEntity.PREFIX);

        // group variables
        values = updateWorkspaceObjects(target.optJSONObject("group_variables"), WorkspaceVariableGroupEntity.PREFIX, WorkspaceVariableGroupEntity::new);
        createSupplier(values, (baseEntity) -> new WorkspaceVariableEntity().setWorkspaceVariableGroupEntity((WorkspaceVariableGroupEntity) baseEntity), WorkspaceVariableEntity.PREFIX);
    }

    private Map<String, WorkspaceBlock> parseWorkspace(WorkspaceEntity workspaceEntity) {
        JSONObject jsonObject = new JSONObject(workspaceEntity.getContent());
        JSONObject target = jsonObject.getJSONObject("target");

        JSONObject blocks = target.getJSONObject("blocks");
        Map<String, WorkspaceBlock> workspaceMap = new HashMap<>();

        for (String blockId : blocks.keySet()) {
            JSONObject block = blocks.getJSONObject(blockId);

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

    private void createSupplier(Map<BaseEntity, JSONArray> res, Function<BaseEntity, BaseEntity> entitySupplier, String prefix) {
        List<String> existedEntities = entityContext.findAllByPrefix(prefix).stream().map(BaseEntity::getEntityID).collect(Collectors.toList());
        for (Map.Entry<BaseEntity, JSONArray> entry : res.entrySet()) {
            JSONArray jsonArray = entry.getValue().optJSONArray(2);
            if (jsonArray != null) {
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObject1 = jsonArray.getJSONObject(i);
                    saveOrUpdateEntity(() -> entitySupplier.apply(entry.getKey()), jsonObject1.getString("id"), jsonObject1.getString("name"), prefix);
                    existedEntities.remove(prefix + jsonObject1.getString("id"));
                }
            }
        }
        for (String existedEntity : existedEntities) {
            entityContext.delete(existedEntity);
        }
    }

    private Map<BaseEntity, JSONArray> updateWorkspaceObjects(JSONObject list, String repositoryPrefix, Supplier<BaseEntity> entitySupplier) {
        Set<String> entities = new HashSet<>();
        Map<BaseEntity, JSONArray> res = new HashMap<>();
        if (list != null) {
            for (String id : list.keySet()) {
                JSONArray array = list.optJSONArray(id);
                String name = array == null ? list.getString(id) : array.getString(0);
                if (!name.isEmpty()) {
                    BaseEntity entity = saveOrUpdateEntity(entitySupplier, id, name, repositoryPrefix);
                    res.put(entity, array);
                    entities.add(repositoryPrefix + id);
                }
            }
        }
        // remove deleted items
        for (BaseEntity entity : entityContext.findAllByPrefix(repositoryPrefix)) {
            if (!entities.contains(entity.getEntityID())) {
                entityContext.delete(entity);
            }
        }
        return res;
    }

    private BaseEntity saveOrUpdateEntity(Supplier<BaseEntity> entitySupplier, String id, String name, String repositoryPrefix) {
        BaseEntity entity = entityContext.getEntity(repositoryPrefix + id);
        if (entity == null) {
            entity = entityContext.save(entitySupplier.get().computeEntityID(() -> id).setName(name));
        } else if (entity.getName() == null || !entity.getName().equals(name)) { // update name if changed
            if (name != null) {
                entity = entityContext.save(entity.setName(name));
            }
        }
        return entity;
    }

    private WorkspaceBlock getOrCreateWorkspaceBlock(Map<String, WorkspaceBlock> workspaceMap, JSONObject block, String key) {
        if (block.has(key) && !block.isNull(key)) {
            workspaceMap.putIfAbsent(block.getString(key), new WorkspaceBlockImpl(block.getString(key), workspaceMap, scratch3Blocks, entityContext));
            return workspaceMap.get(block.getString(key));
        }
        return null;
    }

    public void postConstruct() {
        try {
            reloadVariables();
            reloadWorkspaces();
        } catch (Exception ex) {
            log.error("Unable to load workspace. Looks like workspace has incorrect value", ex);
        }
        entityContext.addEntityUpdateListener(WorkspaceEntity.class, this::reloadWorkspace);
        entityContext.addEntityUpdateListener(WorkspaceShareVariableEntity.class, this::reloadVariable);

        // listen for clear workspace
        entityContext.listenSettingValue(SystemClearWorkspaceButtonSetting.class, () ->
                entityContext.findAll(WorkspaceEntity.class).forEach(entity -> entityContext.save(entity.setContent(""))));

        // listen for clear variables
        entityContext.listenSettingValue(SystemClearWorkspaceVariablesButtonSetting.class, () -> {
            entityContext.findAll(WorkspaceEntity.class).forEach(entity -> entityContext.save(entity.setContent("")));
            WorkspaceShareVariableEntity entity = entityContext.getEntity(WorkspaceShareVariableEntity.PREFIX + WorkspaceShareVariableEntity.NAME);
            entityContext.save(entity.setContent(""));
        });
    }

    private void reloadVariables() {
        WorkspaceShareVariableEntity entity = entityContext.getEntity(WorkspaceShareVariableEntity.PREFIX + WorkspaceShareVariableEntity.NAME);
        if (entity == null) {
            entity = entityContext.save(new WorkspaceShareVariableEntity().computeEntityID(() -> WorkspaceShareVariableEntity.NAME));
        }
        reloadVariable(entity);
    }

    private void reloadWorkspaces() {
        List<WorkspaceEntity> list = entityContext.findAll(WorkspaceEntity.class);
        if (list.isEmpty()) {
            WorkspaceEntity mainWorkspace = entityContext.getEntity(WorkspaceEntity.PREFIX + WorkspaceRepository.GENERAL_WORKSPACE_TAB_NAME);
            if (mainWorkspace == null) {
                entityContext.save(new WorkspaceEntity().computeEntityID(() -> WorkspaceRepository.GENERAL_WORKSPACE_TAB_NAME));
            }
        } else {
            for (WorkspaceEntity workspaceEntity : list) {
                reloadWorkspace(workspaceEntity);
            }
        }
    }

    private static class TabHolder {
        private List<WorkspaceBlockProcessService> tab2Services = new ArrayList<>();
        private Map<String, WorkspaceBlock> tab2WorkspaceBlocks = new HashMap<>();
    }
}
