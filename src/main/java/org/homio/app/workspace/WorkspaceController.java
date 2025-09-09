package org.homio.app.workspace;

import static org.homio.api.ContextVar.GROUP_BROADCAST;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.api.exception.NotFoundException;
import org.homio.api.model.OptionModel;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.WorkspaceEntity;
import org.homio.app.model.var.WorkspaceGroup;
import org.homio.app.model.var.WorkspaceVariable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.*;

@Log4j2
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/rest/workspace", produces = "application/json")
public class WorkspaceController {

  private final ContextImpl context;
  private final WorkspaceService workspaceService;

  @SneakyThrows
  @GetMapping("/http/{path}")
  public String handleNetScratch(
      @PathVariable("path") String path, @RequestParam Map<String, String> params) {
    var locks = context.network().getScratchHttpRequests().get(path);
    if (locks == null) {
      throw new NotFoundException("Unable to find lock for path: " + path);
    }
    CompletableFuture<String> requestFuture = null;
    if (locks.answerLockRef() != null) {
      requestFuture = new CompletableFuture<>();
      locks.answerLockRef().set(requestFuture);
    }
    locks.lock().signalAll(params);
    return requestFuture == null ? null : requestFuture.get(60, TimeUnit.SECONDS);
  }

  @GetMapping("/extension")
  public List<Scratch3ExtensionImpl> getExtensions() {
    List<Scratch3ExtensionImpl> extensions = new ArrayList<>(workspaceService.getExtensions());
    extensions.sort(null);
    return extensions;
  }

  @GetMapping("/{entityID}")
  public String getWorkspace(@PathVariable("entityID") String entityID) {
    WorkspaceEntity workspaceEntity = context.db().get(WorkspaceEntity.class, entityID);
    if (workspaceEntity == null) {
      throw new NotFoundException("Unable to find workspace tab with id: " + entityID);
    }
    return workspaceEntity.getContent();
  }

  @GetMapping("/variable")
  public String getWorkspaceVariables() {
    JSONObject result = new JSONObject();

    Map<String, WorkspaceGroup> groups =
        context.db().findAll(WorkspaceGroup.class).stream()
            .collect(Collectors.toMap(WorkspaceGroup::getEntityID, g -> g));
    WorkspaceGroup broadcasts = groups.remove(GROUP_BROADCAST);
    JSONObject broadcastsVariables = new JSONObject();
    result.put(GROUP_BROADCAST, broadcastsVariables);
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
  public void saveWorkspace(@PathVariable("entityID") String entityID, @RequestBody String json) {
    WorkspaceEntity workspaceEntity = context.db().get(WorkspaceEntity.class, entityID);
    if (workspaceEntity == null) {
      throw new NotFoundException("Unable to find workspace: " + entityID);
    }
    context.db().save(workspaceEntity.setContent(json));
  }

  @PostMapping("/variable")
  public void saveVariables(@RequestBody String ignore) {}

  @GetMapping("/tab")
  public List<OptionModel> getWorkspaceTabs() {
    return context.toOptionModels(context.db().findAll(WorkspaceEntity.class));
  }

  @SneakyThrows
  @PostMapping("/tab/{name}")
  public OptionModel createWorkspaceTab(@PathVariable("name") String name) {
    WorkspaceEntity entity = context.db().get(WorkspaceEntity.class, name);
    if (entity == null) {
      entity = new WorkspaceEntity(name, name);
      entity = context.db().save(entity);
      return OptionModel.of(entity.getEntityID(), entity.getTitle());
    }
    throw new IllegalArgumentException("Workspace tab with name <" + name + "> already exists");
  }

  @SneakyThrows
  @GetMapping("/tab/{name}")
  public boolean tabExists(@PathVariable("name") String name) {
    return context.db().get(WorkspaceEntity.class, name) != null;
  }

  @Setter
  public static class CreateVariable {

    String key;
    String varGroup;
    String varName;
  }
}
