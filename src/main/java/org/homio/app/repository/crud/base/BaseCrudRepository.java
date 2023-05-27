package org.homio.app.repository.crud.base;

import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.repository.PureRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface BaseCrudRepository<T extends HasEntityIdentifier>
        extends JpaRepository<T, Integer>, PureRepository<T> {}
