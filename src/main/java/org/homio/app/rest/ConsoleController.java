package org.homio.app.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.val;
import org.homio.api.Context;
import org.homio.api.ContextNetwork;
import org.homio.api.console.ConsolePlugin;
import org.homio.api.console.ConsolePluginEditor;
import org.homio.api.console.ConsolePluginFrame;
import org.homio.api.console.ConsolePluginTable;
import org.homio.api.entity.BaseEntity;
import org.homio.api.exception.NotFoundException;
import org.homio.api.exception.ServerException;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.model.OptionModel;
import org.homio.api.service.ssh.SshBaseEntity;
import org.homio.api.service.ssh.SshProviderService;
import org.homio.api.service.ssh.SshProviderService.SshSession;
import org.homio.api.setting.console.header.ConsoleHeaderSettingPlugin;
import org.homio.api.ui.field.action.HasDynamicContextMenuActions;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.action.v1.UIInputEntity;
import org.homio.api.util.CommonUtils;
import org.homio.app.LogService;
import org.homio.app.builder.ui.UIInputBuilderImpl;
import org.homio.app.console.LogsConsolePlugin;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.manager.common.impl.ContextUIImpl;
import org.homio.app.model.entity.SettingEntity;
import org.homio.app.model.entity.user.UserGuestEntity;
import org.homio.app.model.rest.EntityUIMetaData;
import org.homio.app.rest.ItemController.ActionModelRequest;
import org.homio.app.setting.system.SystemFramesSetting;
import org.homio.app.spring.ContextCreated;
import org.homio.app.spring.ContextRefreshed;
import org.homio.app.utils.OptionUtil;
import org.homio.app.utils.UIFieldUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static org.homio.api.util.Constants.ROLE_ADMIN_AUTHORIZE;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;
import static org.homio.app.model.entity.user.UserBaseEntity.log;

@RestController
@RequestMapping(value = "/rest/console", produces = "application/json")
@RequiredArgsConstructor
public class ConsoleController implements ContextCreated, ContextRefreshed {

  @Getter
  private static final Map<String, SshSession<?>> sessions = new HashMap<>();
  private final LogService logService;
  private final ContextImpl context;
  private final ItemController itemController;
  private final Map<String, ConsolePlugin<?>> logsConsolePluginsMap = new HashMap<>();
  private final List<ConsoleTab> logs = new ArrayList<>();

  private static void addConsolePlugin(Entry<String, ConsolePlugin<?>> entry,
                                       Map<String, ConsoleTab> parens,
                                       Set<ConsoleTab> tabs,
                                       boolean removable) {
    if (entry.getKey().equals("icl")) {
      return;
    }
    ConsolePlugin<?> consolePlugin = entry.getValue();
    try {
      if (!consolePlugin.isEnabled()) {
        return;
      }
    } catch (Exception ignore) {
      return;
    }
    String parentName = consolePlugin.getParentTab();
    ConsoleTab consoleTab = new ConsoleTab(entry.getKey(), consolePlugin.getRenderType(), consolePlugin.getOptions());
    if (removable) {
      consoleTab.setDeletable(true);
    }
    consolePlugin.assembleOptions(consoleTab.getOptions());

    if (parentName != null) {
      parens.putIfAbsent(parentName, new ConsoleTab(parentName, null, null));
      parens.get(parentName).addChild(consoleTab);
    } else {
      tabs.add(consoleTab);
    }
  }

  /**
   * Get header actions for console plugin
   *
   * @param consolePlugin  - console plugin
   * @param uiInputBuilder - builder
   */
  @SneakyThrows
  static void fetchUIHeaderActions(
    ConsolePlugin<?> consolePlugin, UIInputBuilder uiInputBuilder) {
    val actionMap = consolePlugin.getHeaderActions();
    if (actionMap != null) {
      for (val entry : actionMap.entrySet()) {
        Class<? extends ConsoleHeaderSettingPlugin<?>> settingClass = entry.getValue();
        ((UIInputBuilderImpl) uiInputBuilder).addFireActionBeforeChange(entry.getKey(), CommonUtils.newInstance(settingClass).fireActionsBeforeChange(),
          SettingEntity.getKey(settingClass), 0);
      }
    }
    if (consolePlugin instanceof ConsolePluginEditor) {
      Class<? extends ConsoleHeaderSettingPlugin<?>> nameHeaderAction = ((ConsolePluginEditor) consolePlugin).getFileNameHeaderAction();
      if (nameHeaderAction != null) {
        ((UIInputBuilderImpl) uiInputBuilder).addReferenceAction("name", SettingEntity.getKey(nameHeaderAction), 1);
      }
    }
  }

  @Override
  public void onContextRefresh(Context context) throws Exception {
    List<ConsolePlugin> consolePlugins = new ArrayList<>(this.context.getBeansOfType(ConsolePlugin.class));
    Collections.sort(consolePlugins);
    for (ConsolePlugin<?> consolePlugin : consolePlugins) {
      ContextUIImpl.consolePluginsMap.put(consolePlugin.getName(), consolePlugin);
    }
  }

  @Override
  public void onContextCreated(ContextImpl context) throws Exception {
    for (String tab : logService.getTabs()) {
      logs.add(new ConsoleTab(tab, ConsolePlugin.RenderType.lines, null));
      logsConsolePluginsMap.put(tab, new LogsConsolePlugin(this.context, logService, tab));
    }

    ObjectNode nodes = context.setting().getValue(SystemFramesSetting.class);
    nodes.fields().forEachRemaining(entry -> {
      entry.getValue().fields().forEachRemaining(child -> {
        JsonNode node = child.getValue();
        String host = node.get("host").asText();
        String tabName = child.getKey();
        String targetHost = buildTargetHost(host, node.get("proxy").asBoolean());
        addFramePlugin(tabName, entry.getKey(), targetHost);
      });
    });
  }

  @GetMapping("/tab/{tab}/content")
  public Object getTabContent(@PathVariable("tab") String tab) {
    ConsolePlugin<?> consolePlugin = ContextUIImpl.getPlugin(tab);
    if (consolePlugin == null) {
      consolePlugin = logsConsolePluginsMap.get(tab);
      if (consolePlugin == null) {
        throw new IllegalArgumentException("Unable to find console plugin with name: " + tab);
      }
    }
    UserGuestEntity.assertConsoleAccess(context, consolePlugin);

    if (consolePlugin instanceof ConsolePluginTable<? extends HasEntityIdentifier> tableConsolePlugin) {
      Collection<? extends HasEntityIdentifier> baseEntities = tableConsolePlugin.getValue();
      Class<? extends HasEntityIdentifier> clazz = tableConsolePlugin.getEntityClass();

      List<Collection<UIInputEntity>> dynamicActions = new ArrayList<>();
      for (HasEntityIdentifier entity : baseEntities) {
        UIInputBuilder uiInputBuilder = context.ui().inputBuilder();
        if (entity instanceof HasDynamicContextMenuActions da) {
          da.assembleActions(uiInputBuilder);
          dynamicActions.add(uiInputBuilder.buildAll());
        } else {
          dynamicActions.add(null);
        }
      }

      return new EntityContent()
        .setList(baseEntities)
        .setActions(UIFieldUtils.fetchUIActionsFromClass(clazz, context))
        .setDynamicActions(dynamicActions)
        .setUiFields(UIFieldUtils.fillEntityUIMetadataList(clazz, context));
    }
    return consolePlugin.getValue();
  }

  @GetMapping("/tab/{tab}/actions")
  public Collection<UIInputEntity> getConsoleTabActions(@PathVariable("tab") String tab) {
    UIInputBuilder uiInputBuilder = context.ui().inputBuilder();
    ConsolePlugin<?> consolePlugin = ContextUIImpl.getPlugin(tab);
    if (consolePlugin != null) {
      fetchUIHeaderActions(consolePlugin, uiInputBuilder);
    }
    return uiInputBuilder.buildAll();
  }

  @SneakyThrows
  @PostMapping("/tab/{tab}/action")
  @PreAuthorize(ROLE_ADMIN_AUTHORIZE)
  public ActionResponseModel executeAction(@PathVariable("tab") String tab, @RequestBody ActionModelRequest request) {
    String entityID = request.getEntityID();
    ConsolePlugin<?> consolePlugin = ContextUIImpl.getPlugin(tab);
    if (consolePlugin instanceof ConsolePluginTable table) {
      HasEntityIdentifier identifier = table.findEntity(entityID);
      return itemController.executeAction(request, identifier, context.db().get(identifier.getEntityID()));
    }
    return consolePlugin.executeAction(entityID, request.getParams());
  }

  @GetMapping("/tab/{tab}/{entityID}/{fieldName}/options")
  public Collection<OptionModel> loadSelectOptions(
    @PathVariable("tab") String tab,
    @PathVariable("entityID") String entityID,
    @PathVariable("fieldName") String fieldName) {
    ConsolePlugin<?> consolePlugin = ContextUIImpl.getPlugin(tab);
    if (consolePlugin instanceof ConsolePluginTable) {
      Collection<? extends HasEntityIdentifier> baseEntities = ((ConsolePluginTable<? extends HasEntityIdentifier>) consolePlugin).getValue();
      HasEntityIdentifier identifier =
        baseEntities.stream().filter(e -> e.getEntityID().equals(entityID)).findAny().orElseThrow(
          () -> NotFoundException.entityNotFound(entityID));
      return OptionUtil.loadOptions(identifier, context, fieldName, null, null, null);
    }
    return null;
  }

  @GetMapping("/tab")
  public Set<ConsoleTab> getTabs() {
    Set<ConsoleTab> tabs = new HashSet<>();
    try {
      UserGuestEntity.assertLogAccess(context);

      ConsoleTab logsTab = new ConsoleTab("logs", null, null);
      logs.forEach(logsTab::addChild);
      tabs.add(logsTab);
    } catch (Exception ignore) {
    }

    Map<String, ConsoleTab> parens = new HashMap<>();
    for (Map.Entry<String, ConsolePlugin<?>> entry : ContextUIImpl.consolePluginsMap.entrySet()) {
      addConsolePlugin(entry, parens, tabs, false);
    }
    for (Map.Entry<String, ConsolePlugin<?>> entry : ContextUIImpl.consoleRemovablePluginsMap.entrySet()) {
      addConsolePlugin(entry, parens, tabs, true);
    }
    tabs.addAll(parens.values());

    // register still unavailable console plugins if any
    for (String pluginName : ContextUIImpl.customConsolePluginNames) {
      if (tabs.stream().noneMatch(t -> t.name.equals(pluginName))) {
        tabs.add(new ConsoleTab(pluginName, null, null));
      }
    }
    return tabs;
  }

  @PostMapping("/ssh/{entityID}/tab/{tabID}")
  public SshProviderService.SshSession openSshSession(
    @PathVariable("entityID") String entityID,
    @PathVariable("tabID") String tabID) {
    log.info("Request to open ssh: {}. TabID: {}", entityID, tabID);
    UserGuestEntity.assertSshAccess(context);

    SshSession session = sessions
      .values().stream()
      .filter(s -> tabID.equals(s.getMetadata().get("tabID")))
      .findAny().orElse(null);
    if (session != null) {
      return session;
    }

    BaseEntity entity = context.db().get(entityID);
    if (entity instanceof SshBaseEntity) {
      SshProviderService service = ((SshBaseEntity<?, ?>) entity).getService();
      SshSession sshSession = service.openSshSession((SshBaseEntity) entity);
      if (sshSession != null) {
        sshSession.getMetadata().put("tabID", tabID);
        sessions.put(sshSession.getToken(), sshSession);
      }
      return sshSession;
    }
    throw new IllegalArgumentException("Entity: " + entityID + " has to implement 'SshBaseEntity'");
  }

  @GetMapping("/ssh/tabs")
  public List<SshTab> getOpenedSshTabs() {
    UserGuestEntity.assertSshAccess(context);
    return sessions
      .values()
      .stream()
      .map(s -> new SshTab(s.getMetadata().get("tabID"), s.getEntity().getName()))
      .toList();
  }

  @PostMapping("/ssh/{token}/resize")
  public void resizeSshConsole(@PathVariable("token") String token, @RequestBody SshResizeRequest request) {
    UserGuestEntity.assertSshAccess(context);
    SshSession<?> sshSession = sessions.get(token);
    if (sshSession != null) {
      ((SshProviderService) sshSession.getEntity().getService())
        .resizeSshConsole(sshSession, request.cols);
    }
  }

  @DeleteMapping("/ssh/{token}")
  public void closeSshSession(@PathVariable("token") String token) {
    UserGuestEntity.assertSshAccess(context);
    SshSession<?> sshSession = sessions.remove(token);
    if (sshSession != null) {
      ((SshProviderService) sshSession.getEntity().getService()).closeSshSession(sshSession);
    }
  }

  @SneakyThrows
  @PostMapping("/frame")
  @PreAuthorize(ROLE_ADMIN_AUTHORIZE)
  public void createFrame(@RequestBody CreateFrameRequest request) {
    if (!request.host.startsWith("http")) {
      request.host = "http://" + request.host;
    }
    URL url = new URL(request.host);
    ContextNetwork.ping(url.getHost(), url.getPort() == -1 ? 80 : url.getPort());
    if (context.ui().console().getRegisteredPlugin(request.name) != null) {
      throw new ServerException("Console plugin %s already exists".formatted(request.name));
    }
    ObjectNode nodes = context.setting().getValue(SystemFramesSetting.class);
    nodes.putIfAbsent(request.getParent(), OBJECT_MAPPER.createObjectNode());
    ObjectNode node = (ObjectNode) nodes.get(request.getParent());
    ObjectNode pluginData = OBJECT_MAPPER.createObjectNode()
      .put("proxy", request.proxy)
      .put("host", request.host);
    node.set(request.getName(), pluginData);
    context.setting().setValue(SystemFramesSetting.class, nodes);

    String targetHost = buildTargetHost(request.host, request.proxy);
    addFramePlugin(request.name, request.parent, targetHost);
  }

  private String buildTargetHost(String targetHost, boolean proxy) {
    if (proxy) {
      return context.service().registerUrlProxy(String.valueOf(Math.abs(targetHost.hashCode())), targetHost, builder -> {
      });
    }
    return targetHost;
  }

  @SneakyThrows
  @DeleteMapping("/frame/{parent}/{name}")
  @PreAuthorize(ROLE_ADMIN_AUTHORIZE)
  public boolean removeFrame(@PathVariable("parent") String parent, @PathVariable("name") String name) {
    ObjectNode nodes = context.setting().getValue(SystemFramesSetting.class);
    if (!nodes.has(parent)) {
      throw new ServerException("No parent tab: " + parent + " found");
    }
    ObjectNode parentNode = (ObjectNode) nodes.get(parent);
    JsonNode pluginData = parentNode.remove(name);
    if (pluginData == null) {
      throw new ServerException("No tab: " + name + " found");
    }
    if (pluginData.has("proxyHost")) {
      context.service().unRegisterUrlProxy(pluginData.get("host").asText());
    }

    context.ui().console().unRegisterPlugin(name);
    if (parentNode.isEmpty()) {
      nodes.remove(parent);
    }

    context.setting().setValue(SystemFramesSetting.class, nodes);
    return nodes.isEmpty();
  }

  private void addFramePlugin(String host, String parent, String targetHost) {
    context.ui().console().registerPlugin(host, new ConsolePluginFrame() {

      @Override
      public @Nullable String getParentTab() {
        return parent;
      }

      @Override
      public @NotNull Context context() {
        return context;
      }

      @Override
      public FrameConfiguration getValue() {
        return new FrameConfiguration(targetHost);
      }
    }, true);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  @RequiredArgsConstructor
  public static class ConsoleTab {

    private final @NotNull String name;
    private final @Nullable ConsolePlugin.RenderType renderType;
    private boolean deletable;
    private List<ConsoleTab> children;
    private JSONObject options;

    public ConsoleTab(@NotNull String name, @Nullable ConsolePlugin.RenderType renderType, @Nullable JSONObject options) {
      this(name, renderType);
      this.options = options == null ? new JSONObject() : options;
    }

    public void addChild(ConsoleTab consoleTab) {
      if (this.children == null) {
        this.children = new ArrayList<>();
      }
      this.children.add(consoleTab);
    }
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class EntityContent {

    Collection<?> list;
    List<EntityUIMetaData> uiFields;
    Collection<UIInputEntity> actions;
    List<Collection<UIInputEntity>> dynamicActions;
  }

  @Setter
  public static class SshResizeRequest {

    private int cols;
  }

  @Getter
  @Setter
  public static class CreateFrameRequest {

    private String name;
    private String parent;
    private String host;
    private boolean proxy;
  }

  @Getter
  @RequiredArgsConstructor
  public static class SshTab {

    private final String entityID;
    private final String name;
  }
}
