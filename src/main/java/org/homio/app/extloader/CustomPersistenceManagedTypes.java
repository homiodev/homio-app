package org.homio.app.extloader;

import lombok.Getter;
import lombok.Setter;
import org.springframework.core.io.ResourceLoader;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypesScanner;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.HashSet;
import java.util.List;

@Getter
@Setter
@Component
public class CustomPersistenceManagedTypes implements PersistenceManagedTypes {

  private final ResourceLoader resourceLoader;
  private List<String> managedClassNames;
  private List<String> managedPackages;
  private URL persistenceUnitRootUrl;

  public CustomPersistenceManagedTypes(ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
    this.scan();
  }

  /**
   * Scan for new entities.
   *
   * @return true if found new entities
   */
  public boolean scan() {
    PersistenceManagedTypes persistenceManagedTypes = new PersistenceManagedTypesScanner(resourceLoader).scan("org.homio");
    this.managedPackages = persistenceManagedTypes.getManagedPackages();
    this.persistenceUnitRootUrl = persistenceManagedTypes.getPersistenceUnitRootUrl();
    List<String> scannedManagedClassNames = persistenceManagedTypes.getManagedClassNames();
    if (managedClassNames == null
        || this.managedClassNames.size() != scannedManagedClassNames.size()
        || !new HashSet<>(this.managedClassNames).containsAll(scannedManagedClassNames)) {
      this.managedClassNames = scannedManagedClassNames;
      return true;
    }
    return false;
  }
}
