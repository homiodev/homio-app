package org.touchhome.app.rest;

import static org.touchhome.bundle.api.util.Constants.ADMIN_ROLE;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;
import javax.persistence.Entity;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import net.rossillo.spring.web.mvc.CacheControl;
import net.rossillo.spring.web.mvc.CachePolicy;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
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
import org.touchhome.app.LogService;
import org.touchhome.app.manager.ImageService;
import org.touchhome.app.manager.common.ClassFinder;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.manager.common.EntityManager;
import org.touchhome.app.model.UIHideEntityIfFieldNotNull;
import org.touchhome.app.model.rest.EntityUIMetaData;
import org.touchhome.app.setting.system.SystemClearCacheButtonSetting;
import org.touchhome.app.setting.system.SystemShowEntityCreateTimeSetting;
import org.touchhome.app.setting.system.SystemShowEntityUpdateTimeSetting;
import org.touchhome.app.spring.ContextCreated;
import org.touchhome.app.spring.ContextRefreshed;
import org.touchhome.app.utils.InternalUtil;
import org.touchhome.app.utils.UIFieldSelectionUtil;
import org.touchhome.app.utils.UIFieldUtils;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.ImageEntity;
import org.touchhome.bundle.api.model.ActionResponseModel;
import org.touchhome.bundle.api.model.HasEntityLog;
import org.touchhome.bundle.api.model.HasPosition;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.repository.AbstractRepository;
import org.touchhome.bundle.api.service.EntityService;
import org.touchhome.bundle.api.ui.UISidebarChildren;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.ui.field.UIFilterOptions;
import org.touchhome.bundle.api.ui.field.action.HasDynamicContextMenuActions;
import org.touchhome.bundle.api.ui.field.action.UIActionButton;
import org.touchhome.bundle.api.ui.field.action.UIContextMenuAction;
import org.touchhome.bundle.api.ui.field.action.UIContextMenuUploadAction;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputEntity;
import org.touchhome.bundle.api.ui.field.selection.dynamic.DynamicParameterFields;
import org.touchhome.bundle.api.ui.field.selection.dynamic.SelectionWithDynamicParameterFields;
import org.touchhome.common.exception.NotFoundException;
import org.touchhome.common.exception.ServerException;
import org.touchhome.common.util.CommonUtils;

@SuppressWarnings({"rawtypes", "unchecked"})
@Log4j2
@RestController
@RequestMapping("/rest/item")
@RequiredArgsConstructor
public class ItemController implements ContextCreated, ContextRefreshed {

    private static final Map<String, List<Class<? extends BaseEntity>>> typeToEntityClassNames =
        new ConcurrentHashMap<>();
    private final Map<String, List<ItemContext>> itemsBootstrapContextMap =
        new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;
    private final EntityContextImpl entityContext;
    private final EntityManager entityManager;
    private final ClassFinder classFinder;
    private final ImageService imageService;
    private final LogService logService;

    private final ReentrantLock putItemsLock = new ReentrantLock();
    private final ReentrantLock updateItemLock = new ReentrantLock();

    private Map<String, Class<? extends BaseEntity>> baseEntitySimpleClasses;

    @SneakyThrows
    static ActionResponseModel executeMethodAction(Method method, Object actionHolder, EntityContext entityContext,
        BaseEntity actionEntity, JSONObject params) {
        List<Object> objects = new ArrayList<>();
        for (AnnotatedType parameterType : method.getAnnotatedParameterTypes()) {
            if (actionHolder.getClass().isAssignableFrom((Class) parameterType.getType())) {
                objects.add(actionHolder);
            } else if (BaseEntity.class.isAssignableFrom((Class) parameterType.getType())) {
                objects.add(actionEntity);
            } else if (JSONObject.class.isAssignableFrom((Class<?>) parameterType.getType())) {
                objects.add(params);
            } else {
                objects.add(entityContext.getBean((Class) parameterType.getType()));
            }
        }
        method.setAccessible(true);
        return (ActionResponseModel) method.invoke(actionHolder, objects.toArray());
    }

    @PostConstruct
    public void postConstruct() {
        entityContext
            .setting()
            .listenValue(
                SystemClearCacheButtonSetting.class,
                "ic-clear-cache",
                () -> {
                    typeToEntityClassNames.clear();
                });
    }

    @SneakyThrows
    public ActionResponseModel executeAction(
        ActionRequestModel actionRequestModel, Object actionHolder, BaseEntity actionEntity) {
        for (Method method :
            MethodUtils.getMethodsWithAnnotation(
                actionHolder.getClass(), UIContextMenuAction.class)) {
            UIContextMenuAction menuAction =
                method.getDeclaredAnnotation(UIContextMenuAction.class);
            if (menuAction.value().equals(actionRequestModel.getName())) {
                return executeMethodAction(
                    method,
                    actionHolder,
                    entityContext,
                    actionEntity,
                    actionRequestModel.getParams());
            }
        }
        for (Method method :
            MethodUtils.getMethodsWithAnnotation(
                actionHolder.getClass(), UIContextMenuUploadAction.class)) {
            UIContextMenuUploadAction menuAction =
                method.getDeclaredAnnotation(UIContextMenuUploadAction.class);
            if (menuAction.value().equals(actionRequestModel.getName())) {
                return executeMethodAction(
                    method,
                    actionHolder,
                    entityContext,
                    actionEntity,
                    actionRequestModel.getParams());
            }
        }
        // in case when action attached to field or method
        if (actionRequestModel.metadata != null && actionRequestModel.metadata.has("field")) {
            String fieldName = actionRequestModel.metadata.getString("field");

            AccessibleObject field =
                Optional.ofNullable(
                            (AccessibleObject)
                                FieldUtils.getField(
                                    actionHolder.getClass(), fieldName, true))
                        .orElse(
                            InternalUtil.findMethodByName(
                                actionHolder.getClass(), fieldName));
            if (field != null) {
                for (UIActionButton actionButton :
                    field.getDeclaredAnnotationsByType(UIActionButton.class)) {
                    if (actionButton.name().equals(actionRequestModel.name)) {
                        CommonUtils.newInstance(actionButton.actionHandler())
                                   .handleAction(entityContext, actionRequestModel.params);
                    }
                }
            }
        }
        if (actionHolder instanceof HasDynamicContextMenuActions) {
            return ((HasDynamicContextMenuActions) actionHolder)
                .handleAction(
                    entityContext, actionRequestModel.name, actionRequestModel.params);
        }
        throw new IllegalArgumentException("Unable to find action: <" + actionRequestModel.getName() + "> for model: " + actionHolder);
    }

    @Override
    public void onContextCreated(EntityContextImpl entityContext) {
        this.entityContext.setting().listenValue(SystemClearCacheButtonSetting.class, "boot-clear", itemsBootstrapContextMap::clear);
        // invalidate UIField cache scenarios
        this.entityContext.setting().listenValue(SystemShowEntityCreateTimeSetting.class,
            "invalidate-uifield-createTime-cache", this.itemsBootstrapContextMap::clear);
        this.entityContext.setting().listenValue(SystemShowEntityUpdateTimeSetting.class,
            "invalidate-uifield-updateTime-cache", this.itemsBootstrapContextMap::clear);
    }

    @Override
    public void onContextRefresh() {
        List<Class<? extends BaseEntity>> baseEntityClasses =
            classFinder.getClassesWithParent(BaseEntity.class);
        this.baseEntitySimpleClasses =
            baseEntityClasses.stream().collect(Collectors.toMap(Class::getSimpleName, s -> s));

        for (Class<? extends BaseEntity> baseEntityClass : baseEntityClasses) {
            Class<?> cursor = baseEntityClass.getSuperclass();
            while (!cursor.getSimpleName().equals(BaseEntity.class.getSimpleName())) {
                this.baseEntitySimpleClasses.put(
                    cursor.getSimpleName(), (Class<? extends BaseEntity>) cursor);
                cursor = cursor.getSuperclass();
            }
        }
    }

    @GetMapping("/{type}/context")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public List<ItemContext> getItemsBootstrapContext(@PathVariable("type") String type, @RequestParam(value = "subType", defaultValue = "") String subType) {
        String key = type + subType;
        // itemsBootstrapContextMap.clear(); // TODO: remove this
        itemsBootstrapContextMap.computeIfAbsent(key, s -> {
            List<ItemContext> itemContexts = new ArrayList<>();

            for (Class<?> classType : findAllClassImplementationsByType(type)) {
                List<EntityUIMetaData> entityUIMetaData = UIFieldUtils.fillEntityUIMetadataList(classType, new HashSet<>(), entityContext);
                if (subType != null && subType.contains(":")) {
                    String[] bundleAndClassName = subType.split(":");
                    Object subClassObject = entityContext.getBeanOfBundleBySimpleName(bundleAndClassName[0], bundleAndClassName[1]);
                    List<EntityUIMetaData> subTypeFieldMetadata = UIFieldUtils.fillEntityUIMetadataList(subClassObject, new HashSet<>(), entityContext, false);
                    // add 'cutFromJson' because custom fields must be fetched from json parameter (uses first available json                    // parameter)
                    for (EntityUIMetaData data : subTypeFieldMetadata) {
                        data.setTypeMetaData(new JSONObject(StringUtils.defaultString(data.getTypeMetaData(), "{}")).put("cutFromJson", true).toString());
                    }
                    entityUIMetaData.addAll(subTypeFieldMetadata);
                }
                if (!entityContext.setting().getValue(SystemShowEntityCreateTimeSetting.class)) {
                    entityUIMetaData.removeIf(field -> field.getEntityName().equals("creationTime"));
                }
                if (!entityContext.setting().getValue(SystemShowEntityUpdateTimeSetting.class)) {
                    entityUIMetaData.removeIf(field -> field.getEntityName().equals("updateTime"));
                }
                // fetch type actions
                Collection<UIInputEntity> actions = UIFieldUtils.fetchUIActionsFromClass(classType, entityContext);

                itemContexts.add(new ItemContext(classType.getSimpleName(), HasEntityLog.class.isAssignableFrom(classType), entityUIMetaData, actions));
            }

            return itemContexts;
        });
        return itemsBootstrapContextMap.get(key);
    }

    @GetMapping("/{type}/types")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public Set<OptionModel> getImplementationsByBaseType(@PathVariable("type") String type) {
        // type nay be base class also
        Class<?> entityClassByType = entityManager.getClassByType(type);
        if (entityClassByType == null) {
            putTypeToEntityIfNotExists(type);
            if (!typeToEntityClassNames.containsKey(type)) {
                return Collections.emptySet();
            }
        }
        Set<OptionModel> list = entityClassByType == null ? new HashSet<>() : fetchCreateItemTypes(entityClassByType);
        for (Class<? extends BaseEntity> aClass : typeToEntityClassNames.get(type)) {
            list.add(getUISideBarMenuOption(aClass));
        }
        return list;
    }

    @PostMapping(value = "/{entityID}/logs/debug/{value}")
    public void setEntityDebugLogLevel(
        @PathVariable("entityID") String entityID, @PathVariable("value") boolean debug) {
        BaseEntity entity = entityContext.getEntity(entityID);
        if (entity instanceof HasEntityLog) {
            HasEntityLog hasEntityLog = (HasEntityLog) entity;
            if (hasEntityLog.isDebug() != debug) {
                hasEntityLog.setDebug(debug);
                entityContext.save(entity);
            }
        }
    }

    @GetMapping(value = "/{entityID}/logs")
    public ResponseEntity<StreamingResponseBody> getLogs(@PathVariable("entityID") String entityID) {
        Path logPath = logService.getEntityLogsFile(entityContext.getEntity(entityID));
        if (logPath == null) {
            throw new IllegalArgumentException("Unable to find log file path for entity: " + entityID);
        }

        StreamingResponseBody stream = out -> Files.copy(logPath, out);
        return new ResponseEntity(stream, HttpStatus.OK);
    }

    @GetMapping("/type/{type}/options")
    public List<OptionModel> getItemOptionsByType(@PathVariable("type") String type) {
        putTypeToEntityIfNotExists(type);
        List<OptionModel> list = new ArrayList<>();
        for (Class<? extends BaseEntity> aClass : typeToEntityClassNames.get(type)) {
            list.addAll(OptionModel.entityList(entityContext.findAll(aClass)));
        }
        Collections.sort(list);

        return list;
    }

    /*TODO: @PostMapping(value = "/{type}/installDep/{dependency}")
    public void installDep(@PathVariable("type") String type, @PathVariable("dependency") String dependency) {
      if (typeToRequireDependencies.containsKey(type)) {
        List<DependencyExecutableInstaller> installers = typeToRequireDependencies.get(type).installers;
        DependencyExecutableInstaller installer =
            installers.stream().filter(c -> c.getName().equals(dependency)).findAny().orElse(null);
        if (installer != null && installer.isRequireInstallDependencies(entityContext, false)) {
          entityContext.bgp().runWithProgress("install-deps-" + dependency, false, progressBar -> {
            installer.installDependency(entityContext, progressBar);
            reloadItems(Collections.singletonList(type));
          }, null, () -> new RuntimeException("INSTALL_DEPENDENCY_IN_PROGRESS"));
        }
      }
    }*/

    @PostMapping(value = "/{entityID}/action")
    public ActionResponseModel callAction(@PathVariable("entityID") String entityID,
        @RequestBody ActionRequestModel requestModel) {
        return callActionWithBinary(entityID, requestModel, null);
    }

    @PostMapping(value = "/{entityID}/actionWithBinary")
    public ActionResponseModel callActionWithBinary(@PathVariable("entityID") String entityID, @RequestPart ActionRequestModel actionRequestModel,
        @RequestParam("data") MultipartFile[] files) {
        try {
            BaseEntity<?> entity = entityContext.getEntity(entityID);
            actionRequestModel.params.put("files", files);
            return executeAction(actionRequestModel, entity, entity);
        } catch (Exception ex) {
            log.error("Error while execute action: {}", CommonUtils.getErrorMessage(ex));
            return ActionResponseModel.showError(ex);
        }
    }

    @GetMapping("/{type}/actions")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public Collection<UIInputEntity> getItemsActions(@PathVariable("type") String type) {
        Class<?> entityClassByType = entityManager.getClassByType(type);
        return UIFieldUtils.fetchUIActionsFromClass(entityClassByType, entityContext);
    }

    @PostMapping("/{type}")
    public BaseEntity<?> create(@PathVariable("type") String type) {
        log.debug("Request creating entity by type: <{}>", type);
        Class<? extends BaseEntity> typeClass = EntityContextImpl.baseEntityNameToClass.get(type);
        if (typeClass == null) {
            throw new IllegalArgumentException("Unable to find base entity with type: " + type);
        }
        BaseEntity<?> baseEntity = CommonUtils.newInstance(typeClass);
        return entityContext.save(baseEntity);
    }

    @PostMapping("/{entityID}/copy")
    public BaseEntity<?> copyEntityByID(@PathVariable("entityID") String entityID) {
        return entityContext.copyEntity(entityContext.getEntity(entityID));
    }

    @DeleteMapping("/{entityID}")
    @Secured(ADMIN_ROLE)
    public void removeEntity(@PathVariable("entityID") String entityID) {
        entityContext.delete(entityID);
    }

    @GetMapping("/{entityID}/dependencies")
    public List<String> canRemove(@PathVariable("entityID") String entityID) {
        BaseEntity entity = entityContext.getEntity(entityID);
        if (entity == null) {
            entity = entityContext.getEntity(entityID, false);
            if (entity == null) {
                entityContext.getCacheService().clearCache();
                entityContext.ui().reloadWindow("Clear cache");
                return Collections.emptyList();
            }
        }
        Optional<AbstractRepository> repositoryOpt = entityContext.getRepository(entity);
        AbstractRepository repository = repositoryOpt.orElseThrow();
        Collection<BaseEntity> usages = getUsages(entityID, repository);
        return usages.stream().map(Object::toString).collect(Collectors.toList());
    }

    /**
     * synchronized because if we add few series to item/widget/etc... and pushing changes to server in parallel we may face with loose changes when merging
     * changes from new income data and saved in db
     */
    @PutMapping
    @SneakyThrows
    public BaseEntity<?> updateItems(@RequestBody String json) {
        updateItemLock.lock();
        try {
            JSONObject jsonObject = new JSONObject(json);
            BaseEntity<?> resultField = null;
            for (String entityId : jsonObject.keySet()) {
                log.info("Put update item: <{}>", entityId);
                BaseEntity<?> entity = entityContext.getEntity(entityId);

                if (entity == null) {
                    throw new NotFoundException("Entity '" + entityId + "' not found");
                }

                JSONObject entityFields = jsonObject.getJSONObject(entityId);
                entity = objectMapper.readerForUpdating(entity).readValue(entityFields.toString());

                // reference fields isn't updatable, we need update them manually
                for (String fieldName : entityFields.keySet()) {
                    Field field = FieldUtils.getField(entity.getClass(), fieldName, true);
                    if (field != null && BaseEntity.class.isAssignableFrom(field.getType())) {
                        BaseEntity<?> refEntity = entityContext.getEntity(entityFields.getString(fieldName));
                        FieldUtils.writeField(field, entity, refEntity);
                    }
                }

                // update entity
                BaseEntity<?> savedEntity = entityContext.save(entity);
                if (resultField == null) {
                    resultField = savedEntity;
                }
            }
            return resultField;
        } finally {
            updateItemLock.unlock();
        }
    }

    @GetMapping("/typeContext/{type}")
    public ItemsByTypeResponse getItemsTypeContextByType(@PathVariable("type") String type) {
        putTypeToEntityIfNotExists(type);
        List<BaseEntity> items = new ArrayList<>();

        for (Class<? extends BaseEntity> aClass : typeToEntityClassNames.get(type)) {
            items.addAll(entityContext.findAll(aClass));
        }
        List<Collection<UIInputEntity>> contextActions = new ArrayList<>();
        items.removeIf(this::isRemoveItemFromResult);
        for (BaseEntity item : items) {
            UIInputBuilder uiInputBuilder = entityContext.ui().inputBuilder();
            if (item instanceof HasDynamicContextMenuActions) {
                ((HasDynamicContextMenuActions) item).assembleActions(uiInputBuilder);
            }
            contextActions.add(uiInputBuilder.buildAll());
        }
        return new ItemsByTypeResponse(items, contextActions);
    }

    @SneakyThrows
    private boolean isRemoveItemFromResult(BaseEntity baseEntity) {
        UIHideEntityIfFieldNotNull hideCondition = baseEntity.getClass().getDeclaredAnnotation(UIHideEntityIfFieldNotNull.class);
        if (hideCondition != null && FieldUtils.readDeclaredField(baseEntity, hideCondition.value(), true) != null) {
            return true;
        }
        return false;
    }

    @GetMapping("/service/{esName}")
    public List<EntityService> getItemsByEntityServiceType(@PathVariable("esName") String esName) {
        return entityContext.getEntityServices(EntityService.class)
                            .stream().filter(e -> {
                for (Class<?> anInterface : ClassUtils.getAllInterfaces(((EntityService<?, ?>) e).getEntityServiceItemClass())) {
                    if (anInterface.getSimpleName().equals(esName)) {
                        return true;
                    }
                }
                for (Class<?> anInterface : ClassUtils.getAllSuperclasses(((EntityService<?, ?>) e).getEntityServiceItemClass())) {
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
        for (Class<? extends BaseEntity> aClass : typeToEntityClassNames.get(type)) {
            list.addAll(entityContext.findAll(aClass));
        }

        return list;
    }

    @GetMapping("/{entityID}")
    public BaseEntity<?> getItem(@PathVariable("entityID") String entityID) {
        return entityManager.getEntityWithFetchLazy(entityID);
    }

    /*@PostMapping("/{entityID}/image")
    public DeviceBaseEntity updateItemImage(@PathVariable("entityID") String entityID, @RequestBody ImageEntity imageEntity) {
        return updateItem(entityID, true, baseEntity -> baseEntity.setImageEntity(imageEntity));
    }*/

    @SneakyThrows
    @PutMapping("/{entityID}/mappedBy/{mappedBy}")
    public BaseEntity<?> putToItem(@PathVariable("entityID") String entityID, @PathVariable("mappedBy") String mappedBy, @RequestBody String json) {
        // to avoid problem with lost values in case of parallel call of putToItem rest API
        // of course we may use hashtable for locks but this method not fires often at all
        putItemsLock.lock();
        try {
            JSONObject jsonObject = new JSONObject(json);
            BaseEntity<?> owner = entityContext.getEntity(entityID);

            for (String type : jsonObject.keySet()) {
                Class<? extends BaseEntity> className = entityManager.getClassByType(type);
                JSONObject entityFields = jsonObject.getJSONObject(type);
                BaseEntity<?> newEntity = objectMapper.readValue(entityFields.toString(), className);
                FieldUtils.writeField(newEntity, mappedBy, owner, true);
                entityContext.save(newEntity);
            }

            return entityContext.getEntity(owner);
        } finally {
            putItemsLock.unlock();
        }
    }

    @SneakyThrows
    @Secured(ADMIN_ROLE)
    @DeleteMapping("/{entityID}/field/{field}/item/{entityToRemove}")
    public BaseEntity<?> removeFromItem(@PathVariable("entityID") String entityID, @PathVariable("field") String field,
        @PathVariable("entityToRemove") String entityToRemove) {
        BaseEntity<?> entity = entityContext.getEntity(entityID);
        entityContext.delete(entityToRemove);
        return entityContext.getEntity(entity);
    }

    @PostMapping("/{entityID}/block")
    public void updateBlockPosition(
        @PathVariable("entityID") String entityID, @RequestBody UpdateBlockPosition position) {
        BaseEntity<?> entity = entityContext.getEntity(entityID);
        if (entity != null) {
            if (entity instanceof HasPosition) {
                HasPosition<?> hasPosition = (HasPosition<?>) entity;
                hasPosition.setXb(position.xb);
                hasPosition.setYb(position.yb);
                hasPosition.setBw(position.bw);
                hasPosition.setBh(position.bh);
                hasPosition.setXbl(position.xbl);
                hasPosition.setYbl(position.ybl);
                entityContext.save(entity);
            } else {
                throw new IllegalArgumentException("Entity: " + entityID + " has no ability to update position");
            }
        }
    }

    @PostMapping("/{entityID}/uploadImageBase64")
    @Secured(ADMIN_ROLE)
    public ImageEntity uploadImageBase64(@PathVariable("entityID") String entityID, @RequestBody BufferedImage bufferedImage) {
        try {
            return imageService.upload(entityID, bufferedImage);
        } catch (Exception e) {

            log.error(e.getMessage(), e);
            throw new ServerException(e);
        }
    }

    @PostMapping("/{entityID}/{fieldName}/options")
    public Collection<OptionModel> loadSelectOptions(
        @PathVariable("entityID") String entityID,
        @PathVariable("fieldName") String fieldName,
        @RequestBody GetOptionsRequest optionsRequest) {
        Object classEntity = entityContext.getEntity(entityID);
        if (classEntity == null) {
            // i.e in case we load Widget
            Class<?> aClass = entityManager.getClassByType(entityID);
            if (aClass == null) {
                List<Class<?>> classImplementationsByType = findAllClassImplementationsByType(entityID);
                aClass = classImplementationsByType.isEmpty() ? null : classImplementationsByType.get(0);
            }
            if (aClass == null) {
                throw new IllegalArgumentException("Unable to find class for entity: " + entityID);
            }
            classEntity = CommonUtils.newInstance(aClass);
            if (classEntity == null) {
                throw new IllegalArgumentException("Unable find class: " + entityID);
            }
        }
        Class<?> entityClass = classEntity.getClass();
        if (StringUtils.isNotEmpty(optionsRequest.getFieldFetchType())) {
            String[] bundleAndClassName = optionsRequest.getFieldFetchType().split(":");
            entityClass = entityContext.getBeanOfBundleBySimpleName(bundleAndClassName[0], bundleAndClassName[1]).getClass();
        }

        List<OptionModel> options = getEntityOptions(fieldName, classEntity, entityClass);
        if (options != null) {
            return options;
        }

        return UIFieldSelectionUtil.loadOptions(classEntity, entityContext, fieldName, null, optionsRequest.getSelectType(), optionsRequest.getDeps(),
            optionsRequest.getParam0());
    }

    @GetMapping("/{entityID}/{fieldName}/{selectedEntityID}/dynamicParameterOptions")
    public Collection<OptionModel> getDynamicParameterOptions(
        @PathVariable("entityID") String entityID,
        @PathVariable("fieldName") String fieldName,
        @PathVariable("selectedEntityID") String selectedEntityID) {
        Object classEntity = entityContext.getEntity(entityID);
        if (classEntity == null) {
            throw NotFoundException.entityNotFound(entityID);
        }
        Object selectedClassEntity = entityContext.getEntity(selectedEntityID);
        if (selectedClassEntity == null) {
            throw NotFoundException.entityNotFound(selectedEntityID);
        }

        if (!(selectedClassEntity instanceof SelectionWithDynamicParameterFields)) {
            throw new IllegalStateException("SelectedEntity must implement interface <" + SelectionWithDynamicParameterFields.class.getSimpleName() + ">");
        }
        SelectionWithDynamicParameterFields.RequestDynamicParameter parameter = new SelectionWithDynamicParameterFields.RequestDynamicParameter(
            classEntity, UIFieldUtils.fetchRequestWidgetType(classEntity, null));
        DynamicParameterFields dynamicParameterFields = ((SelectionWithDynamicParameterFields) selectedClassEntity).getDynamicParameterFields(parameter);
        if (dynamicParameterFields == null) {
            throw new IllegalStateException("SelectedEntity getDynamicParameterFields returned null");
        }
        return UIFieldSelectionUtil.loadOptions(dynamicParameterFields, entityContext, fieldName, selectedClassEntity, null, null, null);
    }

    private List<OptionModel> getEntityOptions(String fieldName, Object classEntity, Class<?> entityClass) {
        Field field = FieldUtils.getField(entityClass, fieldName, true);
        Class<?> returnType = field == null ? null : field.getType();
        if (returnType == null) {
            Method method = InternalUtil.findMethodByName(entityClass, fieldName);
            if (method == null) {
                return null;
            }
            returnType = method.getReturnType();
        }
        if (returnType.getDeclaredAnnotation(Entity.class) != null) {
            Class<BaseEntity<?>> clazz = (Class<BaseEntity<?>>) returnType;
            List<? extends BaseEntity> selectedOptions = entityContext.findAll(clazz);
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
                          .collect(Collectors.toSet());
    }

    private OptionModel getUISideBarMenuOption(Class<?> aClass) {
        OptionModel optionModel = OptionModel.key(aClass.getSimpleName());
        UISidebarChildren uiSidebarChildren = aClass.getAnnotation(UISidebarChildren.class);
        if (uiSidebarChildren != null) {
            optionModel.json(json ->
                json.put("icon", uiSidebarChildren.icon())
                    .put("color", uiSidebarChildren.color()));
        }
        return optionModel;
    }

    // set synchronized to avoid calculate multiple times
    private synchronized void putTypeToEntityIfNotExists(String type) {
        if (!typeToEntityClassNames.containsKey(type)) {
            typeToEntityClassNames.put(type, new ArrayList<>());
            Class<? extends BaseEntity> baseEntityByName = baseEntitySimpleClasses.get(type);
            if (baseEntityByName != null) {
                putTypeToEntityByClass(type, baseEntityByName);
            }
        }
    }

    private void putTypeToEntityByClass(String type, Class<? extends BaseEntity> baseEntityByName) {
        if (Modifier.isAbstract(baseEntityByName.getModifiers())) {
            var list = classFinder.getClassesWithParent(baseEntityByName)
                                  .stream().filter((Predicate<Class>) child -> {
                    if (child.isAnnotationPresent(UISidebarChildren.class)) {
                        UISidebarChildren uiSidebarChildren = (UISidebarChildren) child.getDeclaredAnnotation(UISidebarChildren.class);
                        return uiSidebarChildren.allowCreateItem();
                    }
                    return true;
                }).collect(Collectors.toList());
            typeToEntityClassNames.get(type).addAll(list);
        } else {
            typeToEntityClassNames.get(type).add(baseEntityByName);
        }

        // populate if entity require extra packages to install
        Set<Class> baseClasses = new HashSet<>();
        for (Class<? extends BaseEntity> entityClass : typeToEntityClassNames.get(type)) {
            baseClasses.addAll(ClassFinder.findAllParentClasses(entityClass, baseEntityByName));
        }
    }

    private Collection<BaseEntity> getUsages(
        String entityID, AbstractRepository<BaseEntity<?>> repository) {
        Object baseEntity = repository.getByEntityIDWithFetchLazy(entityID, false);
        Map<String, BaseEntity> usages = new HashMap<>();
        fillEntityRelationships(baseEntity, usages);
        return usages.values();
    }

    private void fillEntityRelationships(Object baseEntity, Map<String, BaseEntity> usages) {
        if (baseEntity != null) {
            FieldUtils.getAllFieldsList(baseEntity.getClass())
                      .forEach(
                          field -> {
                              try {
                                  Class<? extends Annotation> aClass =
                                      field.isAnnotationPresent(OneToOne.class)
                                          ? OneToOne.class
                                          : (field.isAnnotationPresent(OneToMany.class)
                                              ? OneToMany.class
                                              : null);
                                  if (aClass != null) {
                                      Object targetValue =
                                          FieldUtils.readField(field, baseEntity, true);
                                      if (targetValue instanceof Collection) {
                                          if (!((Collection<?>) targetValue).isEmpty()) {
                                              for (Object o : (Collection<?>) targetValue) {
                                                  o.toString(); // hibernate initialize
                                                  BaseEntity entity = (BaseEntity) o;
                                                  usages.put(entity.getEntityID(), entity);
                                                  fillEntityRelationships(entity, usages);
                                              }
                                          }
                                      } else if (targetValue != null) {
                                          BaseEntity entity = (BaseEntity) targetValue;
                                          usages.put(entity.getEntityID(), entity);
                                          fillEntityRelationships(entity, usages);
                                      }
                                  }
                              } catch (Exception e) {
                                  throw new ServerException(e);
                              }
                          });
        }
    }

    private Method findFilterOptionMethod(String fieldName, Object entity) {
        for (Method declaredMethod : entity.getClass().getDeclaredMethods()) {
            if (declaredMethod.isAnnotationPresent(UIFilterOptions.class)
                && declaredMethod
                .getAnnotation(UIFilterOptions.class)
                .value()
                .equals(fieldName)) {
                return declaredMethod;
            }
        }

        // if Class has only one selection and only one filtered method - use it
        long count =
            FieldUtils.getFieldsListWithAnnotation(entity.getClass(), UIField.class).stream()
                      .map(p -> p.getAnnotation(UIField.class).type())
                      .filter(f -> f == UIFieldType.SelectBox)
                      .count();
        if (count == 1) {
            List<Method> methodsListWithAnnotation =
                Stream.of(entity.getClass().getDeclaredMethods())
                      .filter(m -> m.isAnnotationPresent(UIFilterOptions.class))
                      .collect(Collectors.toList());

            if (methodsListWithAnnotation.size() == 1) {
                return methodsListWithAnnotation.get(0);
            }
        }
        return null;
    }

    private List<Class<?>> findAllClassImplementationsByType(String type) {
        Class<?> classByType = entityManager.getClassByType(type);
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
            for (Class<? extends DynamicParameterFields> dynamicClassType :
                classFinder.getClassesWithParent(DynamicParameterFields.class)) {
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
        private String[] selectType;
        private String param0; // for lazy loading
        private Map<String, String> deps;
    }

    @Getter
    @RequiredArgsConstructor
    public static class ItemsByTypeResponse {

        private final List<BaseEntity> items;
        private final List<Collection<UIInputEntity>> contextActions;
    }

    @Data
    private static class UpdateBlockPosition {

        private int xb;
        private int yb;
        private int bw;
        private int bh;
        private Integer xbl;
        private Integer ybl;
    }

    @Getter
    @Setter
    public static class ActionRequestModel {

        private String entityID;
        private String name;
        private JSONObject metadata;
        private JSONObject params;
    }

    @Getter
    @AllArgsConstructor
    private static class ItemContext {

        private final String type;
        private final boolean hasLogs;
        private final List<EntityUIMetaData> fields;
        private final Collection<UIInputEntity> actions;
    }

    @Getter
    @Setter
    private static class SingleInlineItemRequest {

        private String entityID;
        private String entityFieldName;
        private String innerFieldName;
        private String inlineEntityID;
        private Object innerValue;
    }
}
