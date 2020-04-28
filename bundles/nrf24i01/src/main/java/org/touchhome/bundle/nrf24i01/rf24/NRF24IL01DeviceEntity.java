package org.touchhome.bundle.nrf24i01.rf24;

import org.touchhome.bundle.api.model.DeviceBaseEntity;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.nrf24i01.rf24.options.*;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

@Entity
//@UISidebarMenu(icon = "fa-dot-circle", parent = UISidebarMenu.TopSidebarMenu.HARDWARE, itemType = NRF24IL01DeviceEntity.class, order = 4, allowCreateNewItems = false)
public class NRF24IL01DeviceEntity extends DeviceBaseEntity<NRF24IL01DeviceEntity> {

    @UIField(order = 12)
    @Enumerated(EnumType.STRING)
    private CRCSize crcSize;

    @UIField(order = 13)
    @Enumerated(EnumType.STRING)
    private DataRate dataRate;

    @UIField(order = 14)
    @Enumerated(EnumType.STRING)
    private PALevel paLevel;

    @UIField(order = 15)
    @Enumerated(EnumType.STRING)
    private RetryCount retryCount;

    @UIField(order = 16)
    @Enumerated(EnumType.STRING)
    private RetryDelay retryDelay;

    public CRCSize getCrcSize() {
        return crcSize;
    }

    public void setCrcSize(CRCSize crcSize) {
        this.crcSize = crcSize;
    }

    public PALevel getPaLevel() {
        return paLevel;
    }

    public void setPaLevel(PALevel paLevel) {
        this.paLevel = paLevel;
    }

    public DataRate getDataRate() {
        return dataRate;
    }

    public void setDataRate(DataRate dataRate) {
        this.dataRate = dataRate;
    }

    public RetryCount getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(RetryCount retryCount) {
        this.retryCount = retryCount;
    }

    public RetryDelay getRetryDelay() {
        return retryDelay;
    }

    public void setRetryDelay(RetryDelay retryDelay) {
        this.retryDelay = retryDelay;
    }

    @Override
    public String getShortTitle() {
        return "NRF24I01";
    }
}
