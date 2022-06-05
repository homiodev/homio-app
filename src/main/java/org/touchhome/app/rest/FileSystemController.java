package org.touchhome.app.rest;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.bundle.api.entity.DeviceBaseEntity;
import org.touchhome.bundle.api.entity.storage.BaseFileSystemEntity;
import org.touchhome.bundle.api.entity.storage.VendorFileSystem;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.common.model.FileSystemItem;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.touchhome.bundle.api.workspace.scratch.Scratch3ExtensionBlocks.ENTITY;

@RestController
@RequestMapping("/rest/fs")
@RequiredArgsConstructor
public class FileSystemController {

    private final EntityContextImpl entityContext;
    private List<BaseFileSystemEntity> fileSystems;

    public void postConstruct() {
        this.fileSystems = entityContext.getEntityServices(BaseFileSystemEntity.class);

        this.entityContext.event().addEntityRemovedListener(BaseFileSystemEntity.class, "fs-remove", (e) ->
                this.fileSystems = entityContext.getEntityServices(BaseFileSystemEntity.class));
        this.entityContext.event().addEntityCreateListener(BaseFileSystemEntity.class, "fs-create", (e) ->
                this.fileSystems = entityContext.getEntityServices(BaseFileSystemEntity.class));
    }

    @GetMapping("")
    public List<FileSystemConfiguration> getFileSystems() {
        return fileSystems.stream().map(FileSystemConfiguration::new).collect(Collectors.toList());
    }

    @GetMapping("/file")
    public Collection<OptionModel> getFiles(@RequestParam(name = ENTITY, required = false) String fsEntityId) {
        if (fsEntityId != null) {
            BaseFileSystemEntity entity = entityContext.getEntity(fsEntityId);
            if (entity != null) {
                Collection<FileSystemItem> allFiles = entity.getFileSystem(entityContext).getAllFiles(true);
                return entity.getFileSystem(entityContext).getAllFiles(true);
            }
        }
        return Collections.emptyList();
    }

    @GetMapping("/folder")
    public Collection<OptionModel> getFolders(@RequestParam(name = ENTITY, required = false) String fsEntityId) {
        if (fsEntityId != null) {
            BaseFileSystemEntity entity = entityContext.getEntity(fsEntityId);
            if (entity != null) {
                return entity.getFileSystem(entityContext).getAllFolders(true);
            }
        }
        return Collections.emptyList();
    }

    @GetMapping("/{fs}/list")
    public Collection<FileSystemItem> list(@PathVariable("fs") String fs,
                                           @RequestParam(value = "fileId", defaultValue = "") String fileId) {
        VendorFileSystem fileSystem = getFileSystem(fs);
        return fileSystem.getChild(fileId.split("~~~"));
    }

    @SneakyThrows
    @DeleteMapping
    public void remove(@RequestBody RemoveFilesRequest request) {
        VendorFileSystem fileSystem = getFileSystem(request.sourceFs);
        List<String[]> sourcePathList =
                request.getSourceFileIds().stream().map(id -> id.split("~~~")).collect(Collectors.toList());
        fileSystem.delete(sourcePathList);
    }

    @GetMapping("/{fs}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable("fs") String fs,
                                                        @RequestParam(value = "fileId") String fileId) throws Exception {
        VendorFileSystem fileSystem = getFileSystem(fs);
        VendorFileSystem.DownloadData data = fileSystem.download(fileId.split("~~~"), false, null);

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(data.getContentType());
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return TouchHomeUtils.inputStreamToResource(data.getInputStream(), mediaType);
    }

    @PostMapping("/archiveEntries")
    public FileSystemItem openArchive(@RequestBody OpenArchiveNodeRequest request) throws Exception {
        return getFileSystem(request.sourceFs).getArchiveEntries(
                request.sourceFileId.split("~~~"), request.password);
    }

    @PostMapping("/create")
    public FileSystemItem createNode(@RequestBody CreateNodeRequest request) throws Exception {
        VendorFileSystem fileSystem = getFileSystem(request.sourceFs);
        if (request.dir) {
            return fileSystem.createFolder(request.fileId.split("~~~"), request.name);
        } else {
            return fileSystem.upload(request.fileId.split("~~~"), request.name, new byte[0], false, false);
        }
    }

    @PostMapping("/unarchive")
    public FileSystemItem unarchiveNode(@RequestBody UnArchiveNodeRequest request) throws Exception {
        if (request.sourceFs.equals(request.targetFs)) {
            String[] targetPath = (request.targetDir).split("~~~");
            return getFileSystem(request.sourceFs).unArchive(request.sourceFileId.split("~~~"), targetPath,
                    request.password, request.removeSource, request.fileHandler);
        }
        throw new IllegalStateException("Not implemented exception");
    }

    @PostMapping("/archive")
    public FileSystemItem archiveNode(@RequestBody ArchiveNodeRequest request) throws Exception {
        List<String[]> sourcePathList =
                request.getSourceFileIds().stream().map(id -> id.split("~~~")).collect(Collectors.toList());
        if (request.sourceFs.equals(request.targetFs)) {
            String[] targetPath = (request.targetDir + "~~~" + request.targetName).split("~~~");
            return getFileSystem(request.sourceFs).archive(sourcePathList, targetPath, request.format, request.level,
                    request.password, request.removeSource);
        }
        throw new IllegalStateException("Not implemented exception");
    }

    @PostMapping("/copy")
    public FileSystemItem copyNode(@RequestBody CopyNodeRequest request) throws Exception {
        List<String[]> sourcePathList =
                request.getSourceFileIds().stream().map(id -> id.split("~~~")).collect(Collectors.toList());
        String[] targetPath = request.targetPath.split("~~~");
        if (request.sourceFs.equals(request.targetFs)) {
            return getFileSystem(request.sourceFs).copy(sourcePathList, targetPath, request.removeSource,
                    request.replaceExisting);
        }
        VendorFileSystem sourceFileSystem = getFileSystem(request.targetFs);
        VendorFileSystem targetFileSystem = getFileSystem(request.targetFs);

        List<FileSystemItem> items = new ArrayList<>(sourcePathList.size());
        for (String[] sourcePath : sourcePathList) {
            VendorFileSystem.DownloadData downloadData = sourceFileSystem.download(sourcePath, false, null);
            items.add(targetFileSystem.upload(targetPath, sourcePath[sourcePath.length - 1], downloadData.getContent(),
                    false, request.replaceExisting));
            downloadData.close();
        }
        if (request.removeSource) {
            sourceFileSystem.delete(sourcePathList);
        }
        return null;
    }

    @PostMapping("/{fs}/upload")
    public FileSystemItem upload(@PathVariable("fs") String fs,
                                 @RequestParam("fileId") String fileId,
                                 @RequestParam("replace") boolean replace,
                                 @RequestParam("data") MultipartFile[] files) throws Exception {
        return getFileSystem(fs).upload(fileId.split("~~~"), files, replace);
    }

    @PostMapping("/{fs}/rename")
    public FileSystemItem rename(@PathVariable("fs") String fs,
                                 @RequestBody RenameNodeRequest request) throws Exception {
        VendorFileSystem fileSystem = getFileSystem(fs);
        return fileSystem.rename(request.fileId.split("~~~"), request.newName);
    }

    @GetMapping("/{fs}/search")
    public List<FileSystemItem> search(@RequestParam("query") String query) throws IOException {
        //  if (StringUtils.isEmpty(query)) {
        return null;
        // }
        /*List<FileSystemItem> result = new ArrayList<>();
        Files.walkFileTree(Paths.get(defaultPath), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().contains(query)) {
                    result.add(new FileSystemItem(file.toFile()));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                if (exc instanceof AccessDeniedException) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return super.visitFileFailed(file, exc);
            }
        });*/
        // return result;
    }

    private VendorFileSystem getFileSystem(String fs) {
        return fileSystems.stream().filter(e -> e.getEntityID().equals(fs)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("Unable to find File system: " + fs))
                .getFileSystem(entityContext);
    }

    @Getter
    @Setter
    private static class RenameNodeRequest {
        public String fileId;
        public String newName;
    }

    @Getter
    @Setter
    private static class OpenArchiveNodeRequest extends BaseNodeRequest {
        public String password;
        private String sourceFileId;
    }

    @Getter
    @Setter
    private static class CreateNodeRequest extends BaseNodeRequest {
        public String fileId;
        public String name;
        public boolean dir;
    }

    @Getter
    @Setter
    private static class UnArchiveNodeRequest extends BaseNodeRequest {
        public String targetDir;
        public String password;
        public String fileHandler;
        private String sourceFileId;
        public boolean removeSource;
    }

    @Getter
    @Setter
    private static class ArchiveNodeRequest extends BaseNodeRequest {
        public List<String> sourceFileIds;
        public String format;
        public String level;
        public String password;
        public String targetDir;
        public String targetName;
        public boolean removeSource;
    }

    @Getter
    @Setter
    private static class CopyNodeRequest extends BaseNodeRequest {
        public List<String> sourceFileIds;
        public String targetPath;
        public boolean removeSource;
        public boolean replaceExisting;
        public boolean ignoreExisting;
    }

    @Getter
    @Setter
    private static class RemoveFilesRequest extends BaseNodeRequest {
        public List<String> sourceFileIds;
    }

    @Getter
    @Setter
    private static class BaseNodeRequest {
        public String sourceFs;
        public String targetFs;
    }

    @Getter
    private class FileSystemConfiguration {
        public final String id;
        public final String name;
        public final String icon;
        public final String color;
        public final boolean hasDelete;
        public final boolean hasRename;
        public final boolean hasUpload;
        public final boolean hasCreateFile;
        public final boolean hasCreateFolder;
        public final List<String> editableExtensions;
        public final List<OptionModel> zipExtensions;

        public FileSystemConfiguration(BaseFileSystemEntity fs) {
            this.id = fs.getEntityID();
            DeviceBaseEntity entity = (DeviceBaseEntity) fs;
            this.name = StringUtils.defaultIfEmpty(entity.getName(), entity.getShortTitle());
            this.icon = fs.getIcon();
            this.color = fs.getIconColor();
            this.hasDelete = true;
            this.hasRename = true;
            this.hasUpload = true;
            this.hasCreateFile = true;
            this.hasCreateFolder = true;

            VendorFileSystem fileSystem = fs.getFileSystem(entityContext);
            List<VendorFileSystem.ArchiveFormat> supportArchiveFormat = fileSystem.getSupportArchiveFormat();
            this.zipExtensions = supportArchiveFormat.stream().map(f ->
                            OptionModel.of(f.getId(), f.getName()).json(json -> json.put("extensions", f.getExtensions())))
                    .collect(Collectors.toList());
            this.editableExtensions = Arrays.asList("txt", "java", "cpp", "sh", "css", "scss", "js", "json", "xml", "html", "php",
                    "py", "ts", "ino", "conf", "service", "md", "png", "jpg", "jpeg");
        }
    }
}
