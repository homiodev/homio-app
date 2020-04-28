package org.touchhome.bundle.nrf24i01.rf24.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.repository.AbstractDeviceRepository;
import org.touchhome.bundle.nrf24i01.rf24.NRF24IL01DeviceEntity;
import org.touchhome.bundle.nrf24i01.rf24.RF24Service;

@Repository
public class NRF24I01DeviceRepository extends AbstractDeviceRepository<NRF24IL01DeviceEntity> {

    @Autowired
    private RF24Service rf24Service;

    public NRF24I01DeviceRepository() {
        super(NRF24IL01DeviceEntity.class, "nrf24_");
    }

    @Override
    public NRF24IL01DeviceEntity save(NRF24IL01DeviceEntity entity) {
        NRF24IL01DeviceEntity oldEntity = getByEntityID(entity.getEntityID());
        if (oldEntity != null) {
            updateNRF24I01(oldEntity, entity);
        }
        return super.save(entity);
    }

    private void updateNRF24I01(NRF24IL01DeviceEntity oldEntity, NRF24IL01DeviceEntity newEntity) {
        if (oldEntity.getCrcSize() != newEntity.getCrcSize()) {
            rf24Service.updateCRCSize(newEntity.getCrcSize());
        }
        if (oldEntity.getDataRate() != newEntity.getDataRate()) {
            rf24Service.updateDataRate(newEntity.getDataRate());
        }
        if (oldEntity.getPaLevel() != newEntity.getPaLevel()) {
            rf24Service.updatePALevel(newEntity.getPaLevel());
        }
        if (oldEntity.getRetryCount() != newEntity.getRetryCount() || oldEntity.getRetryDelay() != newEntity.getRetryDelay()) {
            rf24Service.updateRetry(newEntity.getRetryCount(), newEntity.getRetryDelay());
        }
    }
}



































