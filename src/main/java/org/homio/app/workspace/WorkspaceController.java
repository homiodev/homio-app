package org.homio.app.workspace;

import static org.homio.api.util.Constants.ADMIN_ROLE_AUTHORIZE;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.api.AddonEntrypoint;
import org.homio.api.EntityContextVar.VariableType;
import org.homio.api.exception.NotFoundException;
import org.homio.api.model.OptionModel;
import org.homio.api.util.CommonUtils;
import org.homio.app.manager.AddonService;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.model.entity.WorkspaceEntity;
import org.homio.app.model.var.WorkspaceGroup;
import org.homio.app.model.var.WorkspaceVariable;
import org.homio.app.repository.device.WorkspaceRepository;
import org.homio.app.utils.UIFieldSelectionUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@RestController
@RequiredArgsConstructor
@RequestMapping("/rest/workspace")
public class WorkspaceController {

    private final EntityContextImpl entityContext;
    private final AddonService addonService;
    private final WorkspaceService workspaceService;
    private final WorkspaceRepository workspaceRepository;

    @GetMapping("/extension")
    public List<Scratch3ExtensionImpl> getExtensions() {
        List<Scratch3ExtensionImpl> extensions = new ArrayList<>(workspaceService.getExtensions());
        extensions.sort(null);
        return extensions;
    }

    @GetMapping("/extension/{addonID}.png")
    public ResponseEntity<InputStreamResource> getExtensionImage(@PathVariable("addonID") String addonID) {
        AddonEntrypoint addonEntrypoint = addonService.getAddon(addonID);
        InputStream stream = addonEntrypoint.getClass().getClassLoader().getResourceAsStream("extensions/" + addonEntrypoint.getAddonID() + ".png");
        if (stream == null) {
            stream = addonEntrypoint.getClass().getClassLoader().getResourceAsStream("images/image.png");
        }
        if (stream == null) {
            throw new NotFoundException("Unable to find workspace extension addon image for addon: " + addonID);
        }
        return CommonUtils.inputStreamToResource(stream, MediaType.IMAGE_PNG);
    }

    @GetMapping("/{entityID}")
    public String getWorkspace(@PathVariable("entityID") String entityID) {
        WorkspaceEntity workspaceEntity = entityContext.getEntity(entityID);
        if (workspaceEntity == null) {
            throw new NotFoundException("Unable to find workspace tab with id: " + entityID);
        }
        return workspaceEntity.getContent();
    }

    @SneakyThrows
    @GetMapping("/variable/values")
    public List<OptionModel> getWorkspaceVariableValues() {
        List<OptionModel> options = new ArrayList<>();
        List<WorkspaceVariable> entities = entityContext.findAll(WorkspaceVariable.class)
                                                        .stream()
                                                        .filter(s -> !s.getWorkspaceGroup().getGroupId().equals("broadcasts"))
                                                        .collect(Collectors.toList());
        UIFieldSelectionUtil.assembleItemsToOptions(options, WorkspaceVariable.class,
            entities, entityContext, null);
        return UIFieldSelectionUtil.groupingOptions(UIFieldSelectionUtil.filterOptions(options));
    }

    @GetMapping("/variable")
    public String getWorkspaceVariables() {
        JSONObject result = new JSONObject();

        Map<String, WorkspaceGroup> groups = entityContext.findAll(WorkspaceGroup.class).stream().collect(Collectors.toMap(WorkspaceGroup::getGroupId, g -> g));
        WorkspaceGroup broadcasts = groups.remove("broadcasts");
        JSONObject broadcastsVariables = new JSONObject();
        result.put("broadcasts", broadcastsVariables);
        if (broadcasts != null) {
            for (WorkspaceVariable broadcastVariable : broadcasts.getWorkspaceVariables()) {
                broadcastsVariables.put(broadcastVariable.getEntityID(), broadcastVariable.getName());
            }
        }

        JSONObject variables = new JSONObject();
        result.put("variables", variables);
        variables.put("example", new JSONArray().put("example").put(0));
        /*for (WorkspaceGroup workspaceGroup : groups.values()) {
            groupVariables.put(workspaceGroup.getGroupId(), new JSONArray().put(workspaceGroup.getName()).put(new JSONArray()).put(new JSONArray()));

            for (WorkspaceVariable variable : workspaceGroup.getWorkspaceVariables()) {
                JSONArray variables = groupVariables.getJSONArray(variable.getWorkspaceGroup().getGroupId()).getJSONArray(2);

                String title = format("%s   [%s]", variable.getTitle(),
                    variable.getWorkspaceGroup().getTitle());

                variables.put(new JSONObject()
                    .put("name", title)
                    .put("type", "group_variables_group")
                    .put("id_", variable.getEntityID())
                    .put("restriction", variable.getRestriction()));
            }
        }*/

        return result.toString();
    }

    @GetMapping("/variable/{type}")
    public List<OptionModel> getWorkspaceVariables(@PathVariable("type") String type) {
        return OptionModel.entityList(entityContext.findAllByPrefix(type));
    }

    @SneakyThrows
    @PostMapping("/{entityID}")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public void saveWorkspace(@PathVariable("entityID") String entityID, @RequestBody String json) {
        WorkspaceEntity workspaceEntity = entityContext.getEntity(entityID);
        if (workspaceEntity == null) {
            throw new NotFoundException("Unable to find workspace: " + entityID);
        }
        entityContext.save(workspaceEntity.setContent(json));
    }

    @PostMapping("/variable")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public void saveVariables(@RequestBody String json) {
        /*JSONObject request = new JSONObject(json);
        Map<String, WorkspaceGroup> groups = entityContext.findAll(WorkspaceGroup.class).stream().collect(Collectors.toMap(WorkspaceGroup::getGroupId, g -> g));

        JSONObject broadcasts = request.getJSONObject("broadcasts");
        WorkspaceGroup broadcastGroup = groups.remove("broadcasts");
        if (broadcastGroup == null) {
            EntityContextVarImpl.createBroadcastGroup(entityContext);
            broadcastGroup = entityContext.getEntity(WorkspaceGroup.PREFIX + "broadcasts");
        }
        if (broadcastGroup == null) {
            broadcastGroup = entityContext.save(new WorkspaceGroup().setGroupId("broadcasts"));
        }
        Set<String> existedBroadcasts = Optional.ofNullable(broadcastGroup.getWorkspaceVariables())
                                                .orElse(Collections.emptySet())
                                                .stream()
                                                .map(WorkspaceVariable::getEntityID)
                                                .collect(Collectors.toSet());
        for (String broadcastId : broadcasts.keySet()) {
            existedBroadcasts.remove(createOrRenameVariable(broadcastGroup, broadcastId, broadcasts.getString(broadcastId)).getEntityID());
        }
        // remove not existed broadcasts
        for (String broadcastId : existedBroadcasts) {
            log.warn("Remove broadcast: {}", broadcastId);
            entityContext.delete(broadcastId);
        }

        JSONObject groupVariables = request.getJSONObject("group_variables");
        Set<String> existedGroups = groups.keySet().stream().map(g -> WorkspaceGroup.PREFIX + g).collect(Collectors.toSet());
        // save not existed groups
        for (String groupId : groupVariables.keySet()) {
            WorkspaceGroup workspaceGroup = createOrRenameGroup(groupId, groupVariables.getJSONArray(groupId).getString(0));
            existedGroups.remove(workspaceGroup.getEntityID());
            JSONArray variables = groupVariables.getJSONArray(groupId).optJSONArray(2);
            List<String> existedVariables = workspaceGroup.getWorkspaceVariables().stream().map(BaseEntity::getEntityID).collect(Collectors.toList());
            if (variables != null) {
                for (int i = 0; i < variables.length(); i++) {
                    JSONObject variable = variables.getJSONObject(i);
                    existedVariables.remove(createOrRenameVariable(workspaceGroup, variable.getString("id_"), variable.getString("name")).getEntityID());
                }
            }

            // remove not existed variables
            for (String variable : existedVariables) {
                log.warn("Remove variable: {}", variable);
                entityContext.delete(variable);
            }
        }

        // remove group of variables!
        for (String existedGroup : existedGroups) {
            log.warn("Remove variable: {}", existedGroup);
            entityContext.delete(existedGroup);
        }*/
    }

    @GetMapping("/tab")
    public List<OptionModel> getWorkspaceTabs() {
        List<WorkspaceEntity> tabs = entityContext.findAll(WorkspaceEntity.class);
        Collections.sort(tabs);
        return OptionModel.entityList(tabs);
    }

    @SneakyThrows
    @PostMapping("/tab/{name}")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public OptionModel createWorkspaceTab(@PathVariable("name") String name) {
        WorkspaceEntity workspaceEntity = entityContext.getEntity(WorkspaceEntity.PREFIX + name);
        if (workspaceEntity == null) {
            WorkspaceEntity entity = entityContext.save(new WorkspaceEntity().setName(name).setEntityID(name));
            return OptionModel.of(entity.getEntityID(), entity.getTitle());
        }
        throw new IllegalArgumentException("Workspace tab with name <" + name + "> already exists");
    }

    @SneakyThrows
    @GetMapping("/tab/{name}")
    public boolean tabExists(@PathVariable("name") String name) {
        return entityContext.getEntity(WorkspaceEntity.PREFIX + name) != null;
    }

    @SneakyThrows
    @PutMapping("/tab/{entityID}")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public void renameWorkspaceTab(@PathVariable("entityID") String entityID, @RequestBody OptionModel option) {
        WorkspaceEntity entity = entityContext.getEntity(entityID);
        if (entity == null) {
            throw new NotFoundException("Unable to find workspace tab with id: " + entityID);
        }

        if (!WorkspaceRepository.GENERAL_WORKSPACE_TAB_NAME.equals(entity.getName())
            && !WorkspaceRepository.GENERAL_WORKSPACE_TAB_NAME.equals(option.getKey())) {

            WorkspaceEntity newEntity = workspaceRepository.getByName(option.getKey());

            if (newEntity == null) {
                entityContext.save(entity.setName(option.getKey()));
            } else {
                throw new IllegalArgumentException("Workspace tab with name <" + option.getKey() + "> already exists");
            }
        }
    }

    @DeleteMapping("/tab/{entityID}")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public void deleteWorkspaceTab(@PathVariable("entityID") String entityID) {
        WorkspaceEntity entity = entityContext.getEntity(entityID);
        if (entity == null) {
            throw new NotFoundException("Unable to find workspace tab with id: " + entityID);
        }
        if (WorkspaceRepository.GENERAL_WORKSPACE_TAB_NAME.equals(entity.getName())) {
            throw new IllegalArgumentException("W.ERROR.REMOVE_MAIN_TAB");
        }
        if (!workspaceService.isEmpty(entity.getContent())) {
            throw new IllegalArgumentException("W.ERROR.REMOVE_NON_EMPTY_TAB");
        }
        entityContext.delete(entityID);
    }

    private WorkspaceVariable createOrRenameVariable(WorkspaceGroup workspaceGroup, String variableId, String variableName) {
        WorkspaceVariable workspaceVariable = entityContext.getEntity(WorkspaceVariable.PREFIX + variableId);
        if (workspaceVariable == null) {
            return entityContext.save(new WorkspaceVariable(variableId, variableName, workspaceGroup, VariableType.Any));
        } else if (!Objects.equals(variableName, workspaceVariable.getName())) {
            return entityContext.save(workspaceVariable.setName(variableName));
        }
        return workspaceVariable;
    }

    private WorkspaceGroup createOrRenameGroup(String groupId, String groupName) {
        WorkspaceGroup workspaceGroup = entityContext.getEntity(WorkspaceGroup.PREFIX + groupId);
        if (workspaceGroup == null) {
            return entityContext.save(new WorkspaceGroup().setGroupId(groupId).setName(groupName));
        } else if (!Objects.equals(groupName, workspaceGroup.getName())) {
            return entityContext.save(workspaceGroup.setName(groupName));
        }
        return workspaceGroup;
    }

    @Setter
    public static class CreateVariable {

        String key;
        String varGroup;
        String varName;
    }
}
