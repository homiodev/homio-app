package org.touchhome.app.rest;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.io.FileUtils;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.bundle.api.BeanPostConstruct;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.TreeConfiguration;
import org.touchhome.bundle.api.entity.storage.BaseFileSystemEntity;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.bundle.raspberry.entity.RaspberryDeviceEntity;
import org.touchhome.bundle.raspberry.fs.RaspberryFileSystem;
import org.touchhome.common.fs.FileSystemProvider;
import org.touchhome.common.fs.TreeNode;
import org.touchhome.common.util.ArchiveUtil;
import org.touchhome.common.util.CommonUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

@RestController
@RequestMapping("/rest/fs")
@RequiredArgsConstructor
public class FileSystemController implements BeanPostConstruct {

    private final EntityContextImpl entityContext;
    private List<BaseFileSystemEntity> fileSystems;
    private RaspberryFileSystem localFileSystem;

    @Override
    public void postConstruct(EntityContext context) {
        EntityContextImpl entityContext = (EntityContextImpl) context;
        this.entityContext.event().addEntityRemovedListener(BaseFileSystemEntity.class, "fs-remove",
                (e) -> this.fileSystems = entityContext.getEntityServices(BaseFileSystemEntity.class));
        this.entityContext.event().addEntityCreateListener(BaseFileSystemEntity.class, "fs-create",
                (e) -> this.fileSystems = entityContext.getEntityServices(BaseFileSystemEntity.class));

        RaspberryDeviceEntity raspberryDeviceEntity =
                this.entityContext.getEntity(RaspberryDeviceEntity.DEFAULT_DEVICE_ENTITY_ID);
        this.localFileSystem = raspberryDeviceEntity.getFileSystem(entityContext);
    }

    @Override
    public void onContextUpdate(EntityContext context) {
        EntityContextImpl entityContext = (EntityContextImpl) context;
        this.fileSystems = entityContext.getEntityServices(BaseFileSystemEntity.class);
    }

    @PostMapping("")
    public List<TreeConfiguration> getFileSystems(@RequestBody GetFSRequest request) {
        List<TreeConfiguration> configurations = new ArrayList<>();
        for (BaseFileSystemEntity fileSystem : fileSystems) {
            if (request.showOnlyLocalFS && !fileSystem.getEntityID().equals(this.localFileSystem.getEntity().getEntityID())) {
                continue;
            }
            TreeConfiguration configuration = new TreeConfiguration(fileSystem);

            for (GetFSRequest.SelectedNode selectedNode : request.selectedNodes) {
                if (selectedNode.fs.equals(fileSystem.getEntityID())) {
                    configuration.setChildren(fileSystem.getFileSystem(entityContext).loadTreeUpToChild(selectedNode.id));
                }
            }
            configurations.add(configuration);
        }
        return configurations;
    }

    @PostMapping("/list")
    public Collection<TreeNode> list(@RequestBody ListRequest request) {
        return getFileSystem(request.sourceFs).getChildren(request.sourceFileId);
    }

    @DeleteMapping
    public TreeNode remove(@RequestBody RemoveFilesRequest request) {
        return getFileSystem(request.sourceFs).delete(request.sourceFileIds);
    }

    @PostMapping("/download")
    public ResponseEntity<InputStreamResource> download(@RequestBody DownloadRequest request) throws Exception {
        FileSystemProvider fileSystem = getFileSystem(request.sourceFs);
        if (request.sourceFileIds.size() == 1) {
            TreeNode treeNode = fileSystem.toTreeNode(request.sourceFileIds.iterator().next());
            InputStream inputStream = fileSystem.getEntryInputStream(treeNode.getId());

            MediaType mediaType;
            try {
                mediaType = MediaType.parseMediaType(treeNode.getAttributes().getContentType());
            } catch (Exception ex) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
            return TouchHomeUtils.inputStreamToResource(inputStream, mediaType);
        } else {
            Path zipFile = archiveSource(request.sourceFs, "zip", request.sourceFileIds, "downloadContent", null, null);
            return TouchHomeUtils.inputStreamToResource(Files.newInputStream(zipFile), new MediaType("application", "zip"));
        }
    }

    @PostMapping("/create")
    public TreeNode createNode(@RequestBody CreateNodeRequest request) {
        FileSystemProvider fileSystem = getFileSystem(request.sourceFs);
        return fileSystem.create(request.sourceFileId, request.name, request.dir, getUploadOption(request));
    }

    @PostMapping("/unarchive")
    public TreeNode unarchive(@RequestBody UnArchiveNodeRequest request) {
        FileSystemProvider sourceFs = getFileSystem(request.sourceFs);
        FileSystemProvider targetFs = getFileSystem(request.targetFs);

        Path archive = sourceFs.getArchiveAsLocalPath(request.sourceFileId);
        // InputStream stream = sourceFs.getEntryInputStream(request.sourceFileId);
        TreeNode sourceItem = sourceFs.toTreeNode(request.sourceFileId);

        String fileExtension = CommonUtils.getExtension(sourceItem.getName());
        String fileWithoutExtension = sourceItem.getName().substring(0, sourceItem.getName().length() - fileExtension.length());
        Path targetPath = CommonUtils.getTmpPath().resolve(fileWithoutExtension);

        ArchiveUtil.UnzipFileIssueHandler issueHandler = ArchiveUtil.UnzipFileIssueHandler.valueOf(request.fileHandler);
        ArchiveUtil.ArchiveFormat zipFormat = ArchiveUtil.ArchiveFormat.getHandlerByExtension(fileExtension);
        ArchiveUtil.unzip(archive, zipFormat, targetPath, null, null, issueHandler);
        Set<String> ids = Arrays.stream(Objects.requireNonNull(targetPath.toFile().listFiles()))
                .map(File::toString).collect(Collectors.toSet());
        TreeNode fileSystemItem = targetFs.copy(localFileSystem.toTreeNodes(ids), request.targetDir,
                FileSystemProvider.UploadOption.Replace);

        if (request.removeSource) {
            sourceFs.delete(Collections.singleton(request.sourceFileId));
        }
        return fileSystemItem;
    }

    @PostMapping("/archive")
    public TreeNode archive(@RequestBody ArchiveNodeRequest request) throws Exception {
        // make archive from requested files/folders
        Path zipFile = archiveSource(request.sourceFs, request.format, request.sourceFileIds, request.targetName, request.level,
                request.password);

        try {
            TreeNode zipTreeNode = TreeNode.of(zipFile.toString(), zipFile, localFileSystem);

            return getFileSystem(request.targetFs).copy(zipTreeNode, request.targetDir,
                    FileSystemProvider.UploadOption.Replace);
        } finally {
            Files.deleteIfExists(zipFile);
        }
    }

    @PostMapping("/copy")
    public TreeNode copyNode(@RequestBody CopyNodeRequest request) {
        FileSystemProvider sourceFs = getFileSystem(request.sourceFs);
        FileSystemProvider targetFs = getFileSystem(request.targetFs);

        Set<TreeNode> entries = sourceFs.toTreeNodes(request.getSourceFileIds());
        TreeNode fileSystemItem = targetFs.copy(entries, request.targetPath, FileSystemProvider.UploadOption.Replace);

        if (request.removeSource) {
            sourceFs.delete(request.getSourceFileIds());
        }
        return fileSystemItem;
    }

    @PostMapping("/upload")
    public TreeNode upload(@RequestParam("sourceFs") String sourceFs, @RequestParam("sourceFileId") String sourceFileId,
                             @RequestParam("replace") boolean replace, @RequestParam("data") MultipartFile[] files) {
        FileSystemProvider fileSystem = getFileSystem(sourceFs);
        Set<TreeNode> treeNodes = Stream.of(files).map(TreeNode::of).collect(Collectors.toSet());
        return fileSystem.copy(treeNodes, sourceFileId, FileSystemProvider.UploadOption.Replace);
    }

    @PostMapping("/rename")
    public TreeNode rename(@RequestBody RenameNodeRequest request) {
        return getFileSystem(request.sourceFs).rename(request.sourceFileId, request.newName, getUploadOption(request));
    }

    @GetMapping("/search")
    public List<TreeNode> search(@RequestParam("query") String query) {
        //  if (StringUtils.isEmpty(query)) {
        return null;
        // }
        /*List<TreeNode> result = new ArrayList<>();
        Files.walkFileTree(Paths.get(defaultPath), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().contains(query)) {
                    result.add(new TreeNode(file.toFile()));
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

    private FileSystemProvider getFileSystem(String fs) {
        return getFileSystemEntity(fs).getFileSystem(entityContext);
    }

    private BaseFileSystemEntity getFileSystemEntity(String fs) {
        return fileSystems.stream().filter(e -> e.getEntityID().equals(fs)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("Unable to find File system: " + fs));
    }

    @Getter
    @Setter
    private static class RenameNodeRequest extends BaseNodeRequest {
        private String sourceFileId;
        private String newName;
    }

    @Getter
    @Setter
    private static class CreateNodeRequest extends BaseNodeRequest {
        public String sourceFileId;
        public String name;
        public boolean dir;
    }

    @Getter
    @Setter
    private static class UnArchiveNodeRequest extends BaseNodeRequest {
        public String targetDir;
        public String fileHandler;
        private String sourceFileId;
        public boolean removeSource;
    }

    @Getter
    @Setter
    private static class ArchiveNodeRequest extends BaseNodeRequest {
        public Set<String> sourceFileIds;
        public String format;
        public String level;
        public String targetDir;
        public String targetName;
        public boolean removeSource;
    }

    @Getter
    @Setter
    private static class CopyNodeRequest extends BaseNodeRequest {
        private Set<String> sourceFileIds;
        private String targetPath;
        private boolean removeSource;
    }

    @Getter
    @Setter
    private static class DownloadRequest extends BaseNodeRequest {
        public Set<String> sourceFileIds;
    }

    @Getter
    @Setter
    private static class RemoveFilesRequest extends BaseNodeRequest {
        public Set<String> sourceFileIds;
    }

    @Getter
    @Setter
    private static class ListRequest extends BaseNodeRequest {
        private String sourceFileId;
    }

    @Getter
    @Setter
    private static class GetFSRequest {
        private boolean showOnlyLocalFS;
        private SelectedNode[] selectedNodes;

        @Getter
        @Setter
        private static class SelectedNode {
            private String fs;
            private String id;
        }
    }

    @Getter
    @Setter
    private static class BaseNodeRequest {
        public String sourceFs;
        public String targetFs;
        public String password;
        public boolean replace;
    }

    private Path archiveSource(String fs, String format, Set<String> sourceFileIds, String targetName, String level,
                               String password) throws IOException {
        FileSystemProvider sourceFs = getFileSystem(fs);

        Collection<TreeNode> entries = sourceFs.toTreeNodes(sourceFileIds);
        Path tmpArchiveAssemblerPath = CommonUtils.getTmpPath().resolve("tmp_archive_assembler_" + System.currentTimeMillis());

        try {
            if (!targetName.endsWith("." + format)) {
                targetName = targetName + "." + format;
            }
            Path targetPath = CommonUtils.getTmpPath().resolve(targetName);

            List<Path> result = new ArrayList<>();
            localFileSystem.copyEntries(entries, tmpArchiveAssemblerPath, new CopyOption[]{REPLACE_EXISTING}, result);
            List<Path> filesToArchive =
                    Arrays.stream(Objects.requireNonNull(tmpArchiveAssemblerPath.toFile().listFiles())).map(File::toPath)
                            .collect(Collectors.toList());

            return ArchiveUtil.zip(filesToArchive, targetPath, ArchiveUtil.ArchiveFormat.getHandlerByExtension(format),
                    level, password, null);
        } finally {
            FileUtils.deleteDirectory(tmpArchiveAssemblerPath.toFile());
        }
    }

    private FileSystemProvider.UploadOption getUploadOption(BaseNodeRequest request) {
        return request.replace ? FileSystemProvider.UploadOption.Replace : FileSystemProvider.UploadOption.Error;
    }
}
