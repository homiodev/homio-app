package org.touchhome.app.repository.crud.base;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.touchhome.bundle.api.model.PureEntity;
import org.touchhome.bundle.api.repository.PureRepository;

@NoRepositoryBean
public interface BaseCrudRepository<T extends PureEntity> extends JpaRepository<T, Integer>, PureRepository<T> {

}
