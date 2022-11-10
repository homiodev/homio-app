package org.touchhome.app.workspace;

import static org.touchhome.bundle.api.util.Constants.ADMIN_ROLE;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.touchhome.app.manager.BundleService;
import org.touchhome.app.manager.common.impl.EntityContextVarImpl;
import org.touchhome.app.manager.var.WorkspaceGroup;
import org.touchhome.app.manager.var.WorkspaceVariable;
import org.touchhome.app.repository.device.WorkspaceRepository;
import org.touchhome.app.rest.BundleController;
import org.touchhome.app.spring.ContextRefreshed;
import org.touchhome.app.workspace.block.Scratch3Space;
import org.touchhome.app.workspace.block.core.Scratch3ControlBlocks;
import org.touchhome.app.workspace.block.core.Scratch3DataBlocks;
import org.touchhome.app.workspace.block.core.Scratch3EventsBlocks;
import org.touchhome.app.workspace.block.core.Scratch3MiscBlocks;
import org.touchhome.app.workspace.block.core.Scratch3MutatorBlocks;
import org.touchhome.app.workspace.block.core.Scratch3OperatorBlocks;
import org.touchhome.bundle.api.BundleEntrypoint;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.bundle.api.workspace.WorkspaceEntity;
import org.touchhome.bundle.api.workspace.scratch.Scratch3Block;
import org.touchhome.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.touchhome.bundle.hardware.Scratch3HardwareBlocks;
import org.touchhome.bundle.http.Scratch3NetworkBlocks;
import org.touchhome.bundle.media.Scratch3AudioBlocks;
import org.touchhome.bundle.media.Scratch3ImageEditBlocks;
import org.touchhome.bundle.ui.Scratch3UIBlocks;
import org.touchhome.common.exception.NotFoundException;
import org.touchhome.common.exception.ServerException;

@Log4j2
@RestController
@RequiredArgsConstructor
@RequestMapping("/rest/workspace")
public class WorkspaceController implements ContextRefreshed {

  private static final Pattern ID_PATTERN = Pattern.compile("[a-z-]*");

  private static final List<Class<?>> systemScratches = Arrays.asList(Scratch3ControlBlocks.class, Scratch3MiscBlocks.class,
      Scratch3DataBlocks.class, Scratch3EventsBlocks.class, Scratch3OperatorBlocks.class, Scratch3MutatorBlocks.class);

  private static final List<Class<?>> inlineScratches = Arrays.asList(Scratch3AudioBlocks.class,
      Scratch3NetworkBlocks.class, Scratch3HardwareBlocks.class, Scratch3UIBlocks.class, Scratch3ImageEditBlocks.class);

  private final BundleController bundleController;
  private final EntityContext entityContext;
  private final BundleService bundleService;
  private final WorkspaceManager workspaceManager;

  private List<Scratch3ExtensionImpl> extensions;

  @Override
  public void onContextRefresh() {
    List<Scratch3ExtensionImpl> oldExtension = this.extensions == null ? Collections.emptyList() : this.extensions;
    this.extensions = new ArrayList<>();
    for (Scratch3ExtensionBlocks scratch3ExtensionBlock : entityContext.getBeansOfType(Scratch3ExtensionBlocks.class)) {
      scratch3ExtensionBlock.init();

      if (!ID_PATTERN.matcher(scratch3ExtensionBlock.getId()).matches()) {
        throw new IllegalArgumentException(
            "Wrong Scratch3Extension: <" + scratch3ExtensionBlock.getId() + ">. Must contains [a-z] or '-'");
      }

      if (!systemScratches.contains(scratch3ExtensionBlock.getClass())) {
        BundleEntrypoint bundleEntrypoint = bundleController.getBundle(scratch3ExtensionBlock.getId());
        if (bundleEntrypoint == null && scratch3ExtensionBlock.getId().contains("-")) {
          bundleEntrypoint = bundleController.getBundle(
              scratch3ExtensionBlock.getId().substring(0, scratch3ExtensionBlock.getId().indexOf("-")));
        }
        int order = Integer.MAX_VALUE;
        if (bundleEntrypoint == null) {
          if (!inlineScratches.contains(scratch3ExtensionBlock.getClass())) {
            throw new ServerException("Unable to find bundle context with id: " + scratch3ExtensionBlock.getId());
          }
        } else {
          order = bundleEntrypoint.order();
        }
        Scratch3ExtensionImpl scratch3ExtensionImpl = new Scratch3ExtensionImpl(scratch3ExtensionBlock, order);

        if (!oldExtension.contains(scratch3ExtensionImpl)) {
          insertScratch3Spaces(scratch3ExtensionBlock);
        }
        extensions.add(scratch3ExtensionImpl);
      }
    }
    Collections.sort(extensions);
  }

  @GetMapping("/extension")
  public List<Scratch3ExtensionImpl> getExtensions() {
    return extensions;
  }

  @GetMapping("/extension/{bundleID}.png")
  public ResponseEntity<InputStreamResource> getExtensionImage(@PathVariable("bundleID") String bundleID) {
    BundleEntrypoint bundleEntrypoint = bundleService.getBundle(bundleID);
    InputStream stream = bundleEntrypoint.getClass().getClassLoader()
        .getResourceAsStream("extensions/" + bundleEntrypoint.getBundleId() + ".png");
    if (stream == null) {
      stream = bundleEntrypoint.getClass().getClassLoader().getResourceAsStream(bundleEntrypoint.getBundleImage());
    }
    if (stream == null) {
      throw new NotFoundException("Unable to find workspace extension bundle image for bundle: " + bundleID);
    }
    return TouchHomeUtils.inputStreamToResource(stream, MediaType.IMAGE_PNG);
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

    Map<String, WorkspaceGroup> groups =
        entityContext.findAll(WorkspaceGroup.class).stream()
            .collect(Collectors.toMap(WorkspaceGroup::getGroupId, g -> g));
    WorkspaceGroup broadcasts = groups.remove("broadcasts");
    JSONObject broadcastsVariables = new JSONObject();
    result.put("broadcasts", broadcastsVariables);
    if (broadcasts != null) {
      for (WorkspaceVariable broadcastVariable : broadcasts.getWorkspaceVariables()) {
        broadcastsVariables.put(broadcastVariable.getVariableId(), broadcastVariable.getName());
      }
    }

    JSONObject groupVariables = new JSONObject();
    result.put("group_variables", groupVariables);
    for (WorkspaceGroup workspaceGroup : groups.values()) {
      groupVariables.put(workspaceGroup.getGroupId(), new JSONArray()
          .put(workspaceGroup.getName())
          .put(new JSONArray())
          .put(new JSONArray()));

      for (WorkspaceVariable variable : workspaceGroup.getWorkspaceVariables()) {
        JSONArray variables = groupVariables.getJSONArray(variable.getWorkspaceGroup().getGroupId()).getJSONArray(2);
        variables.put(new JSONObject()
            .put("name", variable.getName())
            .put("type", "group_variables_group")
            .put("id_", variable.getVariableId())
            .put("restriction", variable.getRestriction()));
      }
    }

    return result.toString();
  }

  @GetMapping("/variable/{type}")
  public List<OptionModel> getWorkspaceVariables(@PathVariable("type") String type) {
    return OptionModel.entityList(entityContext.findAllByPrefix(type));
  }

  @SneakyThrows
  @PostMapping("/{entityID}")
  public void saveWorkspace(@PathVariable("entityID") String entityID, @RequestBody String json) {
    WorkspaceEntity workspaceEntity = entityContext.getEntity(entityID);
    entityContext.save(workspaceEntity.setContent(json));
  }

  @SneakyThrows
  @PostMapping("/variable")
  public void saveVariables(@RequestBody String json) {
    JSONObject request = new JSONObject(json);
    Map<String, WorkspaceGroup> groups =
        entityContext.findAll(WorkspaceGroup.class).stream()
            .collect(Collectors.toMap(WorkspaceGroup::getGroupId, g -> g));

    JSONObject broadcasts = request.getJSONObject("broadcasts");
    WorkspaceGroup broadcastGroup = groups.remove("broadcasts");
    if (broadcastGroup == null) {
      EntityContextVarImpl.createBroadcastGroup(entityContext);
      broadcastGroup = entityContext.getEntity(WorkspaceGroup.PREFIX + "broadcasts");
    }
    Set<String> existedBroadcasts =
        Optional.ofNullable(broadcastGroup.getWorkspaceVariables()).orElse(Collections.emptySet()).stream().map(
            WorkspaceVariable::getEntityID).collect(Collectors.toSet());
    for (String broadcastId : broadcasts.keySet()) {
      existedBroadcasts.remove(createOrRenameVariable(broadcastGroup, broadcastId,
          broadcasts.getString(broadcastId)).getEntityID());
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
      List<String> existedVariables =
          workspaceGroup.getWorkspaceVariables().stream().map(BaseEntity::getEntityID).collect(
              Collectors.toList());
      if (variables != null) {
        for (int i = 0; i < variables.length(); i++) {
          JSONObject variable = variables.getJSONObject(i);
          existedVariables.remove(createOrRenameVariable(workspaceGroup, variable.getString("id_"),
              variable.getString("name")).getEntityID());
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
    }
  }

  @GetMapping("/tab")
  public List<OptionModel> getWorkspaceTabs() {
    List<WorkspaceEntity> tabs = entityContext.findAll(WorkspaceEntity.class);
    Collections.sort(tabs);
    return OptionModel.entityList(tabs);
  }

  @SneakyThrows
  @PostMapping("/tab/{name}")
  public OptionModel createWorkspaceTab(@PathVariable("name") String name) {
    WorkspaceEntity workspaceEntity = entityContext.getEntity(WorkspaceEntity.PREFIX + name);
    if (workspaceEntity == null) {
      WorkspaceEntity entity = entityContext.save(new WorkspaceEntity().setName(name).computeEntityID(() -> name));
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
  @Secured(ADMIN_ROLE)
  public void renameWorkspaceTab(@PathVariable("entityID") String entityID, @RequestBody OptionModel option) {
    WorkspaceEntity entity = entityContext.getEntity(entityID);
    if (entity == null) {
      throw new NotFoundException("Unable to find workspace tab with id: " + entityID);
    }

    if (!WorkspaceRepository.GENERAL_WORKSPACE_TAB_NAME.equals(entity.getName()) &&
        !WorkspaceRepository.GENERAL_WORKSPACE_TAB_NAME.equals(option.getKey())) {

      WorkspaceEntity newEntity = entityContext.getEntityByName(option.getKey(), WorkspaceEntity.class);

      if (newEntity == null) {
        entityContext.save(entity.setName(option.getKey()));
      } else {
        throw new IllegalArgumentException("Workspace tab with name <" + option.getKey() + "> already exists");
      }
    }
  }

  @DeleteMapping("/tab/{entityID}")
  @Secured(ADMIN_ROLE)
  public void deleteWorkspaceTab(@PathVariable("entityID") String entityID) {
    WorkspaceEntity entity = entityContext.getEntity(entityID);
    if (entity == null) {
      throw new NotFoundException("Unable to find workspace tab with id: " + entityID);
    }
    if (WorkspaceRepository.GENERAL_WORKSPACE_TAB_NAME.equals(entity.getName())) {
      throw new IllegalArgumentException("REMOVE_MAIN_TAB");
    }
    if (!workspaceManager.isEmpty(entity.getContent())) {
      throw new IllegalArgumentException("REMOVE_NON_EMPTY_TAB");
    }
    entityContext.delete(entityID);
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

  private WorkspaceVariable createOrRenameVariable(WorkspaceGroup workspaceGroup, String variableId, String variableName) {
    WorkspaceVariable workspaceVariable = entityContext.getEntity(WorkspaceVariable.PREFIX + variableId);
    if (workspaceVariable == null) {
      return entityContext.save(new WorkspaceVariable().setVariableId(variableId).setWorkspaceGroup(workspaceGroup)
          .setName(variableName));
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
