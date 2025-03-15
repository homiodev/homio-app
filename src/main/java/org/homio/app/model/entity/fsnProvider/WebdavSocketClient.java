package org.homio.app.model.entity.fsnProvider;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import lombok.RequiredArgsConstructor;
import org.homio.app.model.entity.NetworkDriveEntity;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.util.List;
import java.util.stream.Collectors;

public class WebdavSocketClient implements NetworkDriveEntity.NetworkClient {

  @NotNull
  private final NetworkDriveEntity entity;
  private Sardine sardine;

  public WebdavSocketClient(@NotNull NetworkDriveEntity entity) {
    this.entity = entity;
  }

  @Override
  public NetworkDriveEntity.NetworkClient connect(boolean localPassive) {
    ProxySelector proxy = null;
    if (entity.getProxyType() != Proxy.Type.DIRECT) {
      proxy = ProxySelector.of(new InetSocketAddress(entity.getProxyHost(), entity.getProxyPort()));
    }
    sardine = SardineFactory.begin(entity.getUser(), entity.getPassword().asString(), proxy);
    return this;
  }

  @Override
  public InputStream getInputStream(@NotNull String id) throws IOException {
    return sardine.get(entity.getUrl() + id);
  }

  @Override
  public void mkdir(@NotNull String id) throws IOException {
    sardine.createDirectory(entity.getUrl() + id);
  }

  @Override
  public boolean put(@NotNull InputStream inputStream, @NotNull String id) throws IOException {
    sardine.put(entity.getUrl() + id, inputStream);
    return true;
  }

  @Override
  public NetworkDriveEntity.NetworkFile getFile(@NotNull String id) throws IOException {
    return null;
  }

  @Override
  public List<WebdavNetworkFile> getAllFiles(@NotNull String parentId) throws IOException {
    return sardine.list(entity.getUrl() + parentId).stream()
      .map(WebdavNetworkFile::new)
      .collect(Collectors.toList());
  }

  public boolean delete(@NotNull String id) throws IOException {
    sardine.delete(entity.getUrl() + id);
    return true;
  }

  @Override
  public boolean rename(@NotNull String oldId, @NotNull String newId) throws IOException {
    sardine.move(entity.getUrl() + oldId, entity.getUrl() + newId);
    return true;
  }

  @Override
  public boolean deleteFile(@NotNull String id) throws IOException {
    sardine.delete(entity.getUrl() + id);
    return true;
  }

  @Override
  public void close() throws IOException {
    if(sardine != null) {
      sardine.shutdown();
    }
  }

  @RequiredArgsConstructor
  public static class WebdavNetworkFile implements NetworkDriveEntity.NetworkFile {
    private final DavResource res;

    @Override
    public String getName() {
      return res.getName();
    }

    @Override
    public boolean isDirectory() {
      return res.isDirectory();
    }

    @Override
    public Long getSize() {
      return res.getContentLength();
    }

    @Override
    public Long getModified() {
      return res.getModified().getTime();
    }
  }
}
