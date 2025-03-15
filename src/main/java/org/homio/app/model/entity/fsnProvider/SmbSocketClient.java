package org.homio.app.model.entity.fsnProvider;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileAllInformation;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import org.homio.app.model.entity.NetworkDriveEntity;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class SmbSocketClient implements NetworkDriveEntity.NetworkClient {

  @NotNull
  private final NetworkDriveEntity entity;
  private SMBClient smbClient;
  private Connection connection;
  private Session session;
  private DiskShare share;

  public SmbSocketClient(@NotNull NetworkDriveEntity entity) {
    this.entity = entity;
  }

  @Override
  public NetworkDriveEntity.NetworkClient connect(boolean localPassive) {
    smbClient = new SMBClient();
    try {
      if (entity.getPort() == 0) {
        connection = smbClient.connect(entity.getUrl());
      } else {
        connection = smbClient.connect(entity.getUrl(), entity.getPort());
      }
      AuthenticationContext auth = new AuthenticationContext(
        entity.getUser(), entity.getPassword().asString().toCharArray(), entity.getDomain());
      session = connection.authenticate(auth);
      share = (DiskShare) session.connectShare(entity.getShareName());
    } catch (IOException e) {
      throw new RuntimeException("Failed to connect to SMB share", e);
    }
    return this;
  }

  @Override
  public InputStream getInputStream(@NotNull String id) throws IOException {
    File file = share.openFile(
      id,
      EnumSet.of(AccessMask.GENERIC_READ),
      EnumSet.noneOf(FileAttributes.class),
      EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
      SMB2CreateDisposition.FILE_OPEN,
      Set.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
    );
    return file.getInputStream();
  }

  @Override
  public void mkdir(@NotNull String id) throws IOException {
    share.mkdir(id);
  }

  @Override
  public boolean put(@NotNull InputStream inputStream, @NotNull String id) throws IOException {
    File file = share.openFile(
      id,
      EnumSet.of(AccessMask.GENERIC_WRITE),
      EnumSet.noneOf(FileAttributes.class),
      EnumSet.of(SMB2ShareAccess.FILE_SHARE_WRITE),
      SMB2CreateDisposition.FILE_OPEN_IF,
      Set.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
    );
    file.write(inputStream.readAllBytes(), 0);
    return true;
  }

  @Override
  public NetworkDriveEntity.NetworkFile getFile(@NotNull String id) throws IOException {
    if (share.fileExists(id)) {
      return new SmbNetworkFile(share.getFileInformation(id));
    }
    return null;
  }

  @Override
  public List<SmbNetworkFile> getAllFiles(@NotNull String parentId) {
    List<SmbNetworkFile> files = new ArrayList<>();
    for (FileIdBothDirectoryInformation fileInfo : share.list(parentId)) {
      String fileName = fileInfo.getFileName();
      if (!fileName.equals(".") && !fileName.equals("..")) {
        files.add(new SmbNetworkFile(fileInfo));
      }
    }
    return files;
  }

  @Override
  public boolean rename(@NotNull String oldId, @NotNull String newId) throws IOException {
    File file = share.openFile(
      oldId,
      EnumSet.of(AccessMask.GENERIC_READ, AccessMask.GENERIC_WRITE),
      EnumSet.noneOf(FileAttributes.class),
      EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE),
      SMB2CreateDisposition.FILE_OPEN,
      Set.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
    );
    file.rename(newId);
    file.close();
    return true;
  }

  @Override
  public boolean deleteFile(@NotNull String id) {
    FileAllInformation fileInformation = share.getFileInformation(id);
    if (new SmbNetworkFile(fileInformation).isDirectory()) {
      share.rmdir(id, true);
    } else {
      share.rm(id);
    }
    return true;
  }

  @Override
  public void close() throws IOException {
    if (share != null) {
      share.close();
    }
    if (session != null) {
      session.close();
    }
    if (connection != null) {
      connection.close();
    }
    if (smbClient != null) {
      smbClient.close();
    }
  }

  public static class SmbNetworkFile implements NetworkDriveEntity.NetworkFile {
    private final FileAllInformation fileInfo;
    private final FileIdBothDirectoryInformation fileIdInfo;

    public SmbNetworkFile(FileAllInformation fileInfo) {
      this.fileInfo = fileInfo;
      this.fileIdInfo = null;
    }

    public SmbNetworkFile(FileIdBothDirectoryInformation fileIdInfo) {
      this.fileIdInfo = fileIdInfo;
      this.fileInfo = null;
    }

    @Override
    public String getName() {
      if (fileIdInfo != null) {
        return fileIdInfo.getFileName();
      }
      return fileInfo.getNameInformation();
    }

    @Override
    public boolean isDirectory() {
      if (fileIdInfo != null) {
        long attributes = fileIdInfo.getFileAttributes();
        return (attributes & FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) != 0;
      }
      long attributes = fileInfo.getBasicInformation().getFileAttributes();
      return (attributes & FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) != 0;
    }

    @Override
    public Long getSize() {
      if (fileIdInfo != null) {
        return fileIdInfo.getEndOfFile();
      }
      return fileInfo.getStandardInformation().getEndOfFile();
    }

    @Override
    public Long getModified() {
      if (fileIdInfo != null) {
        return fileIdInfo.getLastWriteTime().toEpochMillis();
      }
      return fileInfo.getBasicInformation().getLastWriteTime().toEpochMillis();
    }
  }
}