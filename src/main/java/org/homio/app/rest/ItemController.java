package org.homio.app.rest;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.homio.api.util.Constants.ADMIN_ROLE_AUTHORIZE;
import static org.homio.app.model.entity.user.UserBaseEntity.LOG_RESOURCE_AUTHORIZE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.homio.api.EntityContext;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.EntityFieldMetadata;
import org.homio.api.entity.log.HasEntityLog;
import org.homio.api.entity.log.HasEntitySourceLog;
import org.homio.api.exception.NotFoundException;
import org.homio.api.exception.ServerException;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.ActionResponseModel.ResponseAction;
import org.homio.api.model.FileModel;
import org.homio.api.model.OptionModel;
import org.homio.api.service.EntityService;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.ui.field.UIFilterOptions;
import org.homio.api.ui.field.action.HasDynamicContextMenuActions;
import org.homio.api.ui.field.action.UIActionButton;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.action.UIContextMenuUploadAction;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.action.v1.UIInputEntity;
import org.homio.api.ui.field.selection.dynamic.DynamicParameterFields;
import org.homio.api.ui.field.selection.dynamic.SelectionWithDynamicParameterFields;
import org.homio.api.ui.field.selection.dynamic.SelectionWithDynamicParameterFields.RequestDynamicParameter;
import org.homio.api.util.CommonUtils;
import org.homio.app.LogService;
import org.homio.app.config.cacheControl.CacheControl;
import org.homio.app.config.cacheControl.CachePolicy;
import org.homio.app.manager.common.ClassFinder;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.manager.common.EntityManager;
import org.homio.app.model.UIHideEntityIfFieldNotNull;
import org.homio.app.model.entity.widget.attributes.HasPosition;
import org.homio.app.model.rest.EntityUIMetaData;
import org.homio.app.repository.AbstractRepository;
import org.homio.app.setting.system.SystemClearCacheButtonSetting;
import org.homio.app.setting.system.SystemShowEntityCreateTimeSetting;
import org.homio.app.setting.system.SystemShowEntityUpdateTimeSetting;
import org.homio.app.spring.ContextCreated;
import org.homio.app.spring.ContextRefreshed;
import org.homio.app.utils.InternalUtil;
import org.homio.app.utils.UIFieldSelectionUtil;
import org.homio.app.utils.UIFieldUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
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

@Log4j2
@RestController
@RequestMapping("/rest/item")
@RequiredArgsConstructor
public class ItemController implements ContextCreated, ContextRefreshed {

    private static final Map<String, List<Class<? extends BaseEntity>>> typeToEntityClassNames =
        new ConcurrentHashMap<>();
    private final Map<String, List<ItemContextResponse>> itemsBootstrapContextMap =
        new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;
    private final EntityContextImpl entityContext;
    private final EntityManager entityManager;
    private final ClassFinder classFinder;
    private final LogService logService;

    private final ReentrantLock putItemsLock = new ReentrantLock();
    private final ReentrantLock updateItemLock = new ReentrantLock();

    private Map<String, Class<? extends BaseEntity>> baseEntitySimpleClasses = new HashMap<>();

    private final Cache<String, Consumer<String>> fileSaveMapping = CacheBuilder
        .newBuilder().expireAfterAccess(Duration.ofMinutes(5)).build();

    @PostConstruct
    public void postConstruct() {
        entityContext.setting().listenValue(SystemClearCacheButtonSetting.class, "ic-clear-cache",
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

    private @Nullable ActionResponseModel executeActionInternal(ActionModelRequest request, Object actionHolder, BaseEntity actionEntity) throws Exception {
        for (Method method : MethodUtils.getMethodsWithAnnotation(actionHolder.getClass(), UIContextMenuAction.class)) {
            UIContextMenuAction menuAction = method.getDeclaredAnnotation(UIContextMenuAction.class);
            if (menuAction.value().equals(request.getEntityID())) {
                return executeMethodAction(method, actionHolder, entityContext, actionEntity, request.metadata);
            }
        }
        for (Method method : MethodUtils.getMethodsWithAnnotation(actionHolder.getClass(), UIContextMenuUploadAction.class)) {
            UIContextMenuUploadAction menuAction = method.getDeclaredAnnotation(UIContextMenuUploadAction.class);
            if (menuAction.value().equals(request.getEntityID())) {
                return executeMethodAction(method, actionHolder, entityContext, actionEntity, request.metadata);
            }
        }
        // in case when action attached to field or method
        if (request.metadata != null && request.metadata.has("field")) {
            String fieldName = request.metadata.getString("field");

            AccessibleObject field = Optional.ofNullable((AccessibleObject) FieldUtils.getField(actionHolder.getClass(), fieldName, true))
                                             .orElse(InternalUtil.findMethodByName(actionHolder.getClass(), fieldName));
            if (field != null) {
                for (UIActionButton actionButton : field.getDeclaredAnnotationsByType(UIActionButton.class)) {
                    if (actionButton.name().equals(request.metadata.get("action"))) {
                        return CommonUtils.newInstance(actionButton.actionHandler()).handleAction(entityContext, request.metadata);
                    }
                }
                // case when <a> or <button> direct from en.json or from text. call custom handler
                return actionEntity.handleTextFieldAction(fieldName, request.metadata);
            }
        }
        if (actionHolder instanceof HasDynamicContextMenuActions) {
            return ((HasDynamicContextMenuActions) actionHolder).handleAction(entityContext, request.getEntityID(), request.metadata);
        }
        throw new IllegalArgumentException("Unable to find action: <" + request.getEntityID() + "> for model: " + actionHolder);
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
    public void onContextRefresh(EntityContext entityContext) {
        List<Class<? extends BaseEntity>> baseEntityClasses = classFinder.getClassesWithParent(BaseEntity.class);
        this.baseEntitySimpleClasses = baseEntityClasses.stream().collect(Collectors.toMap(Class::getSimpleName, s -> s));

        for (Class<? extends BaseEntity> baseEntityClass : baseEntityClasses) {
            Class<?> cursor = baseEntityClass.getSuperclass();
            while (!cursor.getSimpleName().equals(BaseEntity.class.getSimpleName())) {
                this.baseEntitySimpleClasses.put(cursor.getSimpleName(), (Class<? extends BaseEntity>) cursor);
                cursor = cursor.getSuperclass();
            }
        }
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

    @PostMapping("/{entityID}/{fieldName}/options")
    public Collection<OptionModel> loadSelectOptions(
        @PathVariable("entityID") String entityID,
        @PathVariable("fieldName") String fieldName,
        @RequestBody GetOptionsRequest optionsRequest) {
        Object classEntity = entityContext.getEntity(entityID);
        if (classEntity == null) {
            // i.e in case we load Widget
            Class<?> aClass = entityManager.getUIFieldClassByType(entityID);
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
        if (isNotEmpty(optionsRequest.getFieldFetchType())) {
            String[] addonAndClassName = optionsRequest.getFieldFetchType().split(":");
            entityClass = entityContext.getAddon()
                                       .getBeanOfAddonsBySimpleName(addonAndClassName[0], addonAndClassName[1]).getClass();
        }

        List<OptionModel> options = getEntityOptions(fieldName, classEntity, entityClass);
        if (options != null) {
            return options;
        }

        return UIFieldSelectionUtil.loadOptions(classEntity, entityContext, fieldName, null, optionsRequest.getSelectType(), optionsRequest.getDeps(),
            optionsRequest.getParam0());
    }

    @GetMapping("/options")
    public Collection<OptionModel> getAllOptions() {
        return UIFieldSelectionUtil.getAllOptions(entityContext);
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
        for (Class<? extends BaseEntity> aClass : typeToEntityClassNames.get(type)) {
            OptionModel option = getUISideBarMenuOption(aClass);
            if (option != null) {
                list.add(option);
            }
        }
        return list;
    }

    /*@PostMapping(value = "/{entityID}/logs/debug/{value}")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public void setEntityDebugLogLevel(@PathVariable("entityID") String entityID, @PathVariable("value") boolean debug) {
        BaseEntity entity = entityContext.getEntity(entityID);
        if (entity instanceof HasEntityLog) {
            HasEntityLog hasEntityLog = (HasEntityLog) entity;
            if (hasEntityLog.isDebug() != debug) {
                hasEntityLog.setDebug(debug);
                entityContext.save(entity);
            }
        }
    }*/

    @GetMapping(value = "/{entityID}/logs")
    @PreAuthorize(LOG_RESOURCE_AUTHORIZE)
    public ResponseEntity<StreamingResponseBody> getLogs(
        @PathVariable("entityID") String entityID,
        @RequestParam(value = "source", required = false) String sourceID) {
        BaseEntity entity = entityContext.getEntityRequire(entityID);
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

    @GetMapping(value = "/{entityID}/logs/source")
    @PreAuthorize(LOG_RESOURCE_AUTHORIZE)
    public @NotNull List<OptionModel> getSourceLogs(
        @PathVariable("entityID") String entityID) {
        BaseEntity entity = entityContext.getEntityRequire(entityID);
        if (entity instanceof HasEntitySourceLog) {
            return ((HasEntitySourceLog) entity).getLogSources();
        }
        throw new IllegalStateException("Entity: " + entityID + " not implement HasEntitySourceLog interface");
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

    @PostMapping(value = "/{entityID}/context/action")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public ActionResponseModel callAction(@PathVariable("entityID") String entityID,
        @RequestBody ActionModelRequest request) {
        return callActionWithBinary(entityID, request, null);
    }

    @PostMapping(value = "/{entityID}/context/actionWithBinary")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public ActionResponseModel callActionWithBinary(
        @PathVariable("entityID") String entityID,
        @RequestPart ActionModelRequest request,
        @RequestParam("data") MultipartFile[] files) {
        try {
            if (entityID.equals("FILE_SAVE_ACTION")) {
                callSaveFilesAction(files);
                return null;
            }

            BaseEntity entity = entityContext.getEntityRequire(entityID);
            if (request.metadata == null) {
                request.metadata = new JSONObject();
            }
            request.metadata.put("files", files);
            request.metadata.put("entityID", entityID);
            return executeAction(request, entity, entity);
        } catch (Exception ex) {
            log.error("Error while execute action: {}", CommonUtils.getErrorMessage(ex));
            return ActionResponseModel.showError(ex);
        }
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

    @PostMapping("/{entityID}/notification/action")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public ActionResponseModel notificationAction(@PathVariable("entityID") String entityID,
        @RequestBody ActionModelRequest request) throws Exception {
        return entityContext.ui().handleNotificationAction(entityID, request.entityID, request.metadata);
    }

    @GetMapping("/{type}/actions")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public Collection<UIInputEntity> getItemsActions(@PathVariable("type") String type) {
        Class<?> entityClassByType = entityManager.getUIFieldClassByType(type);
        return UIFieldUtils.fetchUIActionsFromClass(entityClassByType, entityContext);
    }

    @PostMapping("/{type}")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public BaseEntity create(@PathVariable("type") String type) {
        log.debug("Request creating entity by type: <{}>", type);
        Class<? extends EntityFieldMetadata> typeClass = EntityContextImpl.uiFieldClasses.get(type);
        if (typeClass == null) {
            throw new IllegalArgumentException("Unable to find base entity with type: " + type);
        }
        BaseEntity baseEntity = (BaseEntity) CommonUtils.newInstance(typeClass);
        return entityContext.save(baseEntity);
    }

    @PostMapping("/{entityID}/copy")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public BaseEntity copyEntityByID(@PathVariable("entityID") String entityID) {
        return entityContext.copyEntity(entityContext.getEntityRequire(entityID));
    }

    @DeleteMapping("/{entityID}")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public void removeEntity(@PathVariable("entityID") String entityID) {
        entityContext.delete(entityID);
    }

    @GetMapping("/{entityID}/dependencies")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
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
        AbstractRepository repository = EntityContextImpl.getRepository(entity.getEntityID());
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
                BaseEntity entity = entityContext.getEntity(entityId);

                if (entity == null) {
                    throw NotFoundException.entityNotFound(entityId);
                }

                JSONObject entityFields = jsonObject.getJSONObject(entityId);
                entity = objectMapper.readerForUpdating(entity).readValue(entityFields.toString());

                // reference fields isn't updatable, we need update them manually
                for (String fieldName : entityFields.keySet()) {
                    Field field = FieldUtils.getField(entity.getClass(), fieldName, true);
                    if (field != null && BaseEntity.class.isAssignableFrom(field.getType())) {
                        BaseEntity refEntity = entityContext.getEntity(entityFields.getString(fieldName));
                        FieldUtils.writeField(field, entity, refEntity);
                    }
                }

                // update entity
                BaseEntity savedEntity = entityContext.save(entity);
                if (resultField == null) {
                    resultField = savedEntity;
                }
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

        for (Class<? extends BaseEntity> aClass : typeToEntityClassNames.get(type)) {
            items.addAll(entityContext.findAll(aClass));
        }
        items.removeIf(this::isRemoveItemFromResult);
        Collections.sort(items);
        return items;
    }

    @PostMapping("/actions")
    public Map<String, Collection<UIInputEntity>> getActions(@RequestBody List<String> entityIDs) {
        Map<String, Collection<UIInputEntity>> contextActions = new HashMap<>();
        for (String entityID : entityIDs) {
            BaseEntity entity = entityContext.getEntityRequire(entityID);
            UIInputBuilder uiInputBuilder = entityContext.ui().inputBuilder();
            if (entity instanceof HasDynamicContextMenuActions) {
                ((HasDynamicContextMenuActions) entity).assembleActions(uiInputBuilder);
            }
            contextActions.put(entity.getEntityID(), uiInputBuilder.buildAll());
        }
        return contextActions;
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
    public BaseEntity getItem(@PathVariable("entityID") String entityID) {
        return entityManager.getEntityWithFetchLazy(entityID);
    }

    @SneakyThrows
    @PutMapping("/{entityID}/mappedBy/{mappedBy}")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public BaseEntity putToItem(@PathVariable("entityID") String entityID, @PathVariable("mappedBy") String mappedBy, @RequestBody String json) {
        // to avoid problem with lost values in case of parallel call of putToItem rest API
        // of course we may use hashtable for locks but this method not fires often at all
        putItemsLock.lock();
        try {
            JSONObject jsonObject = new JSONObject(json);
            BaseEntity owner = entityContext.getEntityRequire(entityID);

            for (String type : jsonObject.keySet()) {
                Class<? extends BaseEntity> className = (Class<? extends BaseEntity>) entityManager.getUIFieldClassByType(type);
                JSONObject entityFields = jsonObject.getJSONObject(type);
                BaseEntity newEntity = objectMapper.readValue(entityFields.toString(), className);
                FieldUtils.writeField(newEntity, mappedBy, owner, true);
                entityContext.save(newEntity);
            }

            return entityContext.getEntity(owner);
        } finally {
            putItemsLock.unlock();
        }
    }

    @SneakyThrows
    @DeleteMapping("/{entityID}/field/{field}/item/{entityToRemove}")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public BaseEntity removeFromItem(@PathVariable("entityID") String entityID, @PathVariable("field") String field,
        @PathVariable("entityToRemove") String entityToRemove) {
        BaseEntity entity = entityContext.getEntityRequire(entityID);
        entityContext.delete(entityToRemove);
        return entityContext.getEntity(entity);
    }

    /*@PostMapping("/{entityID}/image")
    public DeviceBaseEntity updateItemImage(@PathVariable("entityID") String entityID, @RequestBody ImageEntity imageEntity) {
        return updateItem(entityID, true, baseEntity -> baseEntity.setImageEntity(imageEntity));
    }*/

    @PostMapping("/{entityID}/block")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public void updateBlockPosition(
        @PathVariable("entityID") String entityID, @RequestBody UpdateBlockPositionRequest position) {
        BaseEntity entity = entityContext.getEntity(entityID);
        if (entity != null) {
            if (entity instanceof HasPosition<?> hasPosition) {
                hasPosition.setXb(position.xb);
                hasPosition.setYb(position.yb);
                hasPosition.setBw(position.bw);
                hasPosition.setBh(position.bh);
                hasPosition.setParent(position.parent);
                entityContext.save(entity);
            } else {
                throw new IllegalArgumentException("Entity: " + entityID + " has no ability to update position");
            }
        }
    }

    /*@PostMapping("/{entityID}/uploadImageBase64")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public ImageEntity uploadImageBase64(@PathVariable("entityID") String entityID, @RequestBody BufferedImage bufferedImage) {
        try {
            return imageService.upload(entityID, bufferedImage);
        } catch (Exception e) {

            log.error(e.getMessage(), e);
            throw new ServerException(e);
        }
    }*/

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
        val parameter = new RequestDynamicParameter(classEntity, UIFieldUtils.buildDynamicParameterMetadata(classEntity, null));
        val dynamicParameterFields = ((SelectionWithDynamicParameterFields) selectedClassEntity).getDynamicParameterFields(parameter);
        if (dynamicParameterFields == null) {
            throw new IllegalStateException("SelectedEntity getDynamicParameterFields returned null");
        }
        return UIFieldSelectionUtil.loadOptions(dynamicParameterFields, entityContext, fieldName, selectedClassEntity, null, null, null);
    }

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

    @NotNull
    private List<ItemContextResponse> buildItemBootstrap(String type, String subType) {
        List<ItemContextResponse> itemContexts = new ArrayList<>();

        for (Class<?> classType : findAllClassImplementationsByType(type)) {
            List<EntityUIMetaData> entityUIMetaData = UIFieldUtils.fillEntityUIMetadataList(classType, new HashSet<>(), entityContext);
            if (subType != null && subType.contains(":")) {
                String[] addonAndClassName = subType.split(":");
                Object subClassObject = entityContext.getAddon()
                                                     .getBeanOfAddonsBySimpleName(addonAndClassName[0], addonAndClassName[1]);
                List<EntityUIMetaData> subTypeFieldMetadata = UIFieldUtils.fillEntityUIMetadataList(subClassObject, new HashSet<>(), entityContext, false,
                    null);
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

            boolean hasSourceLogs = HasEntitySourceLog.class.isAssignableFrom(classType);
            boolean hasLogs = HasEntityLog.class.isAssignableFrom(classType);
            itemContexts.add(new ItemContextResponse(classType.getSimpleName(), hasLogs, hasSourceLogs, entityUIMetaData, actions));
        }

        return itemContexts;
    }

    @SneakyThrows
    private boolean isRemoveItemFromResult(BaseEntity baseEntity) {
        UIHideEntityIfFieldNotNull hideCondition = baseEntity.getClass().getDeclaredAnnotation(UIHideEntityIfFieldNotNull.class);
        return hideCondition != null && FieldUtils.readDeclaredField(baseEntity, hideCondition.value(), true) != null;
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
            Class<BaseEntity> clazz = (Class<BaseEntity>) returnType;
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
            var list = classFinder.getClassesWithParent(baseEntityByName);
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

    private void addFilePath(List<Path> files, Path file) {
        if (file != null && Files.exists(file)) {
            files.add(file);
        }
    }

    @Getter
    @Setter
    public static class GetOptionsRequest {

        private String fieldFetchType;
        private String selectType;
        private String param0; // for lazy loading
        private Map<String, String> deps;
    }

    @Getter
    @Setter
    private static class UpdateBlockPositionRequest {

        private int xb;
        private int yb;
        private int bw;
        private int bh;
        private String parent;
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
        private JSONObject metadata;
    }
}
