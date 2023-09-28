package org.homio.app.workspace;

import static org.homio.api.util.Constants.ADMIN_ROLE_AUTHORIZE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.api.exception.NotFoundException;
import org.homio.api.model.OptionModel;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.model.entity.WorkspaceEntity;
import org.homio.app.model.var.WorkspaceGroup;
import org.homio.app.model.var.WorkspaceVariable;
import org.homio.app.repository.device.WorkspaceRepository;
import org.json.JSONArray;
import org.json.JSONObject;
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
    private final WorkspaceService workspaceService;
    private final WorkspaceRepository workspaceRepository;

    @GetMapping("/extension")
    public List<Scratch3ExtensionImpl> getExtensions() {
        List<Scratch3ExtensionImpl> extensions = new ArrayList<>(workspaceService.getExtensions());
        extensions.sort(null);
        return extensions;
    }

    @GetMapping("/{entityID}")
    public String getWorkspace(@PathVariable("entityID") String entityID) {
        WorkspaceEntity workspaceEntity = entityContext.getEntity(entityID);
        if (workspaceEntity == null) {
            throw new NotFoundException("Unable to find workspace tab with id: " + entityID);
        }
        return workspaceEntity.getContent();
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

        return result.toString();
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
    public void saveVariables(@RequestBody String ignore) {
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
        WorkspaceEntity entity = entityContext.getEntity(WorkspaceEntity.PREFIX + name);
        if (entity == null) {
            entity = new WorkspaceEntity(name, name);
            entity = entityContext.save(entity);
            return OptionModel.of(entity.getEntityID(), entity.getTitle());
        }
        throw new IllegalArgumentException("Workspace tab with name <" + name + "> already exists");
    }

    @SneakyThrows
    @GetMapping("/tab/{name}")
    public boolean tabExists(@PathVariable("name") String name) {
        return entityContext.getEntity(WorkspaceEntity.PREFIX + name) != null;
    }

    @Setter
    public static class CreateVariable {

        String key;
        String varGroup;
        String varName;
    }
}
