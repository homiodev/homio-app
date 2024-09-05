package org.homio.app.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.pivovarit.function.ThrowingSupplier;
import jakarta.ws.rs.BadRequestException;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.homio.api.Context;
import org.homio.api.entity.storage.BaseFileSystemEntity;
import org.homio.api.exception.NotFoundException;
import org.homio.api.fs.FileSystemProvider;
import org.homio.api.fs.TreeConfiguration;
import org.homio.api.fs.TreeNode;
import org.homio.api.fs.archive.ArchiveUtil;
import org.homio.api.fs.archive.ArchiveUtil.ArchiveFormat;
import org.homio.api.stream.audio.AudioFormat;
import org.homio.api.stream.audio.AudioStream;
import org.homio.api.stream.audio.FileAudioStream;
import org.homio.api.util.CommonUtils;
import org.homio.app.model.entity.user.UserGuestEntity;
import org.homio.app.model.entity.widget.impl.media.WidgetFMNodeValue;
import org.homio.app.service.FileSystemService;
import org.homio.app.service.device.LocalFileSystemProvider;
import org.homio.hquery.Curl;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.homio.api.ui.field.selection.UIFieldTreeNodeSelection.LOCAL_FS;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;
import static org.homio.hquery.Curl.ONE_MB;

@Log4j2
@RestController
@RequestMapping("/rest/fs")
@RequiredArgsConstructor
public class FileSystemController {

    // constructor parameters
    private final FileSystemService fileSystemService;
    private final Context context;

    @PostMapping("")
    public List<TreeConfiguration> getFileSystems(@RequestBody GetFSRequest request) {
        UserGuestEntity.assertFileManagerReadAccess(context);

        List<TreeConfiguration> configurations = new ArrayList<>();
        String firstAvailableFS = getFS(Optional.ofNullable(request.fileSystemIds).map(ids -> ids.isEmpty() ? null : ids.get(0)).orElse(null));
        // if not fs - set as local
        for (GetFSRequest.SelectedNode selectedNode : request.selectedNodes) {
            selectedNode.fs = Objects.toString(selectedNode.fs, firstAvailableFS);
        }
        Collection<BaseFileSystemEntity> fsItems = getRequestedFileSystems(request);
        for (BaseFileSystemEntity fileSystem : fsItems) {
            List<TreeConfiguration> fsConfigurations = fileSystem.buildFileSystemConfiguration(context);
            loadSelectedChildren(request, fileSystem, fsConfigurations.get(0));
            configurations.addAll(fsConfigurations);
        }
        return configurations;
    }

    @PostMapping("/list")
    public Collection<TreeNode> list(@RequestBody NodeRequest request) {
        UserGuestEntity.assertFileManagerReadAccess(context);
        return fileSystemService.getFileSystem(request.sourceFs, request.alias)
                .getChildren(request.sourceFileId);
    }

    @DeleteMapping
    public TreeNode remove(@RequestBody RemoveFilesRequest request) {
        UserGuestEntity.assertFileManagerWriteAccess(context);
        return fileSystemService.getFileSystem(request.sourceFs, request.alias).delete(request.sourceFileIds);
    }

    @PostMapping("/{sourceFs}/download")
    public ResponseEntity<InputStreamResource> download(
            @PathVariable("sourceFs") String sourceFs,
            @RequestBody NodeRequest request) {
        UserGuestEntity.assertFileManagerReadAccess(context);

        FileSystemProvider fileSystem = fileSystemService.getFileSystem(sourceFs, request.alias);
        TreeNode treeNode = fileSystem.toTreeNode(request.sourceFileId);
        if (treeNode == null || treeNode.getId() == null) {
            throw NotFoundException.fileNotFound(request.sourceFileId);
        }
        InputStream inputStream = fileSystem.getEntryInputStream(treeNode.getId());
        MediaType mediaType = findMediaType(treeNode);
        return CommonUtils.inputStreamToResource(inputStream, mediaType, null);
    }

    @SneakyThrows
    @PostMapping("/{sourceFs}/playAudio")
    public void playAudio(
            @PathVariable("sourceFs") String sourceFs,
            @RequestBody NodeRequest request) {
        UserGuestEntity.assertFileManagerReadAccess(context);

        FileSystemProvider fileSystem = fileSystemService.getFileSystem(sourceFs, request.alias);
        TreeNode treeNode = fileSystem.toTreeNode(request.sourceFileId);
        if (treeNode == null || treeNode.getId() == null) {
            throw NotFoundException.fileNotFound(request.sourceFileId);
        }
        AudioStream audioStream;
        if (fileSystem instanceof LocalFileSystemProvider local) {
            audioStream = new FileAudioStream(local.getFile(treeNode.getId()), AudioFormat.MP3);
        } else {
            InputStream inputStream = fileSystem.getEntryInputStream(treeNode.getId());
            audioStream = AudioStream.fromUnknownStream(inputStream, AudioFormat.MP3);
        }
        context.ui().media().playWebAudio(audioStream, null, null);
    }

    @SneakyThrows
    @GetMapping("/{jsonContent}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable("jsonContent") String jsonContent,
                                                        @RequestParam(value = "start", required = false) double start,
                                                        @RequestParam(value = "end", required = false) double end) {
        UserGuestEntity.assertFileManagerReadAccess(context);

        byte[] content = Base64.getDecoder().decode(jsonContent.getBytes());
        NodeRequest nodeRequest = OBJECT_MAPPER.readValue(content, NodeRequest.class);
        if (end > 0) {
            return downloadWithCut(nodeRequest, start, end);
        }
        return download(nodeRequest.getSourceFs(), nodeRequest);
    }

    private ResponseEntity<InputStreamResource> downloadWithCut(NodeRequest request, double start, double end) {
        FileSystemProvider fileSystem = fileSystemService.getFileSystem(request.sourceFs, request.alias);
        InputStream io = getTrimVideoInputStream(fileSystem, request.sourceFileId, start, end);
        if (io == null) {
            throw new IllegalStateException("Unable to trim video. IO is null");
        }
        TreeNode treeNode = fileSystem.toTreeNode(request.sourceFileId);
        MediaType mediaType = findMediaType(treeNode);
        return CommonUtils.inputStreamToResource(io, mediaType, null);
    }

    @SneakyThrows
    private InputStream getTrimVideoInputStream(FileSystemProvider fileSystem, String sourceFileId, double start, double end) {
        TreeNode treeNode = fileSystem.toTreeNode(sourceFileId);
        String fn = treeNode.getName();
        Path trimFile = CommonUtils.getTmpPath().resolve(fn);
        Files.deleteIfExists(trimFile);
        if (fileSystem instanceof LocalFileSystemProvider fsl) {
            File videoFile = fsl.getFile(sourceFileId);
            context.media().fireFfmpeg("-ss " + start,
                    videoFile.getAbsolutePath(), "-to " + end + " -c:v copy -c:a copy \"" + trimFile + "\"", 600);
        } else {
            Files.copy(fileSystem.getEntryInputStream(sourceFileId), trimFile, REPLACE_EXISTING);
        }
        if (Files.exists(trimFile) && Files.size(trimFile) > 0) {
            return Files.newInputStream(trimFile);
        }
        return null;
    }

    @SneakyThrows
    @PostMapping("/{sourceFs}/preview")
    public ResponseEntity<InputStreamResource> preview(
            @PathVariable("sourceFs") String sourceFs,
            @RequestParam("w") int width,
            @RequestParam("h") int height,
            @RequestParam(value = "drawTextAsImage", required = false) boolean drawTextAsImage,
            @RequestBody NodeRequest request) {
        UserGuestEntity.assertFileManagerReadAccess(context);

        FileSystemProvider fileSystem = fileSystemService.getFileSystem(sourceFs, request.alias);
        TreeNode treeNode = fileSystem.toTreeNode(request.sourceFileId);
        if (treeNode == null || treeNode.getId() == null) {
            throw NotFoundException.fileNotFound(request.sourceFileId);
        }
        WidgetFMNodeValue node = new WidgetFMNodeValue(treeNode);
        String content = WidgetFMNodeValue.getThumbnail(node.treeNode(), width, height, drawTextAsImage);

        MediaType mediaType = findMediaType(treeNode);
        return CommonUtils.inputStreamToResource(new ByteArrayInputStream(content.getBytes()), mediaType, null);
    }

    @NotNull
    private static MediaType findMediaType(TreeNode treeNode) {
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            String contentType = treeNode.getAttributes().getContentType();
            if (contentType != null) {
                mediaType = MediaType.parseMediaType(contentType);
            }
        } catch (Exception ignore) {
        }
        return mediaType;
    }

    @SneakyThrows
    @PostMapping("/download")
    public ResponseEntity<InputStreamResource> download(@RequestBody DownloadRequest request) {
        UserGuestEntity.assertFileManagerReadAccess(context);

        Path zipFile = archiveSource(request.sourceFs, request.alias, "zip", request.sourceFileIds,
                "downloadContent", null, null);
        return CommonUtils.inputStreamToResource(
                Files.newInputStream(zipFile), new MediaType("application", "zip"), null);
    }

    @PostMapping("/create")
    public TreeNode createNode(@RequestBody CreateNodeRequest request) {
        UserGuestEntity.assertFileManagerWriteAccess(context);

        FileSystemProvider fileSystem = fileSystemService.getFileSystem(request.sourceFs, request.alias);
        return fileSystem.create(request.sourceFileId, request.name, request.dir, getUploadOption(request));
    }

    @PostMapping("/unarchive")
    public TreeNode unarchive(@RequestBody UnArchiveNodeRequest request) {
        UserGuestEntity.assertFileManagerWriteAccess(context);

        FileSystemProvider sourceFs = fileSystemService.getFileSystem(request.sourceFs, request.alias);
        FileSystemProvider targetFs = fileSystemService.getFileSystem(request.targetFs, request.targetAlias);

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
    public TreeNode archive(@RequestBody ArchiveNodeRequest request) throws Exception {
        UserGuestEntity.assertFileManagerWriteAccess(context);

        // make archive from requested files/folders
        Path zipFile = archiveSource(request.sourceFs, request.alias, request.format,
                request.sourceFileIds, request.targetName, request.level, request.password);

        try {
            TreeNode zipTreeNode = TreeNode.of(zipFile.toString(), zipFile, fileSystemService.getLocalFileSystem());

            return fileSystemService.getFileSystem(request.targetFs, request.targetAlias)
                    .copy(zipTreeNode, request.targetDir, FileSystemProvider.UploadOption.Replace);
        } finally {
            Files.deleteIfExists(zipFile);
        }
    }

    @PostMapping("/copy")
    public TreeNode copyNode(@RequestBody CopyNodeRequest request) {
        UserGuestEntity.assertFileManagerWriteAccess(context);

        FileSystemProvider sourceFs = fileSystemService.getFileSystem(request.sourceFs, request.alias);
        FileSystemProvider targetFs = fileSystemService.getFileSystem(request.targetFs, request.targetAlias);

        Set<TreeNode> entries = sourceFs.toTreeNodes(request.getSourceFileIds());
        if (entries.isEmpty()) {
            throw new BadRequestException("Unable to find entries from: " + String.join("; ", request.getSourceFileIds()));
        }
        TreeNode fileSystemItem = targetFs.copy(entries, request.targetPath, FileSystemProvider.UploadOption.Replace);

        if (request.removeSource) {
            sourceFs.delete(request.getSourceFileIds());
        }
        return fileSystemItem;
    }

    @SneakyThrows
    @PostMapping("/upload")
    public TreeNode upload(
            @RequestParam("sourceFs") String sourceFs,
            @RequestParam("sourceFileId") String sourceFileId,
            @RequestParam("alias") int alias,
            @RequestParam("replace") boolean replace,
            @RequestParam(value = "url", defaultValue = "") String url,
            @RequestParam(value = "data", required = false) MultipartFile[] files) {
        UserGuestEntity.assertFileManagerWriteAccess(context);

        FileSystemProvider fileSystem = fileSystemService.getFileSystem(sourceFs, alias);

        if (files != null && files.length > 0) {
            log.info("Request upload files: '{}' to {}/{}", Arrays.stream(files).map(f -> Objects.toString(f.getOriginalFilename(),
                    f.getName())).collect(Collectors.joining(", ")), sourceFs, sourceFileId);
            int size = Arrays.stream(files).mapToInt(value -> (int) value.getSize()).sum();
            return copyFiles(size, sourceFileId, () -> {
                Set<TreeNode> treeNodes = Stream.of(files).map(TreeNode::of).collect(Collectors.toSet());
                return fileSystem.copy(treeNodes, sourceFileId, FileSystemProvider.UploadOption.Replace);
            });
        } else if (!url.isEmpty()) {
            log.info("Request upload url file: '{}' to {}/{}", url, sourceFs, sourceFileId);
            URI uri = new URI(url);
            String name = Paths.get(uri.getPath()).getFileName().toString();
            int size = Curl.getFileSize(uri.toURL());
            return copyFiles(size, sourceFileId, () -> {
                URLConnection urlConnection = uri.toURL().openConnection();
                urlConnection.setConnectTimeout(10_000);
                urlConnection.setReadTimeout(10_000);
                TreeNode treeNode = new TreeNode(false, false, name, name,
                        (long) size, null, null, null)
                        .setInputStream(urlConnection.getInputStream());
                treeNode.setFileSystem(fileSystem);
                return fileSystem.copy(treeNode, sourceFileId, FileSystemProvider.UploadOption.Replace);
            });
        }
        throw new IllegalArgumentException("Unable to upload from unknown source");
    }

    @SneakyThrows
    private TreeNode copyFiles(int size, String sourceFileId, ThrowingSupplier<TreeNode, Exception> handler) {
        if (size / ONE_MB > 1) {
            context.bgp().builder("copy-" + sourceFileId).execute(() ->
                    handler.get().refreshOnUI(context));
            return null;
        } else {
            return handler.get();
        }
    }

    @PostMapping("/rename")
    public TreeNode rename(@RequestBody RenameNodeRequest request) {
        UserGuestEntity.assertFileManagerWriteAccess(context);

        return fileSystemService.getFileSystem(request.sourceFs, request.alias)
                .rename(request.sourceFileId, request.newName, getUploadOption(request));
    }

    private void loadSelectedChildren(GetFSRequest request, BaseFileSystemEntity fileSystem, TreeConfiguration configuration) {
        for (GetFSRequest.SelectedNode selectedNode : request.selectedNodes) {
            if (selectedNode.fs.equals(fileSystem.getEntityID())) {
                FileSystemProvider fileSystemProvider = fileSystem.getFileSystem(context, 0);
                try {
                    Set<TreeNode> treeNodes = fileSystemProvider.loadTreeUpToChild(request.getRootPath(), selectedNode.id);
                    if (treeNodes != null) {
                        configuration.setChildren(treeNodes);
                    }
                } catch (Exception ignore) {
                } // case when input selectedNode.id is invalid
            }
        }
    }

    @GetMapping("/search")
    public List<TreeNode> search(@RequestParam("query") String query) {
        UserGuestEntity.assertFileManagerReadAccess(context);
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

    private Path archiveSource(String fs, int alias, String format, Set<String> sourceFileIds, String targetName, String level, String password)
            throws IOException {
        FileSystemProvider sourceFs = fileSystemService.getFileSystem(fs, alias);

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
    public static class RenameNodeRequest extends BaseNodeRequest {

        private String sourceFileId;
        private String newName;
    }

    @Getter
    @Setter
    public static class CreateNodeRequest extends BaseNodeRequest {

        public String sourceFileId;
        public String name;
        public boolean dir;
    }

    @Getter
    @Setter
    public static class UnArchiveNodeRequest extends BaseNodeRequest {

        public String targetDir;
        public String fileHandler;
        public boolean removeSource;
        private String sourceFileId;
    }

    @Getter
    @Setter
    public static class ArchiveNodeRequest extends BaseNodeRequest {

        public Set<String> sourceFileIds;
        public String format;
        public String level;
        public String targetDir;
        public String targetName;
        public boolean removeSource;
    }

    @Getter
    @Setter
    public static class CopyNodeRequest extends BaseNodeRequest {

        private Set<String> sourceFileIds;
        private String targetPath;
        private boolean removeSource;
    }

    @Getter
    @Setter
    public static class DownloadRequest extends BaseNodeRequest {

        public Set<String> sourceFileIds;
    }

    @Getter
    @Setter
    public static class RemoveFilesRequest extends BaseNodeRequest {

        public Set<String> sourceFileIds;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(Include.NON_EMPTY)
    public static class NodeRequest extends BaseNodeRequest {

        private String sourceFileId;
    }

    @Getter
    @Setter
    public static class GetFSRequest {

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
    @Accessors(chain = true)
    @JsonInclude(Include.NON_EMPTY)
    public static class BaseNodeRequest {

        public String sourceFs;
        public int alias;

        public String targetFs;
        public int targetAlias;

        public String password;
        public boolean replace;
    }
}
