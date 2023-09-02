package org.homio.app.rest;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.homio.api.ui.field.selection.UIFieldTreeNodeSelection.LOCAL_FS;
import static org.homio.api.util.Constants.ADMIN_ROLE_AUTHORIZE;
import static org.homio.app.model.entity.user.UserBaseEntity.FILE_MANAGER_RESOURCE_AUTHORIZE;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.homio.api.EntityContext;
import org.homio.api.entity.storage.BaseFileSystemEntity;
import org.homio.api.fs.FileSystemProvider;
import org.homio.api.fs.TreeConfiguration;
import org.homio.api.fs.TreeNode;
import org.homio.api.fs.archive.ArchiveUtil;
import org.homio.api.fs.archive.ArchiveUtil.ArchiveFormat;
import org.homio.api.util.CommonUtils;
import org.homio.app.service.FileSystemService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/rest/fs")
@RequiredArgsConstructor
public class FileSystemController {

    // constructor parameters
    private final FileSystemService fileSystemService;
    private final EntityContext entityContext;

    @PostMapping("")
    @PreAuthorize(FILE_MANAGER_RESOURCE_AUTHORIZE)
    public List<TreeConfiguration> getFileSystems(@RequestBody GetFSRequest request) {
        List<TreeConfiguration> configurations = new ArrayList<>();
        String firstAvailableFS = getFS(Optional.ofNullable(request.fileSystemIds).map(ids -> ids.isEmpty() ? null : ids.get(0)).orElse(null));
        // if not fs - set as local
        for (GetFSRequest.SelectedNode selectedNode : request.selectedNodes) {
            selectedNode.fs = defaultString(selectedNode.fs, firstAvailableFS);
        }
        Collection<BaseFileSystemEntity> fsItems = getRequestedFileSystems(request);
        for (BaseFileSystemEntity fileSystem : fsItems) {
            TreeConfiguration configuration = fileSystem.buildFileSystemConfiguration(entityContext);

            for (GetFSRequest.SelectedNode selectedNode : request.selectedNodes) {
                if (selectedNode.fs.equals(fileSystem.getEntityID())) {
                    FileSystemProvider fileSystemProvider = fileSystem.getFileSystem(entityContext);
                    try {
                        Set<TreeNode> treeNodes = fileSystemProvider.loadTreeUpToChild(request.getRootPath(), selectedNode.id);
                        if (treeNodes != null) {
                            configuration.setChildren(treeNodes);
                        }
                    } catch (Exception ignore) {} // case when input selectedNode.id is invalid
                }
            }
            configurations.add(configuration);
        }
        return configurations;
    }

    @PostMapping("/list")
    @PreAuthorize(FILE_MANAGER_RESOURCE_AUTHORIZE)
    public Collection<TreeNode> list(@RequestBody ListRequest request) {
        return fileSystemService.getFileSystem(request.sourceFs).getChildren(request.sourceFileId);
    }

    @DeleteMapping
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public TreeNode remove(@RequestBody RemoveFilesRequest request) {
        return fileSystemService.getFileSystem(request.sourceFs).delete(request.sourceFileIds);
    }

    @GetMapping("/{sourceFs}/download/{resource}")
    @PreAuthorize(FILE_MANAGER_RESOURCE_AUTHORIZE)
    public ResponseEntity<InputStreamResource> download(
        @PathVariable("sourceFs") String sourceFs,
        @PathVariable("resource") String resource) throws Exception {
        FileSystemProvider fileSystem = fileSystemService.getFileSystem(sourceFs);
        TreeNode treeNode = fileSystem.toTreeNode(resource);
        InputStream inputStream = fileSystem.getEntryInputStream(treeNode.getId());

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            String contentType = treeNode.getAttributes().getContentType();
            if (contentType != null) {
                mediaType = MediaType.parseMediaType(contentType);
            }
        } catch (Exception ignore) {
        }
        return CommonUtils.inputStreamToResource(inputStream, mediaType, null);
    }

    @SneakyThrows
    @PostMapping("/download")
    @PreAuthorize(FILE_MANAGER_RESOURCE_AUTHORIZE)
    public ResponseEntity<InputStreamResource> downloadArchive(@RequestBody DownloadRequest request) {
        Path zipFile = archiveSource(request.sourceFs, "zip", request.sourceFileIds, "downloadContent", null, null);
        return CommonUtils.inputStreamToResource(
            Files.newInputStream(zipFile), new MediaType("application", "zip"), null);
    }

    @PostMapping("/create")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public TreeNode createNode(@RequestBody CreateNodeRequest request) {
        FileSystemProvider fileSystem = fileSystemService.getFileSystem(request.sourceFs);
        return fileSystem.create(request.sourceFileId, request.name, request.dir, getUploadOption(request));
    }

    @PostMapping("/unarchive")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public TreeNode unarchive(@RequestBody UnArchiveNodeRequest request) {
        FileSystemProvider sourceFs = fileSystemService.getFileSystem(request.sourceFs);
        FileSystemProvider targetFs = fileSystemService.getFileSystem(request.targetFs);

        Path archive = sourceFs.getArchiveAsLocalPath(request.sourceFileId);
        // InputStream stream = sourceFs.getEntryInputStream(request.sourceFileId);
        TreeNode sourceItem = sourceFs.toTreeNode(request.sourceFileId);

        String fileExtension = CommonUtils.getExtension(sourceItem.getName());
        String fileWithoutExtension = sourceItem.getName().substring(0, sourceItem.getName().length() - fileExtension.length());
        Path targetPath = CommonUtils.getTmpPath().resolve(fileWithoutExtension);

        ArchiveUtil.UnzipFileIssueHandler issueHandler = ArchiveUtil.UnzipFileIssueHandler.valueOf(request.fileHandler);
        ArchiveUtil.ArchiveFormat zipFormat = ArchiveUtil.ArchiveFormat.getHandlerByExtension(fileExtension);
        ArchiveUtil.unzip(archive, zipFormat, targetPath, null, null, issueHandler);
        Set<String> ids = Arrays.stream(Objects.requireNonNull(targetPath.toFile().listFiles())).map(File::toString).collect(Collectors.toSet());
        TreeNode fileSystemItem = targetFs.copy(fileSystemService.getLocalFileSystem().toTreeNodes(ids), request.targetDir,
            FileSystemProvider.UploadOption.Replace);

        if (request.removeSource) {
            sourceFs.delete(Collections.singleton(request.sourceFileId));
        }
        return fileSystemItem;
    }

    @PostMapping("/archive")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public TreeNode archive(@RequestBody ArchiveNodeRequest request) throws Exception {
        // make archive from requested files/folders
        Path zipFile = archiveSource(request.sourceFs, request.format, request.sourceFileIds, request.targetName, request.level, request.password);

        try {
            TreeNode zipTreeNode = TreeNode.of(zipFile.toString(), zipFile, fileSystemService.getLocalFileSystem());

            return fileSystemService.getFileSystem(request.targetFs).copy(zipTreeNode, request.targetDir, FileSystemProvider.UploadOption.Replace);
        } finally {
            Files.deleteIfExists(zipFile);
        }
    }

    @PostMapping("/copy")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public TreeNode copyNode(@RequestBody CopyNodeRequest request) {
        FileSystemProvider sourceFs = fileSystemService.getFileSystem(request.sourceFs);
        FileSystemProvider targetFs = fileSystemService.getFileSystem(request.targetFs);

        Set<TreeNode> entries = sourceFs.toTreeNodes(request.getSourceFileIds());
        TreeNode fileSystemItem = targetFs.copy(entries, request.targetPath, FileSystemProvider.UploadOption.Replace);

        if (request.removeSource) {
            sourceFs.delete(request.getSourceFileIds());
        }
        return fileSystemItem;
    }

    @PostMapping("/upload")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public TreeNode upload(
            @RequestParam("sourceFs") String sourceFs,
            @RequestParam("sourceFileId") String sourceFileId,
            @RequestParam("replace") boolean replace,
            @RequestParam("data") MultipartFile[] files) {
        FileSystemProvider fileSystem = fileSystemService.getFileSystem(sourceFs);
        Set<TreeNode> treeNodes = Stream.of(files).map(TreeNode::of).collect(Collectors.toSet());
        return fileSystem.copy(treeNodes, sourceFileId, FileSystemProvider.UploadOption.Replace);
    }

    @PostMapping("/rename")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public TreeNode rename(@RequestBody RenameNodeRequest request) {
        return fileSystemService.getFileSystem(request.sourceFs).rename(request.sourceFileId, request.newName, getUploadOption(request));
    }

    @GetMapping("/search")
    @PreAuthorize(FILE_MANAGER_RESOURCE_AUTHORIZE)
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


    private Path archiveSource(String fs, String format, Set<String> sourceFileIds, String targetName, String level, String password) throws IOException {
        FileSystemProvider sourceFs = fileSystemService.getFileSystem(fs);

        Collection<TreeNode> entries = sourceFs.toTreeNodes(sourceFileIds);
        Path tmpArchiveAssemblerPath = CommonUtils.getTmpPath().resolve("tmp_archive_assembler_" + System.currentTimeMillis());

        try {
            if (!targetName.endsWith("." + format)) {
                targetName = targetName + "." + format;
            }
            Path targetPath = CommonUtils.getTmpPath().resolve(targetName);

            List<Path> result = new ArrayList<>();
            fileSystemService.getLocalFileSystem().copyEntries(entries, tmpArchiveAssemblerPath, new CopyOption[]{REPLACE_EXISTING}, result);
            List<Path> filesToArchive =
                    Arrays.stream(Objects.requireNonNull(tmpArchiveAssemblerPath.toFile().listFiles())).map(File::toPath).collect(Collectors.toList());

            ArchiveFormat archiveFormat = ArchiveFormat.getHandlerByExtension(format);
            ArchiveUtil.zip(filesToArchive, targetPath, archiveFormat, level, password, null);
            return targetPath;
        } finally {
            FileUtils.deleteDirectory(tmpArchiveAssemblerPath.toFile());
        }
    }

    private FileSystemProvider.UploadOption getUploadOption(BaseNodeRequest request) {
        return request.replace ? FileSystemProvider.UploadOption.Replace : FileSystemProvider.UploadOption.Error;
    }

    private Collection<BaseFileSystemEntity> getRequestedFileSystems(GetFSRequest request) {
        if (request.fileSystemIds == null || request.fileSystemIds.isEmpty()) {
            return this.fileSystemService.getFileSystems()
                                         .stream()
                                         .filter(BaseFileSystemEntity::isShowInFileManager)
                                         .collect(Collectors.toList());
        } else {
            return request.fileSystemIds
                .stream()
                .map(fileSystemService::getFileSystemEntity)
                .collect(Collectors.toList());
        }
    }

    private String getFS(String id) {
        return LOCAL_FS.equals(id) || id == null || id.equals("dvc_board_primary") ? fileSystemService.getLocalFileSystem().getEntity().getEntityID() : id;
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
        public boolean removeSource;
        private String sourceFileId;
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

        private SelectedNode[] selectedNodes;
        private List<String> fileSystemIds;
        private String rootPath;

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
}
