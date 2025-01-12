package org.homio.app.ssh;

import com.pivovarit.function.ThrowingRunnable;
import com.sshtools.client.SshClient;
import com.sshtools.client.sftp.SftpClientTask;
import com.sshtools.client.sftp.SftpFile;
import com.sshtools.common.sftp.SftpStatusException;
import com.sshtools.common.ssh.SshException;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.homio.api.Context;
import org.homio.api.ContextBGP.ThreadContext;
import org.homio.api.entity.storage.BaseFileSystemEntity;
import org.homio.api.fs.BaseCachedFileSystemProvider;
import org.homio.app.ssh.SshGenericFileSystem.SshFile;
import org.homio.app.ssh.SshGenericFileSystem.SshFileService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.stream.Collectors;

public class SshGenericFileSystem extends BaseCachedFileSystemProvider<SshGenericEntity, SshFile, SshFileService> {

  public SshGenericFileSystem(SshGenericEntity entity, Context context) {
    super(entity, context);
    this.entity = entity;
  }

  @Override
  public int getFileSystemAlias() {
    return 0;
  }

  @Override
  public String getFileSystemId() {
    return entity.getEntityID();
  }

  @Override
  protected @NotNull SshFileService createService() {
    return new SshFileService();
  }

  @SneakyThrows
  private void inLock(ThrowingRunnable<Exception> handler) {
    try {
      lock.lock();
      handler.run();
    } finally {
      lock.unlock();
    }
  }

  @RequiredArgsConstructor
  public class SshFile implements FsFileEntity<SshFile> {

    private final SftpFile file;

    @Override
    public @NotNull String getAbsolutePath() {
      return file.getAbsolutePath();
    }

    @Override
    public boolean isDirectory() throws SftpStatusException, SshException {
      return file.isDirectory();
    }

    @Override
    public Long getSize() throws SftpStatusException, SshException {
      return isDirectory() ? null : file.getAttributes().getSize().longValue();
    }

    @Override
    public Long getModifiedDateTime() throws SftpStatusException, SshException {
      return file.getAttributes().getModifiedDateTime().getTime();
    }

    @Override
    @SneakyThrows
    public @Nullable SshFile getParent(boolean stub) {
      if (!stub) {
        SftpFile parent = file.getParent();
        return parent == null ? null : new SshFile(parent);
      }
      if (file.getAbsolutePath().lastIndexOf('/') == -1) {
        return null;
      }
      Path parentPath = Paths.get(file.getAbsolutePath()).getParent();
      if (isPathEmpty(parentPath)) {
        return null;
      }
      return new SshFile(new SftpFile(parentPath.toString(), null) {
        @Override
        public boolean isDirectory() {
          return true;
        }
      }) {
        @Override
        public Long getModifiedDateTime() {
          return null;
        }
      };
    }

    @Override
    public boolean hasChildren() {
      return true;
    }

    @Override
    public BaseFileSystemEntity getEntity() {
      return entity;
    }
  }

  public class SshFileService implements BaseFSService<SshFile> {

    private ThreadContext<Void> serviceThread;
    private SftpClientTask sftpService;

    @Override
    public void close() {
      if (serviceThread != null && sftpService != null) {
        serviceThread.cancel();
        sftpService = null;
        serviceThread = null;
      }
    }

    @Override
    public @NotNull InputStream getInputStream(@NotNull String id) throws Exception {
      return getSftpService().getInputStream(id);
    }

    @Override
    public void mkdir(@NotNull String id) throws Exception {
      getSftpService().mkdirs(id);
    }

    @Override
    public void put(@NotNull InputStream inputStream, @NotNull String id) throws Exception {
      getSftpService().put(inputStream, id);
    }

    @Override
    public void rename(@NotNull String oldName, @NotNull String newName) throws Exception {
      getSftpService().rename(oldName, newName);
    }

    @Override
    public SshFile getFile(@NotNull String id) throws Exception {
      return new SshFile(getSftpService().openFile(id));
    }

    @Override
    @SneakyThrows
    public List<SshFile> readChildren(@NotNull String parentId) {
      SftpClientTask service = getSftpService();
      SftpFile parent = service.openDirectory(parentId);
      return service.readDirectory(parent).stream().filter(sftpFile -> {
        String filename = sftpFile.getFilename();
        return !filename.equals(".") && !filename.equals("..");
      }).map(SshFile::new).collect(Collectors.toList());
    }

    @Override
    @SneakyThrows
    public boolean rm(SshFile sshFile) {
      getSftpService().rm(sshFile.getId());
      return true;
    }

    @Override
    public void recreate() {

    }

    @SneakyThrows
    private @NotNull SftpClientTask getSftpService() {
      AtomicReference<Exception> ex = new AtomicReference<>(null);
      if (sftpService == null || sftpService.isClosed()) {
        Condition waitCondition = lock.newCondition();
        inLock(condition::signal);
        if (serviceThread != null) {
          serviceThread.cancel();
        }
        serviceThread = context.bgp().builder("sftp-client").execute(() -> {
          try (SshClient sshClient = entity.createSshClient()) {
            sshClient.runTask(new SftpClientTask(sshClient) {

              protected void doSftp() {
                sftpService = this;
                try {
                  lock.lock();
                  waitCondition.signal();
                  condition.await();
                } catch (InterruptedException ignore) {
                  sftpService = null;
                } finally {
                  waitCondition.signal();
                  lock.unlock();
                }
              }
            });
          } catch (Exception e) {
            ex.set(e);
            inLock(waitCondition::signal);
          }
        });
        inLock(waitCondition::await);
      }
      if (ex.get() != null) {
        throw ex.get();
      }
      return sftpService;
    }
  }
}
