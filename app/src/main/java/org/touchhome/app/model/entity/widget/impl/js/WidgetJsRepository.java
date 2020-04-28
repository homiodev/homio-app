package org.touchhome.app.model.entity.widget.impl.js;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WidgetJsRepository extends AbstractRepository<WidgetJsEntity> {

    public WidgetJsRepository() {
        super(WidgetJsEntity.class, "mbw_");
    }

    /* @Override
    @Transactional
    public WidgetLineChartEntity save(WidgetLineChartEntity entity) {
        WidgetLineChartEntity widgetLineChartEntity = super.save(entity);
        if (widgetLineChartEntity.getLineChartSeries() == null) {
            widgetLineChartEntity.setChartSeries(new ArrayList<>());
            widgetLineChartEntity.getLineChartSeries().add(createListItem(widgetLineChartEntity, 0));
        }
        return widgetLineChartEntity;
    }*/

    /*@Override
    @Transactional
    public void addListItemAfter(BaseEntity listOwner, BaseEntity insertAfterEntity) {
        boolean updateOrder = false;
        for (WidgetLineChartSeriesEntity chartSeriesEntity : chartSeriesRepository.getByOwner(listOwner)) {
            if (updateOrder) {
                chartSeriesEntity.setPriority(chartSeriesEntity.getPriority() + 1);
                chartSeriesRepository.save(chartSeriesEntity);

            } else if (Objects.equals(chartSeriesEntity.getCommandIndex(), insertAfterEntity.getCommandIndex())) {
                updateOrder = true;
                createListItem((WidgetLineChartEntity) listOwner, chartSeriesEntity.getPriority() + 1);
            }
        }
    }

    @Override
    @Transactional
    public void addListItemBefore(BaseEntity listOwner, BaseEntity insertBeforeEntity) {
        boolean updateOrder = false;
        for (WidgetLineChartSeriesEntity chartSeriesEntity : chartSeriesRepository.getByOwner(listOwner)) {
            if (updateOrder) {
                chartSeriesEntity.setPriority(chartSeriesEntity.getPriority() + 2);
                chartSeriesRepository.save(chartSeriesEntity);

            } else if (Objects.equals(chartSeriesEntity.getCommandIndex(), insertBeforeEntity.getCommandIndex())) {
                updateOrder = true;
                createListItem((WidgetLineChartEntity) listOwner, chartSeriesEntity.getPriority());

                chartSeriesEntity.setPriority(chartSeriesEntity.getPriority() + 1);
                chartSeriesRepository.save(chartSeriesEntity);
            }
        }
    }

    private WidgetLineChartSeriesEntity createListItem(WidgetLineChartEntity owner, int order) {
        WidgetLineChartSeriesEntity entity = new WidgetLineChartSeriesEntity();
        entity.setPriority(order);
        entity.setWidgetLineChartEntity(owner);
        return chartSeriesRepository.save(entity);
    }*/
}



