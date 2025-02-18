package org.homio.app.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.homio.api.Context;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.EntityFieldMetadata;
import org.homio.api.entity.HasStatusAndMsg;
import org.homio.api.entity.UserEntity;
import org.homio.api.entity.device.DeviceBaseEntity;
import org.homio.api.entity.log.HasEntityLog;
import org.homio.api.entity.log.HasEntitySourceLog;
import org.homio.api.entity.version.HasFirmwareVersion;
import org.homio.api.entity.zigbee.ZigBeeDeviceBaseEntity;
import org.homio.api.exception.NotFoundException;
import org.homio.api.exception.ServerException;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.ActionResponseModel.ResponseAction;
import org.homio.api.model.FileModel;
import org.homio.api.model.OptionModel;
import org.homio.api.service.EntityService;
import org.homio.api.ui.UIActionHandler;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.UISidebarMenu;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.ui.field.UIFilterOptions;
import org.homio.api.ui.field.action.HasDynamicContextMenuActions;
import org.homio.api.ui.field.action.HasDynamicUIFields;
import org.homio.api.ui.field.action.UIActionButton;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.action.UIContextMenuUploadAction;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.action.v1.UIInputEntity;
import org.homio.api.ui.field.inline.UIFieldInlineEntities;
import org.homio.api.ui.field.selection.dynamic.DynamicOptionLoader;
import org.homio.api.ui.field.selection.dynamic.DynamicOptionLoader.DynamicOptionLoaderParameters;
import org.homio.api.ui.field.selection.dynamic.DynamicParameterFields;
import org.homio.api.ui.field.selection.dynamic.SelectionWithDynamicParameterFields;
import org.homio.api.ui.field.selection.dynamic.SelectionWithDynamicParameterFields.RequestDynamicParameter;
import org.homio.api.util.CommonUtils;
import org.homio.app.LogService;
import org.homio.app.config.cacheControl.CacheControl;
import org.homio.app.config.cacheControl.CachePolicy;
import org.homio.app.manager.common.ClassFinder;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.manager.common.EntityManager;
import org.homio.app.manager.common.impl.ContextUIImpl.ItemsContextMenuAction;
import org.homio.app.model.UIHideEntityIfFieldNotNull;
import org.homio.app.model.entity.DeviceFallbackEntity;
import org.homio.app.model.entity.user.UserGuestEntity;
import org.homio.app.model.entity.widget.impl.WidgetFallbackEntity;
import org.homio.app.model.rest.EntityUIMetaData;
import org.homio.app.model.var.WorkspaceGroup;
import org.homio.app.repository.AbstractRepository;
import org.homio.app.rest.UIFieldBuilderImpl.FieldBuilderImpl;
import org.homio.app.setting.system.SystemClearCacheButtonSetting;
import org.homio.app.setting.system.SystemShowEntityCreateTimeSetting;
import org.homio.app.setting.system.SystemShowEntityUpdateTimeSetting;
import org.homio.app.spring.ContextCreated;
import org.homio.app.spring.ContextRefreshed;
import org.homio.app.utils.OptionUtil;
import org.homio.app.utils.UIFieldUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.homio.api.util.Constants.ROLE_ADMIN_AUTHORIZE;

@Log4j2
@RestController
@RequestMapping(value = "/rest/item", produces = "application/json")
@RequiredArgsConstructor
public class ItemController implements ContextCreated, ContextRefreshed {

  private static final Map<String, List<Class<?>>> typeToEntityClassNames =
    new ConcurrentHashMap<>();
  public static Map<String, Class<?>> className2Class = new ConcurrentHashMap<>();
  public static Map<String, Class<?>> baseEntitySimpleClasses = new HashMap<>();
  private final Map<String, List<ItemContextResponse>> itemsBootstrapContextMap =
    new ConcurrentHashMap<>();
  private final ObjectMapper objectMapper;
  private final ContextImpl context;
  private final EntityManager entityManager;
  private final ClassFinder classFinder;
  private final LogService logService;
  private final ReentrantLock putItemsLock = new ReentrantLock();
  private final ReentrantLock updateItemLock = new ReentrantLock();
  private final Cache<String, Consumer<String>> fileSaveMapping = CacheBuilder
    .newBuilder().expireAfterAccess(Duration.ofMinutes(5)).build();

  private static void updateDynamicUIFieldValues(BaseEntity entity, JSONObject entityFields) {
    if (entity instanceof HasDynamicUIFields df) {
      UIFieldBuilderImpl builder = new UIFieldBuilderImpl();
      df.assembleUIFields(builder);
      for (String fieldName : entityFields.keySet()) {
        FieldBuilderImpl fieldBuilder = builder.getFields().get(fieldName);
        if (fieldBuilder != null) {
          fieldBuilder.getValue().update(entityFields.get(fieldName));
        }
      }
    }
  }

  @SneakyThrows
  static ActionResponseModel executeMethodAction(Method method, Object actionHolder, Context context,
                                                 BaseEntity actionEntity, JSONObject params) {
    List<Object> objects = new ArrayList<>();
    for (AnnotatedType parameterType : method.getAnnotatedParameterTypes()) {
      if (actionHolder.getClass().isAssignableFrom((Class<?>) parameterType.getType())) {
        objects.add(actionHolder);
      } else if (BaseEntity.class.isAssignableFrom((Class<?>) parameterType.getType())) {
        objects.add(actionEntity);
      } else if (JSONObject.class.isAssignableFrom((Class<?>) parameterType.getType())) {
        objects.add(params);
      } else {
        objects.add(context.getBean((Class<?>) parameterType.getType()));
      }
    }
    method.setAccessible(true);
    return (ActionResponseModel) method.invoke(actionHolder, objects.toArray());
  }

  @SneakyThrows
  private static Object findTargetObjectFromFieldInlineEntities(String variableEntityID, Object actionHolder, Method[] methods) {
    List items = (List) methods[0].invoke(actionHolder);
    for (Object item : items) {
      if (((UIFieldInlineEntities.InlineEntity) item).getEntityID().equals(variableEntityID)) {
        return item;
      }
    }
    return null;
  }

  @SneakyThrows
  private static boolean filterMatchFieldInlineEntity(String variableEntityID, Object i) {
    Field entityID;
    try {
      entityID = i.getClass().getField("entityID");
    } catch (Exception e) {
      entityID = i.getClass().getDeclaredField("ieeeAddress");
    }
    return variableEntityID.equals(FieldUtils.readField(entityID, i, true));
  }

  @PostConstruct
  public void postConstruct() {
    context.setting().listenValue(SystemClearCacheButtonSetting.class, "ic-clear-cache",
      typeToEntityClassNames::clear);
  }

  @SneakyThrows
  public ActionResponseModel executeAction(ActionModelRequest request, Object actionHolder, BaseEntity actionEntity) {
    ActionResponseModel response = executeActionInternal(request, actionHolder, actionEntity);
    if (response != null && ResponseAction.files == response.getResponseAction()) {
      for (FileModel fileModel : (Collection<FileModel>) response.getValue()) {
        if (fileModel.getSaveHandler() != null) {
          fileSaveMapping.put(fileModel.getName(), fileModel.getSaveHandler());
        }
      }
    }
    return response;
  }

  @Override
  public void onContextCreated(ContextImpl context) {
    this.context.setting().listenValue(SystemClearCacheButtonSetting.class, "boot-clear", itemsBootstrapContextMap::clear);
    // invalidate UIField cache scenarios
    this.context.setting().listenValue(SystemShowEntityCreateTimeSetting.class,
      "invalidate-uifield-createTime-cache", this.itemsBootstrapContextMap::clear);
    this.context.setting().listenValue(SystemShowEntityUpdateTimeSetting.class,
      "invalidate-uifield-updateTime-cache", this.itemsBootstrapContextMap::clear);
  }

  @Override
  public void onContextRefresh(Context context) {
    List<Class<? extends BaseEntity>> baseEntityClasses = classFinder.getClassesWithParent(BaseEntity.class);
    baseEntitySimpleClasses = baseEntityClasses.stream().collect(Collectors.toMap(Class::getSimpleName, s -> s));

    for (Class<? extends BaseEntity> baseEntityClass : baseEntityClasses) {
      Class<?> cursor = baseEntityClass.getSuperclass();
      while (!cursor.getSimpleName().equals(BaseEntity.class.getSimpleName())) {
        baseEntitySimpleClasses.put(cursor.getSimpleName(), cursor);
        cursor = cursor.getSuperclass();
      }
    }
  }

  @PostMapping("/{entityID}/{fieldName}/options")
  public Collection<OptionModel> loadSelectOptions(
    @PathVariable("entityID") String entityID,
    @PathVariable("fieldName") String fieldName,
    @RequestBody GetOptionsRequest optionsRequest) {
    Object classEntity = context.db().get(entityID);
    if (classEntity instanceof BaseEntity baseEntity) {
      context.user().getLoggedInUserRequire().assertViewAccess(baseEntity);
    }
    if (classEntity == null) {
      // i.e in case we load Widget
      if (entityID.contains(":GENERATED:")) {
        entityID = entityID.substring(0, entityID.indexOf(":"));
      }
      Class<?> aClass = entityManager.getUIFieldClassByType(entityID);
      if (aClass == null) {
        List<Class<?>> classImplementationsByType = findAllClassImplementationsByType(entityID);
        aClass = classImplementationsByType.isEmpty() ? null : classImplementationsByType.get(0);
      }
      if (aClass == null) {
        throw new IllegalArgumentException("Unable to find class for entity: " + entityID);
      }
      classEntity = CommonUtils.newInstance(aClass);
    }
    Object originalClassEntity = classEntity;
    if (isNotEmpty(optionsRequest.getFieldFetchType())) {
      classEntity = ContextImpl.getFetchType(optionsRequest.getFieldFetchType());
    }
    Class<?> entityClass = classEntity.getClass();
    List<OptionModel> options = getEntityOptions(fieldName, classEntity, entityClass);
    if (options != null) {
      return options;
    }

    return OptionUtil.loadOptions(classEntity, context, fieldName, originalClassEntity, optionsRequest.getSelectType(), optionsRequest.getDeps());
  }

  @GetMapping("/{type}/context")
  @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
  public List<ItemContextResponse> getItemsBootstrapContext(
    @PathVariable("type") String type,
    @RequestParam(value = "subType", defaultValue = "") String subType) {
    String key = type + subType;
    itemsBootstrapContextMap.computeIfAbsent(key, s -> buildItemBootstrap(type, subType));
    return itemsBootstrapContextMap.get(key);
  }

  @GetMapping("/{entityID}/firmwareUpdate/{version}/readme")
  public String getFirmwareUpdateReadme(
    @PathVariable("entityID") String entityID,
    @PathVariable("version") String version) {
    BaseEntity entity = context.db().get(entityID);
    if (!(entity instanceof HasFirmwareVersion firmware)) {
      throw new ServerException("Unable to update non HasFirmwareVersion entity");
    }
    return firmware.getFirmwareVersionReadme(version);
  }

  @PostMapping("/{entityID}/firmwareUpdate/{version}")
  @PreAuthorize(ROLE_ADMIN_AUTHORIZE)
  public void updateFirmware(
    @PathVariable("entityID") String entityID,
    @PathVariable("version") String version) {
    BaseEntity entity = context.db().get(entityID);
    if (!(entity instanceof HasFirmwareVersion firmware)) {
      throw new ServerException("Unable to update non HasFirmwareVersion entity");
    }
    if (firmware.isFirmwareUpdating()) {
      throw new ServerException("W.ERROR.UPDATE_IN_PROGRESS");
    }
    if (version.equals(firmware.getFirmwareVersion())) {
      throw new ServerException("Entity: %s already has version: %s".formatted(entity.getTitle(), version));
    }
    String key = "Update " + entity.getTitle() + "/" + version;
    context.ui().progress().runAndGet(key, false, progressBar ->
        firmware.update(progressBar, version),
      ex -> {
        if (ex != null) {
          context.ui().toastr().error(ex);
        }
        context.ui().updateItem(entity);
      });
  }

  @GetMapping("/options")
  public Collection<OptionModel> getAllOptions() {
    return OptionUtil.getAllOptions(context);
  }

  @GetMapping(value = "/{entityID}/logs")
  public ResponseEntity<StreamingResponseBody> getLogs(
    @PathVariable("entityID") String entityID,
    @RequestParam(value = "source", required = false) String sourceID) {
    UserGuestEntity.assertLogAccess(context);

    BaseEntity entity = context.db().getRequire(entityID);
    if (isNotEmpty(sourceID)) {
      if (!(entity instanceof HasEntitySourceLog)) {
        throw new IllegalStateException("Entity: " + entityID + " not implement HasEntitySourceLog interface");
      }
      return new ResponseEntity<>(
        outputStream -> {
          try (InputStream inputStream = ((HasEntitySourceLog) entity).getSourceLogInputStream(sourceID)) {
            if (inputStream == null) {
              throw new NotFoundException("");
            }
            inputStream.transferTo(outputStream);
          } catch (Exception e) {
            outputStream.write("No file found".getBytes());
          }
        },
        HttpStatus.OK);
    }
    Path logFile = logService.getEntityLogsFile(entity);
    if (logFile == null || !Files.exists(logFile)) {
      throw new IllegalArgumentException("Unable to find log file path for entity: " + entityID);
    }

    return new ResponseEntity<>(
      outputStream -> {
        try (FileChannel inChannel = FileChannel.open(logFile, StandardOpenOption.READ)) {
          long size = inChannel.size();
          WritableByteChannel writableByteChannel = Channels.newChannel(outputStream);
          inChannel.transferTo(0, size, writableByteChannel);
        }
      },
      HttpStatus.OK);
  }

  @GetMapping("/{type}/types")
  @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
  public Set<OptionModel> getImplementationsByBaseType(@PathVariable("type") String type) {
    // type nay be base class also
    Class<?> entityClassByType = entityManager.getUIFieldClassByType(type);
    if (entityClassByType == null) {
      putTypeToEntityIfNotExists(type);
      if (!typeToEntityClassNames.containsKey(type)) {
        return Collections.emptySet();
      }
    }
    Set<OptionModel> list = entityClassByType == null ? new HashSet<>() : fetchCreateItemTypes(entityClassByType);
    for (Class<?> aClass : typeToEntityClassNames.get(type)) {
      OptionModel option = getUISideBarMenuOption(aClass);
      if (option != null) {
        list.add(option);
      }
    }
    return list;
  }

  @GetMapping(value = "/{entityID}/logs/source")
  public @NotNull List<OptionModel> getSourceLogs(
    @PathVariable("entityID") String entityID) {
    BaseEntity entity = context.db().getRequire(entityID);
    context.user().getLoggedInUserRequire().assertViewAccess(entity);
    UserGuestEntity.assertLogAccess(context);

    if (entity instanceof HasEntitySourceLog) {
      return ((HasEntitySourceLog) entity).getLogSources();
    }
    throw new IllegalStateException("Entity: " + entityID + " not implement HasEntitySourceLog interface");
  }

  @GetMapping("/type/{type}/options")
  public List<OptionModel> getItemOptionsByType(@PathVariable("type") String type) {
    putTypeToEntityIfNotExists(type);
    List<BaseEntity> entities = new ArrayList<>();
    for (Class<?> aClass : typeToEntityClassNames.get(type)) {
      if (BaseEntity.class.isAssignableFrom(aClass)) {
        entities.addAll(context.db().findAll((Class<BaseEntity>) aClass));
      }
    }
    if (type.equals(WorkspaceGroup.class.getSimpleName())) {
      entities.removeIf(e -> e.getEntityID().equals("group_broadcast") || e.getEntityID().equals("group_hardware"));
    }
    if (!entities.isEmpty()) {
      return context.toOptionModels(entities);
    }
    Class<?> dynamicClass = className2Class.get(type);
    if (dynamicClass != null) {
      DynamicOptionLoader loader = (DynamicOptionLoader) CommonUtils.newInstance(dynamicClass);
      return loader.loadOptions(new DynamicOptionLoaderParameters(null, context, null, null));
    }
    return List.of();
  }

  @PostMapping(value = "/{entityID}/context/actionWithBinary")
  public ActionResponseModel callActionWithBinary(
    @PathVariable("entityID") String entityID,
    @RequestPart ActionModelRequest request,
    @RequestParam("data") MultipartFile[] files) {
    try {
      if (entityID.equals("FILE_SAVE_ACTION")) {
        callSaveFilesAction(files);
        return null;
      }

      BaseEntity entity = context.db().getRequire(entityID);
      context.user().getLoggedInUserRequire().assertViewAccess(entity);
      if (request.params == null) {
        request.params = new JSONObject();
      }
      request.params.put("files", files);
      request.params.put("entityID", entityID);
      return executeAction(request, entity, entity);
    } catch (Exception ex) {
      log.error("Error while execute action: {}", CommonUtils.getErrorMessage(ex));
      return ActionResponseModel.showError(ex);
    }
  }

  @PostMapping(value = "/{entityID}/context/action")
  public ActionResponseModel callAction(@PathVariable("entityID") String entityID,
                                        @RequestBody ActionModelRequest request) {
    return callActionWithBinary(entityID, request, null);
  }

  @PostMapping("/{entityID}/notification/action")
  @PreAuthorize(ROLE_ADMIN_AUTHORIZE)
  public ActionResponseModel notificationAction(@PathVariable("entityID") String entityID,
                                                @RequestBody ActionModelRequest request) throws Exception {
    return context.ui().handleNotificationAction(entityID, request.entityID, request.params);
  }

  private void callSaveFilesAction(MultipartFile[] files) throws IOException {
    for (MultipartFile file : files) {
      Consumer<String> saveHandler = fileSaveMapping.getIfPresent(file.getOriginalFilename());
      if (saveHandler != null) {
        try (InputStream stream = file.getInputStream()) {
          saveHandler.accept(IOUtils.toString(stream, StandardCharsets.UTF_8));
        }
      }
    }
  }

  @GetMapping("/{type}/actions")
  @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
  public Collection<UIInputEntity> getItemsActions(@PathVariable("type") String type) {
    Class<?> entityClassByType = entityManager.getUIFieldClassByType(type);
    return UIFieldUtils.fetchUIActionsFromClass(entityClassByType, context);
  }

  @PostMapping("/{type}")
  public BaseEntity create(@PathVariable("type") String type) {
    log.debug("Request creating entity by type: <{}>", type);
    Class<? extends EntityFieldMetadata> typeClass = ContextImpl.uiFieldClasses.get(type);
    if (typeClass == null) {
      throw new IllegalArgumentException("Unable to find base entity with type: " + type);
    }
    BaseEntity baseEntity = (BaseEntity) CommonUtils.newInstance(typeClass);
    return context.db().save(baseEntity);
  }

  @PostMapping("/{entityID}/copy")
  public BaseEntity copyEntityByID(@PathVariable("entityID") String entityID) {
    return context.db().copyEntity(context.db().getRequire(entityID));
  }

  @DeleteMapping("/{entityID}")
  public void removeEntity(@PathVariable("entityID") String entityID) {
    context.db().delete(entityID);
  }

  @GetMapping("/{entityID}/dependencies")
  public List<String> canRemove(@PathVariable("entityID") String entityID) {
    BaseEntity entity = context.db().get(entityID);
    context.user().getLoggedInUserRequire().assertEditAccess(entity);
    if (entity == null) {
      entity = context.db().get(entityID, false);
      if (entity == null) {
        context.getCacheService().clearCache();
        context.ui().dialog().reloadWindow("Clear cache");
        return Collections.emptyList();
      }
    }
    AbstractRepository repository = ContextImpl.getRepository(entity.getEntityID());
    Collection<BaseEntity> usages = getUsages(entityID, repository);
    return usages.stream().map(Object::toString).collect(Collectors.toList());
  }

  /**
   * synchronized because if we add few series to item/widget/etc... and pushing changes to server in parallel we may face with loose changes when merging
   * changes from new income data and saved in db
   */
  @PutMapping
  @SneakyThrows
  public BaseEntity updateItems(@RequestBody String json) {
    updateItemLock.lock();
    try {
      JSONObject jsonObject = new JSONObject(json);
      BaseEntity resultField = null;
      for (String entityId : jsonObject.keySet()) {
        log.info("Put update item: <{}>", entityId);
        BaseEntity entity = context.db().get(entityId);
        context.user().getLoggedInUserRequire().assertEditAccess(entity);

        if (entity == null) {
          throw NotFoundException.entityNotFound(entityId);
        }

        JSONObject entityFields = jsonObject.getJSONObject(entityId);
        entity = objectMapper.readerForUpdating(entity).readValue(entityFields.toString());

        updateDynamicUIFieldValues(entity, entityFields);

        // reference fields isn't updatable, we need update them manually
        for (String fieldName : entityFields.keySet()) {
          Field field = FieldUtils.getField(entity.getClass(), fieldName, true);
          if (field != null && BaseEntity.class.isAssignableFrom(field.getType())) {
            BaseEntity refEntity = context.db().get(entityFields.getString(fieldName));
            FieldUtils.writeField(field, entity, refEntity);
          }
        }

        // update entity
        BaseEntity savedEntity = context.db().save(entity);
        if (resultField == null) {
          resultField = savedEntity;
        }
        context.ui().unRegisterForUpdates(entityId);
      }
      return resultField;
    } finally {
      updateItemLock.unlock();
    }
  }

  @GetMapping("/{type}/list")
  public List<BaseEntity> getItems(@PathVariable("type") String type) {
    putTypeToEntityIfNotExists(type);
    List<BaseEntity> items = new ArrayList<>();

    for (Class<?> aClass : typeToEntityClassNames.get(type)) {
      if (BaseEntity.class.isAssignableFrom(aClass)) {
        items.addAll(context.db().findAll((Class<BaseEntity>) aClass));
      }
    }
    items.removeIf(this::isRemoveItemFromResult);
    Collections.sort(items);
    return items;
  }

  /**
   * Fetch dynamic data for every entity on UI. Calls every on every refresh page
   *
   * @param entityIDs - list of entities on ui
   * @return - Map(entityID - data)
   */
  @PostMapping("/dynamicData")
  public Map<String, EntityDynamicData> getActions(@RequestBody List<String> entityIDs) {
    Map<String, EntityDynamicData> contextActions = new HashMap<>();
    UserEntity user = context.user().getLoggedInUserRequire();
    for (String entityID : entityIDs) {
      BaseEntity entity = context.db().getRequire(entityID);
      user.assertViewAccess(entity);
      UIInputBuilder uiInputBuilder = context.ui().inputBuilder();
      if (entity instanceof HasDynamicContextMenuActions da) {
        da.assembleActions(uiInputBuilder);
      }
      EntityDynamicData data = new EntityDynamicData(uiInputBuilder.buildAll());

      // TODO: this may be moved to single call action???
      Map<String, ItemsContextMenuAction> actions = context.ui().getItemsContextMenuActions()
        .get(entityID);
      if (actions != null) {
        for (ItemsContextMenuAction action : actions.values()) {
          data.getActions().addAll(action.getActions());
        }
      }

      if (entity instanceof HasDynamicUIFields df) {
        UIFieldBuilderImpl builder = new UIFieldBuilderImpl();
        df.assembleUIFields(builder);
        data.setDynamicFields(builder.getFields().values().stream().map(FieldBuilderImpl::build).toList());
      }
      contextActions.put(entity.getEntityID(), data);
    }
    return contextActions;
  }

  @GetMapping("/service/{esName}")
  public List<EntityService> getItemsByEntityServiceType(@PathVariable("esName") String esName) {
    return context.getEntityServices(EntityService.class)
      .stream().filter(e -> {
        for (Class<?> anInterface : ClassUtils.getAllInterfaces(((EntityService<?>) e).getEntityServiceItemClass())) {
          if (anInterface.getSimpleName().equals(esName)) {
            return true;
          }
        }
        for (Class<?> anInterface : ClassUtils.getAllSuperclasses(((EntityService<?>) e).getEntityServiceItemClass())) {
          if (anInterface.getSimpleName().equals(esName)) {
            return true;
          }
        }
        return false;
      })
      .collect(Collectors.toList());
  }

  @GetMapping("/type/{type}")
  public List<BaseEntity> getItemsByType(@PathVariable("type") String type) {
    putTypeToEntityIfNotExists(type);
    List<BaseEntity> list = new ArrayList<>();
    for (Class<?> aClass : typeToEntityClassNames.get(type)) {
      if (BaseEntity.class.isAssignableFrom(aClass)) {
        list.addAll(context.db().findAll((Class<BaseEntity>) aClass));
      }
    }
    list.removeIf(this::isRemoveItemFromResult);

    return list;
  }

    /*@PostMapping("/{entityID}/image")
    public DeviceBaseEntity updateItemImage(@PathVariable("entityID") String entityID, @RequestBody ImageEntity imageEntity) {
        return updateItem(entityID, true, baseEntity -> baseEntity.setImageEntity(imageEntity));
    }*/

  @SneakyThrows
  @PutMapping("/{entityID}/mappedBy/{mappedBy}")
  public BaseEntity putToItem(@PathVariable("entityID") String entityID, @PathVariable("mappedBy") String mappedBy, @RequestBody String json) {
    // to avoid problem with lost values in case of parallel call of putToItem rest API
    // of course we may use hashtable for locks but this method not fires often at all
    putItemsLock.lock();
    try {
      JSONObject jsonObject = new JSONObject(json);
      BaseEntity owner = context.db().getRequire(entityID);

      for (String type : jsonObject.keySet()) {
        JSONObject entityFields = jsonObject.getJSONObject(type);
        if (type.contains(":GENERATED:")) {
          type = type.substring(0, type.indexOf(":"));
        }
        Class<? extends BaseEntity> className = (Class<? extends BaseEntity>) entityManager.getUIFieldClassByType(type);
        BaseEntity newEntity = objectMapper.readValue(entityFields.toString(), className);
        FieldUtils.writeField(newEntity, mappedBy, owner, true);
        context.db().save(newEntity);
      }

      return context.db().get(owner);
    } finally {
      putItemsLock.unlock();
    }
  }

  @GetMapping("/{entityID}")
  public BaseEntity getItem(@PathVariable("entityID") String entityID) {
    BaseEntity entity = entityManager.getEntityWithFetchLazy(entityID);
    context.user().getLoggedInUserRequire().assertViewAccess(entity);

    return entity;
  }

    /*@PostMapping("/{entityID}/uploadImageBase64")
    public ImageEntity uploadImageBase64(@PathVariable("entityID") String entityID, @RequestBody BufferedImage bufferedImage) {
        try {
            return imageService.upload(entityID, bufferedImage);
        } catch (Exception e) {

            log.error(e.getMessage(), e);
            throw new ServerException(e);
        }
    }*/

  @SneakyThrows
  @DeleteMapping("/{entityID}/series/{entityToRemove}")
  public BaseEntity removeFromItem(@PathVariable("entityID") String entityID, @PathVariable("entityToRemove") String entityToRemove) {
    context.db().delete(entityToRemove);
    return context.db().get(entityID);
  }

  @GetMapping("/{entityID}/{fieldName}/{selectedEntityID}/dynamicParameterOptions")
  public Collection<OptionModel> getDynamicParameterOptions(
    @PathVariable("entityID") String entityID,
    @PathVariable("fieldName") String fieldName,
    @PathVariable("selectedEntityID") String selectedEntityID) {
    Object classEntity = context.db().get(entityID);
    if (classEntity == null) {
      throw NotFoundException.entityNotFound(entityID);
    }
    Object selectedClassEntity = context.db().get(selectedEntityID);
    if (selectedClassEntity == null) {
      throw NotFoundException.entityNotFound(selectedEntityID);
    }

    if (!(selectedClassEntity instanceof SelectionWithDynamicParameterFields)) {
      throw new IllegalStateException("SelectedEntity must implement interface <" + SelectionWithDynamicParameterFields.class.getSimpleName() + ">");
    }
    val parameter = new RequestDynamicParameter(classEntity, UIFieldUtils.buildDynamicParameterMetadata(classEntity, null));
    val dynamicParameterFields = ((SelectionWithDynamicParameterFields) selectedClassEntity).getDynamicParameterFields(parameter);
    if (dynamicParameterFields == null) {
      throw new IllegalStateException("SelectedEntity getDynamicParameterFields returned null");
    }
    return OptionUtil.loadOptions(dynamicParameterFields, context, fieldName, selectedClassEntity, null, null);
  }

  // get all device that able to get status
  @GetMapping("/deviceWithStatus")
  public List<OptionModel> getItemOptionsByType() {
    List<BaseEntity> entities = new ArrayList<>(context.db().findAll(DeviceBaseEntity.class));
    entities.removeIf(e -> !(e instanceof HasStatusAndMsg) || isRemoveItemFromResult(e));
    Map<String, List<BaseEntity>> groups =
      entities.stream().collect(Collectors.groupingBy(obj -> {

        Class<?> superClass = (Class<?>) MergedAnnotations
          .from(obj.getClass(), SearchStrategy.SUPERCLASS)
          .get(UISidebarMenu.class, MergedAnnotation::isDirectlyPresent)
          .getSource();
        if (superClass != null && !DeviceBaseEntity.class.getSimpleName().equals(superClass.getSimpleName())) {
          return superClass.getSimpleName();
        }
        return obj.getClass().getSimpleName();
      }));

    List<OptionModel> models = new ArrayList<>();
    for (Entry<String, List<BaseEntity>> entry : groups.entrySet()) {
      OptionModel parent = OptionModel.of(entry.getKey(), "DEVICE_TYPE." + entry.getKey());
      models.add(parent);
      BiConsumer<BaseEntity, OptionModel> configurator = null;
      if (!entry.getKey().equals(ZigBeeDeviceBaseEntity.class.getSimpleName())) {
        configurator = (entity, optionModel) -> optionModel
          .setTitle(format("${SELECTION.%s}: %s", entity.getClass().getSimpleName(), entity.getTitle()));
      }
      parent.setChildren(OptionModel.entityList(entry.getValue(), configurator, context));
    }

    Collections.sort(models);
    return models;
  }

  private ActionResponseModel executeActionInternal(ActionModelRequest request, Object actionHolder, BaseEntity actionEntity) throws Exception {
    String actionID = request.params == null ? request.getEntityID() : request.params.optString("action", request.getEntityID());
    for (Method method : MethodUtils.getMethodsWithAnnotation(actionHolder.getClass(), UIContextMenuAction.class)) {
      UIContextMenuAction menuAction = method.getDeclaredAnnotation(UIContextMenuAction.class);
      if (menuAction.value().equals(actionID)) {
        return executeMethodAction(method, actionHolder, context, actionEntity, request.params);
      }
    }
    for (Method method : MethodUtils.getMethodsWithAnnotation(actionHolder.getClass(), UIContextMenuUploadAction.class)) {
      UIContextMenuUploadAction menuAction = method.getDeclaredAnnotation(UIContextMenuUploadAction.class);
      if (menuAction.value().equals(actionID)) {
        return executeMethodAction(method, actionHolder, context, actionEntity, request.params);
      }
    }
    // in case when action attached to field or method
    if (request.params != null && request.params.has("field")) {
      String fieldName = request.params.getString("field");

      AccessibleObject field = Optional.ofNullable((AccessibleObject) FieldUtils.getField(actionHolder.getClass(), fieldName, true))
        .orElse(CommonUtils.findMethodByName(actionHolder.getClass(), fieldName));
      if (field != null) {
        for (UIActionButton actionButton : field.getDeclaredAnnotationsByType(UIActionButton.class)) {
          if (actionButton.name().equals(request.params.get("action"))) {
            return CommonUtils.newInstance(actionButton.actionHandler()).handleAction(context, request.params);
          }
        }
        // case when <a> or <button> direct from en.json or from text. call custom handler
        return actionEntity.handleTextFieldAction(fieldName, request.params);
      }
    }

    // Case for WorkspaceVariable, if user click on single variable
    ActionResponseModel result;
    String variableEntityID = request.params.optString("inlineEntity");
    if (variableEntityID != null) {
      result = tryHandleInlineEntity(variableEntityID, actionHolder, actionID, actionEntity, request);
      if (result != null) {
        return result;
      }
    }

    if (actionHolder instanceof HasDynamicContextMenuActions da) {
      return da.handleAction(context, actionID, request.params);
    }

    Object entityID = request.getParams().optString("entityID", "");
    Map<String, ItemsContextMenuAction> actions = context.ui().getItemsContextMenuActions().get(entityID);
    for (ItemsContextMenuAction action : actions.values()) {
      UIActionHandler actionHandler = action.getUiInputBuilder().findActionHandler(actionID);
      if (actionHandler != null) {
        if (!actionHandler.isEnabled(context)) {
          throw new IllegalArgumentException("Unable to invoke disabled action");
        }
        return actionHandler.handleAction(context, request.params);
      }
    }

    throw new IllegalArgumentException("Unable to find action: <" + actionID + "> for model: " + actionHolder);
  }

  private ActionResponseModel tryHandleInlineEntity(String variableEntityID, Object actionHolder, String actionID, BaseEntity actionEntity, ActionModelRequest request) {
    Method[] methods = MethodUtils.getMethodsWithAnnotation(actionHolder.getClass(), UIFieldInlineEntities.class);
    if (methods.length == 0) {
      return null;
    }
    ParameterizedType grt = (ParameterizedType) methods[0].getGenericReturnType();
    Class realType = (Class) grt.getActualTypeArguments()[0];
    if (!UIFieldInlineEntities.InlineEntity.class.isAssignableFrom(realType)) {
      return null;
    }
    for (Method method : MethodUtils.getMethodsWithAnnotation(realType, UIContextMenuAction.class)) {
      UIContextMenuAction menuAction = method.getDeclaredAnnotation(UIContextMenuAction.class);
      if (menuAction.value().equals(actionID)) {
        var targetObject = findTargetObjectFromFieldInlineEntities(variableEntityID, actionHolder, methods);
        if (targetObject != null) {
          ActionResponseModel model = executeMethodAction(method, targetObject, context, actionEntity, request.params);
          return model == null ? ActionResponseModel.none() : model;
        }
      }
    }
    return null;
  }

  private List<ItemContextResponse> buildItemBootstrap(String type, String subType) {
    List<ItemContextResponse> itemContexts = new ArrayList<>();

    for (Class<?> classType : findAllClassImplementationsByType(type)) {
      List<EntityUIMetaData> entityUIMetaData = UIFieldUtils.fillEntityUIMetadataList(classType, new HashSet<>(), context);
      if (isNotEmpty(subType)) {
        Object subClassObject = ContextImpl.getFetchType(subType);
        List<EntityUIMetaData> subTypeFieldMetadata = UIFieldUtils.fillEntityUIMetadataList(subClassObject, new HashSet<>(), context, false,
          null);
        // add 'cutFromJson' because custom fields must be fetched from json parameter (uses first available json                    // parameter)
        for (EntityUIMetaData data : subTypeFieldMetadata) {
          String json = new JSONObject(Objects.toString(data.getTypeMetaData(), "{}"))
            .put("cutFromJson", true).toString();
          data.setTypeMetaData(json);
        }
        entityUIMetaData.addAll(subTypeFieldMetadata);
      }
      if (!context.setting().getValue(SystemShowEntityCreateTimeSetting.class)) {
        entityUIMetaData.removeIf(field -> field.getEntityName().equals("creationTime"));
      }
      if (!context.setting().getValue(SystemShowEntityUpdateTimeSetting.class)) {
        entityUIMetaData.removeIf(field -> field.getEntityName().equals("updateTime"));
      }
      // fetch type actions
      Collection<UIInputEntity> actions = UIFieldUtils.fetchUIActionsFromClass(classType, context);

      boolean hasSourceLogs = HasEntitySourceLog.class.isAssignableFrom(classType);
      boolean hasLogs = HasEntityLog.class.isAssignableFrom(classType);
      itemContexts.add(new ItemContextResponse(classType.getSimpleName(), hasLogs, hasSourceLogs, entityUIMetaData, actions));
    }

    return itemContexts;
  }

  @SneakyThrows
  private boolean isRemoveItemFromResult(BaseEntity baseEntity) {
    UIHideEntityIfFieldNotNull hideCondition = baseEntity.getClass().getDeclaredAnnotation(UIHideEntityIfFieldNotNull.class);
    if (hideCondition != null && FieldUtils.readDeclaredField(baseEntity, hideCondition.value(), true) != null) {
      return true;
    } else if (baseEntity instanceof DeviceFallbackEntity || baseEntity instanceof WidgetFallbackEntity) {
      return true;
    }
    return baseEntity.isDisableView();
  }

  private List<OptionModel> getEntityOptions(String fieldName, Object classEntity, Class<?> entityClass) {
    Field field = FieldUtils.getField(entityClass, fieldName, true);
    Class<?> returnType = field == null ? null : field.getType();
    if (returnType == null) {
      Method method = CommonUtils.findMethodByName(entityClass, fieldName);
      if (method == null) {
        return null;
      }
      returnType = method.getReturnType();
    }
    if (returnType.getDeclaredAnnotation(Entity.class) != null) {
      Class<BaseEntity> clazz = (Class<BaseEntity>) returnType;
      List<? extends BaseEntity> selectedOptions = context.db().findAll(clazz);
      List<OptionModel> options = selectedOptions.stream()
        .map(t -> OptionModel.of(t.getEntityID(), t.getTitle()))
        .collect(Collectors.toList());

      // make filtering/add messages/etc...
      Method filterOptionMethod = findFilterOptionMethod(fieldName, classEntity);
      if (filterOptionMethod != null) {
        try {
          filterOptionMethod.setAccessible(true);
          filterOptionMethod.invoke(classEntity, selectedOptions, options);
        } catch (Exception ignore) {
        }
      }
      return options;
    } else if (returnType.isEnum()) {
      return OptionModel.enumList((Class<? extends Enum>) returnType);
    }
    return null;
  }

  private Set<OptionModel> fetchCreateItemTypes(Class<?> entityClassByType) {
    return classFinder.getClassesWithParent(entityClassByType).stream()
      .map(this::getUISideBarMenuOption)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
  }

  private @Nullable OptionModel getUISideBarMenuOption(Class<?> aClass) {
    OptionModel optionModel = OptionModel.key(aClass.getSimpleName());
    UISidebarChildren uiSidebarChildren = aClass.getAnnotation(UISidebarChildren.class);
    if (uiSidebarChildren != null) {
      if (!uiSidebarChildren.allowCreateItem()) {
        return null;
      }
      optionModel.json(json ->
        json.put("icon", uiSidebarChildren.icon())
          .put("color", uiSidebarChildren.color())
          .put("maxExceeded", uiSidebarChildren.maxAllowCreateItem() > 0 &&
                              uiSidebarChildren.maxAllowCreateItem() < getItems(aClass.getSimpleName()).size()));
    }
    return optionModel;
  }

  // set synchronized to avoid calculate multiple times
  private synchronized void putTypeToEntityIfNotExists(String type) {
    if (!typeToEntityClassNames.containsKey(type)) {
      typeToEntityClassNames.put(type, new ArrayList<>());
      Class<?> baseEntityByName = baseEntitySimpleClasses.get(type);
      if (baseEntityByName != null) {
        putTypeToEntityByClass(type, baseEntityByName);
      }
    }
  }

  private void putTypeToEntityByClass(String type, Class<?> baseEntityByName) {
    if (Modifier.isAbstract(baseEntityByName.getModifiers())) {
      var list = classFinder.getClassesWithParent(baseEntityByName);
      typeToEntityClassNames.get(type).addAll(list);
    } else {
      typeToEntityClassNames.get(type).add(baseEntityByName);
    }

    // populate if entity require extra packages to install
    Set<Class> baseClasses = new HashSet<>();
    for (Class<?> entityClass : typeToEntityClassNames.get(type)) {
      baseClasses.addAll(ClassFinder.findAllParentClasses(entityClass, baseEntityByName));
    }
  }

  private Collection<BaseEntity> getUsages(
    String entityID, AbstractRepository<BaseEntity> repository) {
    Object baseEntity = repository.getByEntityIDWithFetchLazy(entityID, false);
    Map<String, BaseEntity> usages = new HashMap<>();
    fillEntityRelationships(baseEntity, usages);
    return usages.values();
  }

  private void fillEntityRelationships(Object baseEntity, Map<String, BaseEntity> usages) {
    if (baseEntity != null) {
      FieldUtils.getAllFieldsList(baseEntity.getClass()).forEach(field -> {
        try {
          Class<? extends Annotation> aClass =
            field.isAnnotationPresent(OneToOne.class) ? OneToOne.class : (field.isAnnotationPresent(OneToMany.class) ? OneToMany.class : null);
          if (aClass != null) {
            fillEntityRelationships(baseEntity, usages, field);
          }
        } catch (Exception e) {
          throw new ServerException(e);
        }
      });
    }
  }

  private void fillEntityRelationships(Object baseEntity, Map<String, BaseEntity> usages, Field field) throws IllegalAccessException {
    Object targetValue = FieldUtils.readField(field, baseEntity, true);
    if (targetValue instanceof Collection) {
      if (!((Collection<?>) targetValue).isEmpty()) {
        for (Object o : (Collection<?>) targetValue) {
          o.toString(); // hibernate initialize
          if (o instanceof BaseEntity entity) {
            usages.put(entity.getEntityID(), entity);
            fillEntityRelationships(entity, usages);
          }
        }
      }
    } else if (targetValue != null) {
      BaseEntity entity = (BaseEntity) targetValue;
      usages.put(entity.getEntityID(), entity);
      fillEntityRelationships(entity, usages);
    }
  }

  private Method findFilterOptionMethod(String fieldName, Object entity) {
    for (Method declaredMethod : entity.getClass().getDeclaredMethods()) {
      if (declaredMethod.isAnnotationPresent(UIFilterOptions.class) && declaredMethod.getAnnotation(UIFilterOptions.class).value().equals(fieldName)) {
        return declaredMethod;
      }
    }

    // if Class has only one selection and only one filtered method - use it
    long count = FieldUtils.getFieldsListWithAnnotation(entity.getClass(), UIField.class).stream().map(p -> p.getAnnotation(UIField.class).type())
      .filter(f -> f == UIFieldType.SelectBox).count();
    if (count == 1) {
      List<Method> methodsListWithAnnotation = Stream
        .of(entity.getClass().getDeclaredMethods())
        .filter(m -> m.isAnnotationPresent(UIFilterOptions.class))
        .toList();

      if (methodsListWithAnnotation.size() == 1) {
        return methodsListWithAnnotation.get(0);
      }
    }
    return null;
  }

  private List<Class<?>> findAllClassImplementationsByType(String type) {
    Class<?> classByType = entityManager.getUIFieldClassByType(type);
    List<Class<?>> classTypes = new ArrayList<>();
    if (classByType == null) {
      putTypeToEntityIfNotExists(type);
      if (!typeToEntityClassNames.containsKey(type)) {
        return classTypes;
      }
      classTypes.addAll(typeToEntityClassNames.getOrDefault(type, Collections.emptyList()));
    } else {
      classTypes.add(classByType);
    }
    if (classTypes.isEmpty()) {
      for (Class<? extends DynamicParameterFields> dynamicClassType : classFinder.getClassesWithParent(DynamicParameterFields.class)) {
        if (dynamicClassType.getSimpleName().equals(type)) {
          classTypes.add(dynamicClassType);
        }
      }
    }
    return classTypes;
  }

  @Getter
  @Setter
  public static class GetOptionsRequest {

    private String fieldFetchType;
    private String selectType;
    private Map<String, String> deps;
  }

  private record ItemContextResponse(
    String type,
    boolean hasLogs,
    boolean hasSourceLogs,
    List<EntityUIMetaData> fields,
    Collection<UIInputEntity> actions) {

  }

  @Getter
  @Setter
  public static class ActionModelRequest {

    private String entityID;
    private JSONObject params;
  }

  @Getter
  @Setter
  @RequiredArgsConstructor
  public static class EntityDynamicData {

    private @NotNull
    final Collection<UIInputEntity> actions;
    private @Nullable List<EntityUIMetaData> dynamicFields;
  }
}
