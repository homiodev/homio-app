package org.homio.app.service.device;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.homio.api.Context;
import org.homio.api.entity.storage.BaseFileSystemEntity;
import org.homio.api.fs.BaseCachedFileSystemProvider;
import org.homio.app.model.entity.NetworkDriveEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class NetworkDriveFileSystem extends BaseCachedFileSystemProvider<NetworkDriveEntity, NetworkDriveFileSystem.NetworkDriveFile, NetworkDriveFileSystem.NetworkDriveFileService> {

  public NetworkDriveFileSystem(NetworkDriveEntity entity, Context context) {
    super(entity, context);
  }

  @Override
  protected @NotNull NetworkDriveFileSystem.NetworkDriveFileService createService() {
    return new NetworkDriveFileService();
  }

  @Override
  public @NotNull String getFileSystemId() {
    return entity.getEntityID();
  }

  @SuppressWarnings("rawtypes")
  @RequiredArgsConstructor
  public class NetworkDriveFile implements FsFileEntity<NetworkDriveFile> {

    private final @NotNull String id;
    private final @NotNull NetworkDriveEntity.NetworkFile file;

    @Override
    public @NotNull String getAbsolutePath() {
      return id;
    }

    @Override
    public boolean isDirectory() {
      return file.isDirectory();
    }

    @Override
    public Long getSize() {
      return file.getSize() == -1 ? null : file.getSize();
    }

    @Override
    public Long getModifiedDateTime() {
      return file.getModified();
    }

    @Override
    @SneakyThrows
    public @Nullable NetworkDriveFileSystem.NetworkDriveFile getParent(boolean stub) {
      Path parent = Paths.get(id).getParent();
      if (isPathEmpty(parent)) {
        return null;
      }
      if (stub) {
        return new NetworkDriveFile(fixPath(parent), () -> parent.getFileName().toString());
      } else {
        return service.getFile(fixPath(parent));
      }
    }

    @Override
    public boolean hasChildren() {
      return file.getSize() != 6;
    }

    @Override
    public BaseFileSystemEntity getEntity() {
      return entity;
    }
  }

  public class NetworkDriveFileService implements BaseFSService<NetworkDriveFile> {

    @Override
    @SneakyThrows
    public void close() {
    }

    @Override
    public @NotNull InputStream getInputStream(@NotNull String id) throws Exception {
      return entity.execute(client -> {
        InputStream inputStream = client.getInputStream(id);
        if (!(inputStream instanceof ByteArrayInputStream)) {
          return new ByteArrayInputStream(inputStream.readAllBytes());
        }
        return inputStream;
      }, true);
    }

    @Override
    public void mkdir(@NotNull String id) throws Exception {
      entity.execute(client -> {
        client.mkdir(id);
        return null;
      }, false);
    }

    @Override
    public void put(@NotNull InputStream inputStream, @NotNull String id) throws Exception {
      entity.execute(client -> client.put(inputStream, id), true);
    }

    @Override
    public void rename(@NotNull String oldName, @NotNull String newName) throws Exception {
      entity.execute(client -> client.rename(oldName, newName), false);
    }

    @Override
    public NetworkDriveFile getFile(@NotNull String id) throws Exception {
      if (!id.startsWith(entity.getFileSystemRoot())) {
        id = fixPath(Paths.get(entity.getFileSystemRoot()).resolve(id));
      }
      var finalId = id;
      return entity.execute(networkClient -> {
        var file = networkClient.getFile(finalId);
        return file == null ? null : new NetworkDriveFile(finalId, file);
      }, false);
    }

    @Override
    @SneakyThrows
    public List<NetworkDriveFile> readChildren(@NotNull String parentId) {
      return entity.execute(client ->
        client.getAllFiles(parentId).stream()
          .map(id -> new NetworkDriveFile(fixPath(Paths.get(parentId)
            .resolve(id.getName())), id))
          .collect(Collectors.toList()), false);
    }

    @Override
    @SneakyThrows
    public boolean rm(@NotNull NetworkDriveFileSystem.NetworkDriveFile networkDriveFile) {
      return entity.execute(ftpClient -> {
        if (networkDriveFile.isDirectory()) {
          return ftpClient.deleteDir(networkDriveFile.getId());
        } else {
          return ftpClient.deleteFile(networkDriveFile.getId());
        }
      }, false);
    }

    @Override
    @SneakyThrows
    public void recreate() {
    }
  }
}
