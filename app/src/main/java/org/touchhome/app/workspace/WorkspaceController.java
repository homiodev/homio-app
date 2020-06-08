package org.touchhome.app.workspace;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.touchhome.app.manager.BundleManager;
import org.touchhome.app.repository.device.WorkspaceRepository;
import org.touchhome.app.rest.BundleController;
import org.touchhome.app.workspace.block.Scratch3Space;
import org.touchhome.bundle.api.BundleContext;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.exception.NotFoundException;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.api.link.HasWorkspaceVariableLinkAbility;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.model.workspace.WorkspaceShareVariableEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;
import org.touchhome.bundle.api.scratch.Scratch3Block;
import org.touchhome.bundle.api.scratch.Scratch3Extension;
import org.touchhome.bundle.api.scratch.Scratch3ExtensionBlocks;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.bundle.api.workspace.WorkspaceEntity;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Log4j2
@RestController
@RequiredArgsConstructor
@RequestMapping("/rest/workspace")
public class WorkspaceController {

    private final List<Scratch3ExtensionBlocks> scratch3ExtensionBlocks;
    private final BundleController bundleController;
    private final EntityContext entityContext;
    private final BundleManager bundleManager;
    private final WorkspaceManager workspaceManager;
    private final List<Scratch3ExtensionImpl> extensions = new ArrayList<>();

    @PostConstruct
    public void init() {
        for (Scratch3ExtensionBlocks scratch3ExtensionBlock : scratch3ExtensionBlocks) {
            if (scratch3ExtensionBlock.getClass().isAnnotationPresent(Scratch3Extension.class)) {
                Scratch3Extension scratch3Extension = scratch3ExtensionBlock.getClass().getDeclaredAnnotation(Scratch3Extension.class);
                if (!Scratch3Extension.ID_PATTERN.matcher(scratch3Extension.value()).matches()) {
                    throw new IllegalArgumentException("Wrong Scratch3Extension: <" + scratch3Extension.value() + ">. Must contains [a-z] or '-'");
                }

                BundleContext bundleContext = bundleController.getBundle(scratch3Extension.value());
                if (bundleContext == null && scratch3Extension.value().contains("-")) {
                    bundleContext = bundleController.getBundle(scratch3Extension.value().substring(0, scratch3Extension.value().indexOf("-")));
                }
                if (bundleContext == null) {
                    throw new IllegalStateException("Unable to find bundle context with id: " + scratch3Extension.value());
                }

                insertScratch3Spaces(scratch3ExtensionBlock);
                extensions.add(new Scratch3ExtensionImpl(scratch3Extension.value(), scratch3ExtensionBlock, bundleContext.order()));
            }
        }
        Collections.sort(extensions);
    }

    @GetMapping("extension")
    public List<Scratch3ExtensionImpl> getExtensions() {
        return extensions;
    }

    @GetMapping("extension/{bundleID}.png")
    public ResponseEntity<InputStreamResource> getExtensionImage(@PathVariable("bundleID") String bundleID) {
        BundleContext bundleContext = bundleManager.getBundle(bundleID);
        InputStream stream = bundleContext.getClass().getClassLoader().getResourceAsStream("extensions/" + bundleContext.getBundleId() + ".png");
        if (stream == null) {
            stream = bundleContext.getClass().getClassLoader().getResourceAsStream(bundleContext.getBundleImage());
        }
        if (stream == null) {
            throw new NotFoundException("Unable to find workspace extension bundle image for bundle: " + bundleID);
        }
        return TouchHomeUtils.inputStreamToResource(stream, MediaType.IMAGE_PNG);
    }

    @GetMapping("{entityID}")
    public String getWorkspace(@PathVariable("entityID") String entityID) {
        WorkspaceEntity workspaceEntity = entityContext.getEntity(entityID);
        if (workspaceEntity == null) {
            throw new NotFoundException("Unable to find workspace tab with id: " + entityID);
        }
        return workspaceEntity.getContent();
    }

    @GetMapping("variable")
    public String getWorkspaceVariables() {
        WorkspaceShareVariableEntity entity = entityContext.getEntity(WorkspaceShareVariableEntity.PREFIX + WorkspaceShareVariableEntity.NAME);
        if (entity == null) {
            throw new NotFoundException("Unable to find workspace variables");
        }
        return entity.getContent();
    }

    @GetMapping("variable/{type}")
    public List<Option> getWorkspaceVariables(@PathVariable("type") String type) {
        return Option.list(entityContext.findAllByPrefix(type));
    }

    @SneakyThrows
    @PostMapping("{entityID}")
    public void saveWorkspace(@PathVariable("entityID") String entityID, @RequestBody String json) {
        WorkspaceEntity workspaceEntity = entityContext.getEntity(entityID);
        entityContext.save(workspaceEntity.setContent(json));
    }

    @SneakyThrows
    @PostMapping("variable")
    public void saveVariables(@RequestBody String json) {
        WorkspaceShareVariableEntity entity = entityContext.getEntity(WorkspaceShareVariableEntity.PREFIX + WorkspaceShareVariableEntity.NAME);
        entityContext.save(entity.setContent(json));
    }

    @SneakyThrows
    @PostMapping("variable/{entityID}")
    public void saveVariables(@PathVariable("entityID") String entityID, @RequestBody CreateVariable createVariable) {
        Optional<AbstractRepository> optionalRepository = entityContext.getRepository(entityID);
        if (optionalRepository.isPresent()) {
            AbstractRepository repository = optionalRepository.get();
            if (repository instanceof HasWorkspaceVariableLinkAbility) {
                ((HasWorkspaceVariableLinkAbility) repository).createVariable(entityID, createVariable.varGroup, createVariable.varName, createVariable.key);
            } else {
                throw new IllegalStateException("Entity: '" + entityID + "' repository has no workspace variable link ability");
            }
        } else {
            throw new IllegalStateException("Unable to find repository for entity: " + entityID);
        }
    }

    @GetMapping("tab")
    public List<Option> getWorkspaceTabs() {
        return Option.list(entityContext.findAll(WorkspaceEntity.class));
    }

    @SneakyThrows
    @PostMapping("tab/{name}")
    public Option createWorkspaceTab(@PathVariable("name") String name) {
        BaseEntity workspaceEntity = entityContext.getEntity(WorkspaceEntity.PREFIX + name);
        if (workspaceEntity == null) {
            WorkspaceEntity entity = entityContext.save(new WorkspaceEntity().setName(name).computeEntityID(() -> name));
            return Option.of(entity.getEntityID(), entity.getTitle());
        }
        throw new IllegalArgumentException("Workspace tab with name <" + name + "> already exists");
    }

    @SneakyThrows
    @PutMapping("tab/{entityID}")
    public void renameWorkspaceTab(@PathVariable("entityID") String entityID, @RequestBody Option option) {
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

    @DeleteMapping("tab/{entityID}")
    public void deleteWorkspaceTab(@PathVariable("entityID") String entityID) {
        WorkspaceEntity entity = entityContext.getEntity(entityID);
        if (entity == null) {
            throw new NotFoundException("Unable to find workspace tab with id: " + entityID);
        }
        if (WorkspaceRepository.GENERAL_WORKSPACE_TAB_NAME.equals(entity.getName())) {
            throw new IllegalArgumentException("Unable to delete main workspace");
        }
        if (!workspaceManager.isEmpty(entity.getContent())) {
            throw new IllegalArgumentException("Unable to delete non empty workspace");
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

    @Setter
    public static class CreateVariable {
        String key;
        String varGroup;
        String varName;
    }
}
