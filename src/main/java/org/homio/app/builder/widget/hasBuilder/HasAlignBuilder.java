package org.homio.app.builder.widget.hasBuilder;

import org.homio.api.ContextWidget.HasAlign;
import org.homio.api.ContextWidget.HorizontalAlign;
import org.homio.api.ContextWidget.VerticalAlign;

public interface HasAlignBuilder<T extends org.homio.app.model.entity.widget.attributes.HasAlign, R>
  extends HasAlign<R> {

  T getWidget();

  @Override
  default R setAlign(HorizontalAlign horizontalAlign, VerticalAlign verticalAlign) {
    getWidget().setAlign(String.format("%sx%s", horizontalAlign.ordinal() + 1, verticalAlign.ordinal() + 1));
    return (R) this;
  }
}
