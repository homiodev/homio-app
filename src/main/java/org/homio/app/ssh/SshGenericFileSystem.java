package org.homio.app.ssh;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.sshtools.client.SshClient;
import com.sshtools.client.sftp.SftpClientTask;
import com.sshtools.client.sftp.SftpFile;
import com.sshtools.common.sftp.SftpStatusException;
import com.sshtools.common.ssh.SshException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.EntityContextBGP.ThreadContext;
import org.homio.bundle.api.fs.FileSystemProvider;
import org.homio.bundle.api.fs.TreeNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SshGenericFileSystem implements FileSystemProvider {

    private SshGenericEntity entity;
    private final EntityContext entityContext;
    private long connectionHashCode;
    private final LoadingCache<String, List<SftpFile>> fileCache;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition;
    private SftpClientTask sftpClient;
    private ThreadContext<Void> sftpClientThread;

    public SshGenericFileSystem(SshGenericEntity entity, EntityContext entityContext) {
        this.entity = entity;
        this.entityContext = entityContext;
        this.condition = lock.newCondition();

        this.fileCache = CacheBuilder.newBuilder().
                                     expireAfterWrite(1, TimeUnit.HOURS).build(new CacheLoader<>() {
                public @NotNull List<SftpFile> load(@NotNull String id) throws SftpStatusException, SshException {
                    SftpClientTask client = getSftpClient();
                    if (client == null) {
                        throw new SshException("Ssh client is null", -1);
                    }
                    SftpFile parent = client.openDirectory(id);
                    return client.readDirectory(parent).stream().filter(sftpFile -> {
                        String filename = sftpFile.getFilename();
                        return !filename.equals(".") && !filename.equals("..");
                    }).collect(Collectors.toList());
                }
            });
    }

    @Override
    public long getTotalSpace() {
        return 0;
    }

    @Override
    public long getUsedSpace() {
        return 0;
    }

    @Override
    public boolean restart(boolean force) {
        try {
            if (!force && connectionHashCode == entity.getConnectionHashCode()) {
                return true;
            }
            dispose();
            getChildren(entity.getFileSystemRoot());
            entity.setStatusOnline();
            connectionHashCode = entity.getConnectionHashCode();
            return true;
        } catch (Exception ex) {
            entity.setStatusError(ex);
            return false;
        }
    }

    @Override
    public void setEntity(Object entity) {
        this.entity = (SshGenericEntity) entity;
        restart(false);
    }

    @Override
    @SneakyThrows
    public InputStream getEntryInputStream(@NotNull String id) {
        try (InputStream stream = getSftpClientRequire().getInputStream(id)) {
            return new ByteArrayInputStream(IOUtils.toByteArray(stream));
        }
    }

    @SneakyThrows
    @Override
    public Set<TreeNode> toTreeNodes(@NotNull Set<String> ids) {
        Set<TreeNode> fmPaths = new HashSet<>();
        for (String id : ids) {
            Path parent = Paths.get(id).getParent();
            fileCache.get(fixPath(parent)).stream()
                     .filter(f -> f.getAbsolutePath().equals(id)).findAny()
                     .ifPresent(sftpFile -> fmPaths.add(buildTreeNode(sftpFile)));
        }
        return fmPaths;
    }

    @SneakyThrows
    @Override
    public TreeNode delete(@NotNull Set<String> ids) {
        List<SftpFile> files = new ArrayList<>();
        for (String id : ids) {
            if (id.equals("")) {
                throw new IllegalStateException("Path must be specified");
            }
            SftpFile sftpFile = getSftpFile(id, true);
            if (sftpFile != null) {
                getSftpClientRequire().rm(id);
                this.fileCache.invalidate(fixPath(Paths.get(id).getParent()));
                files.add(sftpFile);
            }
        }
        return buildRoot(files);
    }

    @Override
    @SneakyThrows
    public TreeNode create(@NotNull String parentId, @NotNull String name, boolean isDir, UploadOption uploadOption) {
        String fullPath = fixPath(Paths.get(parentId).resolve(name));
        SftpFile existedFile = getSftpFile(fullPath, false);

        if (uploadOption != UploadOption.Replace) {
            if (existedFile != null) {
                if (uploadOption == UploadOption.SkipExist) {
                    return null;
                } else if (uploadOption == UploadOption.Error) {
                    throw new FileAlreadyExistsException("File " + name + " already exists");
                }
            }
        }
        SftpClientTask client = getSftpClientRequire();
        if (isDir) {
            client.mkdir(fullPath);
        } else {
            client.put(new ByteArrayInputStream(new byte[0]), fullPath);
        }
        this.fileCache.invalidate(parentId);
        return buildRoot(Collections.singleton(getSftpFile(fullPath, true)));
    }

    private String fixPath(Path path) {
        return SystemUtils.IS_OS_WINDOWS ? path.toString().replace("\\", "/") : path.toString();
    }

    @SneakyThrows
    @Override
    public TreeNode rename(@NotNull String id, @NotNull String newName, UploadOption uploadOption) {
        List<SftpFile> files = fileCache.get(id);
        if (files.size() == 1) {
            SftpFile file = files.get(0);
            clearCache(file);
            FieldUtils.writeDeclaredField(file, "filename", newName, true);

            if (uploadOption != UploadOption.Replace) {
                SftpFile existedFile = getSftpFile(newName, true);
                if (existedFile != null) {
                    if (uploadOption == UploadOption.SkipExist) {
                        return null;
                    } else if (uploadOption == UploadOption.Error) {
                        throw new FileAlreadyExistsException("File " + newName + " already exists");
                    }
                }
            }

            getSftpClientRequire().rename(file.getFilename(), newName);
            //   this.fileCache.put(file.getId(), Collections.singletonList(file));

            return buildRoot(Collections.singleton(file));
        }
        throw new IllegalStateException("File '" + id + "' not found");
    }

    @Override
    public TreeNode copy(@NotNull Collection<TreeNode> entries, @NotNull String targetId, UploadOption uploadOption) {
        List<SftpFile> result = new ArrayList<>();
        this.fileCache.invalidateAll();
        copyEntries(entries, targetId, uploadOption, result);
        return buildRoot(result);
    }

    @Override
    public Set<TreeNode> loadTreeUpToChild(@Nullable String parent, @NotNull String id) {
        return getChildrenRecursively("");
    }

    @Override
    @SneakyThrows
    public @NotNull Set<TreeNode> getChildren(@NotNull String parentId) {
        List<SftpFile> files = fileCache.get(appendRoot(parentId));
        Stream<SftpFile> stream = files.stream();
        if (!entity.isShowHiddenFiles()) {
            stream = stream.filter(s -> !s.getFilename().startsWith("."));
        }
        return stream.map(this::buildTreeNode).collect(Collectors.toSet());
    }

    @Override
    public Set<TreeNode> getChildrenRecursively(@NotNull String parentId) {
        throw new IllegalArgumentException("");
    }

    private TreeNode buildRoot(Collection<SftpFile> result) {
        Path root = Paths.get(entity.getFileSystemRoot());
        TreeNode rootPath = new TreeNode(true, false, "", "", 0L, 0L, null, null);
        // ftpFile.getName() return FQN
        for (SftpFile ftpFile : result) {
            Path pathCursor = root;
            TreeNode cursor = rootPath;

            //build parent directories
            for (Path pathItem : root.relativize(Paths.get(ftpFile.getAbsolutePath()).getParent())) {
                pathCursor = pathCursor.resolve(pathItem);
                SftpFile folder = getSftpFile(fixPath(pathCursor), true);
                if (folder == null) {
                    throw new IllegalStateException("Error in fetching ssh parent dir");
                }
                cursor = cursor.addChild(buildTreeNode(folder));
            }
            cursor.addChild(buildTreeNode(ftpFile));
        }
        return rootPath;
    }

    @SneakyThrows
    private TreeNode buildTreeNode(SftpFile file) {
        boolean isDirectory = file.isDirectory();
        long fileSize = file.getAttributes().getSize().longValue();
        boolean hasChildren = fileSize != 6;
        return new TreeNode(
            isDirectory,
            isDirectory && !hasChildren,
            file.getFilename(),
            file.getAbsolutePath(),
            fileSize,
            file.getAttributes().getModifiedDateTime().getTime(),
            this, null);
    }

    private @NotNull SftpClientTask getSftpClientRequire() {
        SftpClientTask client = getSftpClient();
        if (client == null) {
            throw new IllegalStateException("Sftp client is null");
        }
        return client;
    }

    private @Nullable SftpClientTask getSftpClient() {
        if (sftpClient == null || sftpClient.isClosed()) {
            if (sftpClientThread != null) {
                sftpClientThread.cancel();
            }
            try {
                lock.lock();
                condition.signal();
            } finally {
                lock.unlock();
            }
            sftpClientThread = entityContext.bgp().builder("sftp-client").execute(() -> {
                try (SshClient sshClient = entity.createSshClient()) {
                    sshClient.runTask(new SftpClientTask(sshClient) {

                        protected void doSftp() {
                            sftpClient = this;
                            try {
                                lock.lock();
                                condition.await();
                            } catch (InterruptedException ignore) {
                                sftpClient = null;
                            } finally {
                                lock.unlock();
                            }
                        }
                    });
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        return sftpClient;
    }

    private void clearCache(SftpFile file) {
        this.fileCache.invalidate(file.getAbsolutePath());
    }

    @SneakyThrows
    private void copyEntries(Collection<TreeNode> entries, String targetId, UploadOption uploadOption, List<SftpFile> result) {
        SftpClientTask client = getSftpClientRequire();
        for (TreeNode entry : entries) {
            String path = Paths.get(targetId).resolve(entry.getName()).toString();
            if (entry.getAttributes().isDir()) {
                client.mkdir(path);
                result.add(new SftpFile(path, client.get(path)));
                copyEntries(entry.getFileSystem().getChildren(entry), path, uploadOption, result);
            } else {
                try (InputStream stream = entry.getInputStream()) {
                    List<SftpFile> children = fileCache.get(entry.getParent().getName());
                    SftpFile sftpFile = children.stream().filter(c -> c.getFilename().equals(entry.getName())).findAny().orElse(null);
                    if (sftpFile != null) {
                        if (uploadOption == UploadOption.Append) {
                                /*byte[] prependContent = IOUtils.toByteArray(ftpClient.retrieveFileStream(path));
                                byte[] content = Bytes.concat(prependContent, IOUtils.toByteArray(stream));
                                ftpClient.appendFile(path, new ByteArrayInputStream(content));

                                client.putFiles();*/
                            throw new IllegalArgumentException("");
                        } else {
                            client.put(stream, path);
                        }
                    } else {
                        client.put(stream, path);
                    }
                    result.add(new SftpFile(path, client.get(path)));
                }
            }
        }
    }

    @Nullable
    private SftpFile getSftpFile(String fullPath, boolean fromCache) {
        try {
            if (fromCache) {
                String parentId = fixPath(Paths.get(fullPath).getParent());
                return fileCache.get(parentId).stream().filter(c -> c.getAbsolutePath().equals(fullPath)).findAny().orElse(null);
            } else {
                return getSftpClientRequire().openFile(fullPath);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String appendRoot(String parentId) {
        if (!parentId.startsWith(entity.getFileSystemRoot())) {
            return fixPath(Paths.get(entity.getFileSystemRoot()).resolve(parentId));
        }
        return parentId;
    }
}
