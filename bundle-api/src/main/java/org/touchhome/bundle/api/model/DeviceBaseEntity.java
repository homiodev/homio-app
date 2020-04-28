package org.touchhome.bundle.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.api.ui.PublicJsMethod;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.ui.method.UIFieldSelectValueOnEmpty;

import javax.persistence.*;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "device_discriminator")
@UISidebarMenu(icon = "fas fa-shapes", parent = UISidebarMenu.TopSidebarMenu.HARDWARE, bg = "#51145e")
@NoArgsConstructor
@Accessors(chain = true)
public abstract class DeviceBaseEntity<T extends DeviceBaseEntity> extends BaseEntity<T> {

    @UIField(readOnly = true, order = 100)
    @Getter(onMethod_ = {@PublicJsMethod})
    private String ieeeAddress;

    @Setter(onMethod_ = {@PublicJsMethod})
    @Getter(onMethod_ = {@PublicJsMethod})
    @ManyToOne(fetch = FetchType.LAZY)
    @UIField(order = 11, type = UIFieldType.Selection)
    @UIFieldSelectValueOnEmpty(label = "SELECT_PLACE", color = "#748994", method = "selectPlace")
    private PlaceEntity ownerPlace;

    public String getShortTitle() {
        return "";
    }

    @Override
    public String refreshName() {
        return getShortTitle();
    }

    public List<Option> selectPlace(EntityContext entityContext) {
        List<PlaceEntity> entities = entityContext.findAll(PlaceEntity.class);
        return entities.stream().map(p -> Option.of(p.getEntityID(), p.getTitle())).collect(Collectors.toList());
    }

    @Override
    public void getAllRelatedEntities(Set<BaseEntity> set) {
        set.add(ownerPlace);
    }

    /**
     * Define order in which entity will be shown on UI map
     */
    @PublicJsMethod
    public int getOrder() {
        return 100;
    }

    @PublicJsMethod
    public T setIeeeAddress(String ieeeAddress) {
        this.ieeeAddress = ieeeAddress;
        return (T) this;
    }
}
