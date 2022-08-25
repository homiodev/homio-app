package org.touchhome.app.model.entity.widget;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.touchhome.common.util.CommonUtils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface UIFieldLayout {

    String[] options();

    String rows() default "1:3";

    String columns() default "1:6";

    @Getter
    class LayoutBuilder {
        private final List<ColBuilder> c = new ArrayList<>();
        private final List<RowBuilder> r = new ArrayList<>();

        public static LayoutBuilder builder() {
            return new LayoutBuilder();
        }

        public LayoutBuilder addRow(Consumer<RowBuilder> rowBuilder) {
            RowBuilder builder = new RowBuilder();
            r.add(builder);
            rowBuilder.accept(builder);
            return this;
        }

        @SneakyThrows
        public String build() {
            Integer validateColumnSize = null;
            if (this.r.size() == 0) {
                throw new RuntimeException("Layout must have at least one row");
            }
            for (RowBuilder rowBuilder : this.r) {
                if (validateColumnSize == null) {
                    validateColumnSize = rowBuilder.cell.size();
                } else if (validateColumnSize != rowBuilder.cell.size()) {
                    throw new RuntimeException("Row columns must be equal");
                }
                for (int i = 0; i < validateColumnSize; i++) {
                    // hide next columns if colSPan > 1
                    for (int j = 1; j < rowBuilder.cell.get(i).collSpan; j++) {
                        rowBuilder.cell.get(i + j).collSpan = 0;
                    }
                    rowBuilder.cell.get(i).width = 100F * rowBuilder.cell.get(i).collSpan / validateColumnSize;
                }
            }
            for (int i = 0; i < validateColumnSize; i++) {
                c.add(new ColBuilder(100F / validateColumnSize));
            }
            return CommonUtils.OBJECT_MAPPER.writeValueAsString(this);
        }
    }

    @Getter
    @RequiredArgsConstructor
    class ColBuilder {
        private final float w;
    }

    @Getter
    class RowBuilder {
        private final List<Column> cell = new ArrayList<>();

        public RowBuilder addCol(Consumer<Column> columnConsumer) {
            Column column = new Column();
            columnConsumer.accept(column);
            cell.add(column);
            return this;
        }

        public RowBuilder addCol() {
            return addCol(column -> {});
        }

        public RowBuilder addCol(String value, HorizontalAlign horizontalAlign) {
            return addCol(column -> column.setValue(value).setHorizontalAlign(horizontalAlign));
        }
    }

    @Getter
    @Accessors(chain = true)
    @RequiredArgsConstructor
    class Column {
        @JsonProperty("w")
        private float width;
        @Setter
        @JsonProperty("v")
        private String value = "none";
        @Setter
        @JsonProperty("ha")
        private HorizontalAlign horizontalAlign = HorizontalAlign.left;
        @Setter
        @JsonProperty("va")
        private VerticalAlign verticalAlign = VerticalAlign.middle;
        @Setter
        @JsonProperty("cs")
        private int collSpan = 1;
    }

    enum VerticalAlign {
        top, middle, bottom
    }

    enum HorizontalAlign {
        left, center, right
    }
}
