package org.homio.app.model.entity.fsnProvider;

import lombok.RequiredArgsConstructor;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.homio.app.model.entity.NetworkDriveEntity;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Arrays;
import java.util.List;

public class FtpSocketClient extends FTPClient implements NetworkDriveEntity.NetworkClient {

  @NotNull
  private final NetworkDriveEntity entity;

  public FtpSocketClient(NetworkDriveEntity entity) {
    this.entity = entity;
    setConnectTimeout(getConnectTimeout() * 1000);
    setControlKeepAliveTimeout(getControlKeepAliveTimeoutDuration());
    if (entity.getProxyType() != Proxy.Type.DIRECT) {
      setProxy(new Proxy(entity.getProxyType(), new InetSocketAddress(entity.getProxyHost(), entity.getProxyPort())));
    }
  }

  @Override
  public NetworkDriveEntity.NetworkClient connect(boolean localPassive) {
    try {
      if (entity.getPort() != 0) {
        connect(entity.getUrl(), entity.getPort());
      } else {
        try {
          connect(entity.getUrl());
        } catch (Exception ignore) {
          connect(entity.getUrl(), FTP.DEFAULT_PORT);
        }
      }
    } catch (Exception ex) {
      throw new RuntimeException("Error connect to remote url: " + ex.getMessage());
    }
    try {
      if (!login(entity.getUser(), entity.getPassword().asString())) {
        throw new RuntimeException("User or password incorrect.");
      }
    } catch (Exception ex) {
      throw new RuntimeException("Error during attempt login to ftp: " + ex.getMessage());
    }
    if (localPassive) {
      enterLocalPassiveMode();
    }
    return this;
  }

  @Override
  public InputStream getInputStream(@NotNull String id) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    setFileType(FTPClient.BINARY_FILE_TYPE);
    if (!retrieveFile(id, outputStream)) {
      throw new RuntimeException("Unable to retrieve file: <" + id + "> from ftp. Msg: " + getReplyString());
    }
    return new ByteArrayInputStream(outputStream.toByteArray());
  }

  @Override
  public void mkdir(@NotNull String id) throws IOException {
    makeDirectory(id);
  }

  @Override
  public boolean put(@NotNull InputStream inputStream, @NotNull String id) throws IOException {
    setFileType(FTPClient.BINARY_FILE_TYPE);
    return storeFile(id, inputStream);

  }

  @Override
  public NetworkDriveEntity.NetworkFile getFile(@NotNull String id) throws IOException {
    return new FtpNetworkFile(this.mlistFile(id));
  }

  @Override
  public List<FtpNetworkFile> getAllFiles(@NotNull String parentId) throws IOException {
    return Arrays.stream(this.listFiles(parentId)).map(FtpNetworkFile::new).toList();
  }

  @Override
  public boolean deleteDir(@NotNull String id) throws IOException {
    return removeDirectory(id);
  }

  @Override
  public void close() {
    try {
      logout();
      disconnect();
    } catch (Exception ignore) {
    }
  }

  @RequiredArgsConstructor
  public static class FtpNetworkFile implements NetworkDriveEntity.NetworkFile {
    private final FTPFile file;

    @Override
    public String getName() {
      return file.getName();
    }

    @Override
    public boolean isDirectory() {
      return file.isDirectory();
    }

    @Override
    public Long getSize() {
      return file.getSize();
    }

    @Override
    public Long getModified() {
      return file.getTimestamp() == null ? null : file.getTimestamp().getTimeInMillis();
    }
  }
}
