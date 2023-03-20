package org.touchhome.app.cb.fs;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.touchhome.app.utils.InternalUtil.TIKA;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.touchhome.app.cb.ComputerBoardEntity;
import org.touchhome.bundle.api.fs.FileSystemProvider;
import org.touchhome.bundle.api.fs.TreeNode;
import org.touchhome.bundle.api.fs.archive.ArchiveUtil;
import org.touchhome.bundle.api.util.TouchHomeUtils;

@AllArgsConstructor
public class ComputerBoardFileSystem implements FileSystemProvider {

    @Getter
    private ComputerBoardEntity entity;

    @Override
    public Path getArchiveAsLocalPath(@NotNull String id) {
        Path path = buildPath(id);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Id: " + id + " is not a local path");
        }
        return path;
    }

    @Override
    @SneakyThrows
    public @NotNull Set<TreeNode> getChildren(@NotNull String id) {
        Set<TreeNode> fmPaths = new HashSet<>();
        Path parentPath = buildPath(id);
        if (Files.exists(parentPath)) {
            if (ArchiveUtil.isArchive(parentPath)) {
                List<File> files = ArchiveUtil.getArchiveEntries(parentPath, null);
                return buildArchiveEntries(parentPath, files, false).getChildren();
            }
        } else {
            Path archivePath = getArchivePath(parentPath);
            if (archivePath != null) {
                List<File> children = ArchiveUtil.getChildren(archivePath, archivePath.relativize(parentPath).toString());
                return children.stream().map(c -> buildTreeNode(true, c, c.isDirectory(),
                                   archivePath.resolve(c.getPath()).toString(), null))
                               .collect(Collectors.toSet());
            }
        }
        try (Stream<Path> stream = Files.list(parentPath)) {
            for (Path path : stream.collect(Collectors.toList())) {
                try {
                    if (!Files.isHidden(path)) {
                        fmPaths.add(buildTreeNode(path, path.toFile()));
                    }
                } catch (AccessDeniedException ex) {
                    fmPaths.add(buildTreeNode(path, path.toFile()));
                }
            }
        }
        return fmPaths;
    }

    @Override
    @SneakyThrows
    public Set<TreeNode> getChildrenRecursively(@NotNull String parentId) {
        TreeNode root = new TreeNode();
        buildTreeNodeRecursively(buildPath(parentId), root);
        return root.getChildren();
    }

    @Override
    @SneakyThrows
    public Set<TreeNode> toTreeNodes(@NotNull Set<String> ids) {
        Set<TreeNode> fmPaths = new HashSet<>();
        Map<Path, Set<Path>> archiveIds = new HashMap<>();
        for (String id : ids) {
            Path path = buildPath(id);
            if (Files.exists(path)) {
                if (!Files.isHidden(path) && Files.isReadable(path)) {
                    fmPaths.add(buildTreeNode(path, path.toFile()));
                }
            } else {
                Path archivePath = getArchivePath(path);
                if (archivePath != null) {
                    archiveIds.putIfAbsent(archivePath, new HashSet<>());
                    archiveIds.get(archivePath).add(path);
                }
            }
        }
        for (Map.Entry<Path, Set<Path>> entry : archiveIds.entrySet()) {
            List<File> archiveEntries = ArchiveUtil.getArchiveEntries(entry.getKey(), null);
            Map<String, Path> valueToKey =
                entry.getValue().stream().collect(Collectors.toMap(p -> entry.getKey().relativize(p).toString(), p -> p));

            for (File archiveEntry : archiveEntries) {
                Path path = valueToKey.get(archiveEntry.toString());
                if (path != null) {
                    fmPaths.add(buildTreeNode(path, archiveEntry));
                }
            }
        }
        return fmPaths;
    }

    @Override
    @SneakyThrows
    public InputStream getEntryInputStream(@NotNull String id) {
        Path path = buildPath(id);
        if (!Files.exists(path)) {
            // try check if path is archive;
            Path archivePath = getArchivePath(path);
            if (archivePath != null) {
                return ArchiveUtil.downloadArchiveEntry(archivePath, archivePath.relativize(path).toString(), null);
            }
        } else {
            return Files.newInputStream(path);
        }
        throw new IllegalArgumentException("Unable to find entry: " + path);
    }

    @Override
    public long getTotalSpace() {
        return new File(entity.getFileSystemRoot()).getTotalSpace();
    }

    @Override
    public long getUsedSpace() {
        File file = new File(entity.getFileSystemRoot());
        return file.getTotalSpace() - file.getUsableSpace();
    }

    @Override
    public boolean restart(boolean force) {
        return true;
    }

    @Override
    public void setEntity(Object entity) {
        this.entity = (ComputerBoardEntity) entity;

    }

    @Override
    @SneakyThrows
    public TreeNode delete(Set<String> ids) {
        Set<Path> removedFiles = new HashSet<>();
        Map<Path, Set<String>> archiveIds = new HashMap<>();
        for (String id : ids) {
            Path path = buildPath(id);
            if (Files.isDirectory(path)) {
                removedFiles.addAll(TouchHomeUtils.removeFileOrDirectory(path));
            } else {
                if (Files.exists(path)) {
                    removedFiles.add(path);
                    Files.delete(path);
                } else {
                    Path archivePath = getArchivePath(path);
                    if (archivePath != null) {
                        archiveIds.putIfAbsent(archivePath, new HashSet<>());
                        archiveIds.get(archivePath).add(archivePath.relativize(path).toString());
                    }
                }
            }
        }
        for (Map.Entry<Path, Set<String>> entry : archiveIds.entrySet()) {
            Set<Path> removedPathList = ArchiveUtil.removeEntries(entry.getKey(), entry.getValue(), null);
            for (Path path : removedPathList) {
                if (entry.getValue().contains(path.toString())) {
                    removedFiles.add(entry.getKey().resolve(path.toString())); // build local path
                }
            }
        }
        return buildRoot(removedFiles);
    }

    @Override
    @SneakyThrows
    public TreeNode create(@NotNull String parentId, @NotNull String name, boolean isDir, UploadOption uploadOption) {
        Path path = buildPath(parentId).resolve(name);
        if (Files.exists(path)) {
            if (uploadOption == UploadOption.Error) {
                throw new FileAlreadyExistsException("Folder " + name + " already exists");
            }
            return buildRoot(Collections.singletonList(path));
        }
        Path resultPath = null;
        if (Files.exists(path)) {
            if (uploadOption == UploadOption.SkipExist) {
                return null;
            } else if (!isDir && uploadOption == UploadOption.Error) {
                throw new FileAlreadyExistsException("File already exist");
            }
        }
        try {
            if (isDir) {
                resultPath = Files.createDirectories(path);
            } else {
                resultPath = Files.createFile(path);
            }
        } catch (NoSuchFileException ex) {
            Path archivePath = getArchivePath(path);
            if (archivePath != null) {
                Path entryPath = archivePath.relativize(path);
                Pair<TreeNode, TreeNode> entryTree = TreeNode.buildTree(entryPath);
                // mark entry as file or dir
                if (!isDir) {
                    entryTree.getRight().getAttributes().setDir(false);
                    entryTree.getRight().setInputStream(new ByteArrayInputStream(new byte[0]));
                }
                ArchiveUtil.addToArchive(archivePath, Collections.singleton(entryTree.getLeft()));
                return buildArchiveEntries(archivePath, ArchiveUtil.getArchiveEntries(archivePath, null), true);
            }
        }
        return resultPath == null ? null : buildRoot(Collections.singletonList(resultPath));
    }

    @Override
    @SneakyThrows
    public TreeNode rename(@NotNull String id, @NotNull String newName, UploadOption uploadOption) {
        Path path = buildPath(id);
        if (Files.exists(path)) {
            Path target = path.resolveSibling(newName);
            if (Files.exists(target) && uploadOption == UploadOption.Error) {
                throw new FileAlreadyExistsException("File " + newName + " already exists");
            }
            Path created = Files.move(path, target, REPLACE_EXISTING);
            return buildRoot(Collections.singletonList(created));
        } else {
            Path archivePath = getArchivePath(path);
            if (archivePath != null) {
                String entryName = archivePath.relativize(path).toString();
                ArchiveUtil.renameEntry(archivePath, entryName, newName);
                return buildArchiveEntries(archivePath, ArchiveUtil.getArchiveEntries(archivePath, null), true);
            }
        }
        throw new IllegalStateException("File '" + id + "' not found");
    }

    @Override
    @SneakyThrows
    public TreeNode copy(@NotNull Collection<TreeNode> entries, @NotNull String targetId, UploadOption uploadOption) {
        CopyOption[] options = uploadOption == UploadOption.Replace ? new CopyOption[]{REPLACE_EXISTING} : new CopyOption[0];
        List<Path> result = new ArrayList<>();
        Path targetPath = buildPath(targetId);
        copyEntries(entries, targetPath, options, result);
        result.add(targetPath);
        return buildRoot(result);
    }

    @Override
    public Set<TreeNode> loadTreeUpToChild(@Nullable String rootPath, @NotNull String id) {
        Path targetPath;
        if (rootPath != null) {
            Path path = buildPath(rootPath);
            targetPath = path.resolve(id);
        } else {
            targetPath = buildPath(id);
        }
        if (!Files.exists(targetPath)) {
            return null;
        }
        Set<TreeNode> rootChildren = getChildren(trimToEmpty(rootPath));
        Set<TreeNode> currentChildren = rootChildren;
        Path pathId = Paths.get(trimToEmpty(rootPath));
        for (Path pathItem : Paths.get(id)) { // split id by '/'
            Path pathItemId = pathId.resolve(pathItem);
            String pathItemIdStr = pathItemId.toString().replaceAll("\\\\", "/");
            TreeNode foundedObject =
                currentChildren.stream().filter(c -> c.getId().equals(pathItemIdStr)).findAny().orElseThrow(() ->
                    new IllegalStateException("Unable find object: " + pathItemIdStr));
            currentChildren = getChildren(pathItemIdStr);
            foundedObject.addChildren(currentChildren);
            pathId = pathItemId;
        }
        return rootChildren;
    }

    public void copyEntries(Collection<TreeNode> entries, Path targetPath, CopyOption[] options, List<Path> result)
        throws IOException {
        // if copying to archive
        Path archivePath = getArchivePath(targetPath);
        if (archivePath != null) {
            Path pathInArchive = archivePath.relativize(targetPath);
            if (!pathInArchive.toString().isEmpty()) {
                Pair<TreeNode, TreeNode> tree = TreeNode.buildTree(pathInArchive);
                tree.getRight().addChildren(entries);
                entries = Collections.singletonList(tree.getLeft());
            }
            for (TreeNode entry : entries) {
                result.addAll(entry.toPath(archivePath));
            }

            ArchiveUtil.addToArchive(archivePath, entries);
            return;
        }

        boolean hasFileInPathname = !TouchHomeUtils.getExtension(targetPath.toString()).isEmpty();
        if (!hasFileInPathname) {
            Files.createDirectories(targetPath);
        } else {
            Files.createDirectories(targetPath.getParent());
        }

        // check if targetPath already has fileName
        if (entries.size() == 1 && !entries.iterator().next().getAttributes().isDir()) {
            TreeNode entry = entries.iterator().next();
            Path entryPath = hasFileInPathname ? targetPath : targetPath.resolve(entry.getName());
            try (InputStream stream = entry.getInputStream()) {
                Files.copy(stream, entryPath, options);
            }
            result.add(entryPath);
            return;
        }
        for (TreeNode entry : entries) {
            Path entryPath = targetPath.resolve(entry.getName());
            result.add(entryPath);

            if (!entry.getAttributes().isDir()) {
                try (InputStream stream = entry.getInputStream()) {
                    Files.copy(stream, entryPath, options);
                }
            } else {
                Files.createDirectories(entryPath);
                copyEntries(entry.getFileSystem().getChildren(entry), entryPath, options, result);
            }
        }
    }

    // not optimised
    private void buildTreeNodeRecursively(Path parentPath, TreeNode root) throws IOException {
        try (Stream<Path> stream = Files.list(parentPath)) {
            for (Path path : stream.collect(Collectors.toList())) {
                try {
                    if (!Files.isHidden(path)) {
                        TreeNode childTreeNode = root.addChild(buildTreeNode(path, path.toFile()));
                        if (Files.isDirectory(path)) {
                            buildTreeNodeRecursively(path, childTreeNode);
                        }
                    }
                } catch (AccessDeniedException ignore) {
                }
            }
        }
    }

    private Path buildPath(String id) {
        if (!id.startsWith(entity.getFileSystemRoot())) {
            return Paths.get(entity.getFileSystemRoot()).resolve(id);
        }
        return Paths.get(id);
    }

    private Path getArchivePath(Path path) {
        Path cursor = path;
        do {
            if (Files.exists(cursor) && ArchiveUtil.isArchive(cursor)) {
                return cursor;
            }
        } while ((cursor = cursor.getParent()) != null);
        return null;
    }

    @SneakyThrows
    private TreeNode buildTreeNode(Path path, File file) {
        String fullPath = path.toAbsolutePath().toString().substring(entity.getFileSystemRoot().length());
        if (!SystemUtils.IS_OS_LINUX) {
            fullPath = fullPath.replaceAll("\\\\", "/");
        }
        if (fullPath.startsWith("/")) {
            fullPath = fullPath.substring(1);
        }
        boolean isDirectory = file.isDirectory();
        boolean exists = file.exists();
        String contentType =
            !isDirectory && exists ? StringUtils.defaultString(Files.probeContentType(path), TIKA.detect(path)) : null;
        return buildTreeNode(exists, file, isDirectory, fullPath, contentType);
    }

    private TreeNode buildTreeNode(boolean exists, File file, boolean isDirectory, String fullPath, String contentType) {
        boolean isEmpty = !exists || (file.isDirectory() && Objects.requireNonNull(file.list()).length == 0);
        if (isEmpty) {
            return new TreeNode(isDirectory, true, file.getName(), fullPath, 0L, 0L, this, contentType);
        }
        return new TreeNode(isDirectory, false, file.getName(), fullPath, file.length(), file.lastModified(), this, contentType);
    }

    private TreeNode buildArchiveEntries(Path archivePath, List<File> files, boolean includeRoot) {
        Path root = Paths.get(entity.getFileSystemRoot());
        if (!includeRoot) {
            root = root.resolve(archivePath);
        }
        final TreeNode rootPath = this.buildTreeNode(root, root.toFile());

        TreeNode cursorRoot = rootPath;
        if (includeRoot) {
            Path pathCursor = root;
            for (Path pathItem : root.relativize(archivePath)) {
                pathCursor = pathCursor.resolve(pathItem);
                cursorRoot = cursorRoot.addChild(buildTreeNode(pathCursor, pathCursor.toFile()));
            }
        }

        for (File file : files) {
            Path pathCursor = Paths.get(entity.getFileSystemRoot()).resolve(archivePath);
            TreeNode cursor = cursorRoot;
            for (Path pathItem : file.toPath()) {
                pathCursor = pathCursor.resolve(pathItem);
                cursor = cursor.addChild(buildTreeNode(pathCursor, file));
            }
        }
        evaluateEmptyFolders(rootPath);
        return rootPath;
    }

    private void evaluateEmptyFolders(TreeNode parentPath) {
        if (parentPath.getChildren() == null) {
            parentPath.getAttributes().setEmpty(true);
            return;
        }
        for (TreeNode child : parentPath.getChildren()) {
            evaluateEmptyFolders(child);
        }
    }

    private TreeNode buildRoot(Collection<Path> paths) {
        Path root = Paths.get(entity.getFileSystemRoot());
        TreeNode rootPath = this.buildTreeNode(root, root.toFile());
        for (Path path : paths) {
            Path pathCursor = root;
            TreeNode cursor = rootPath;
            for (Path pathItem : root.relativize(path)) {
                pathCursor = pathCursor.resolve(pathItem);
                cursor = cursor.addChild(buildTreeNode(pathCursor, pathCursor.toFile()));
            }
        }
        return rootPath;
    }
}
