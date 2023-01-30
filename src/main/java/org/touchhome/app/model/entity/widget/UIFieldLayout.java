package org.touchhome.app.model.entity.widget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.touchhome.common.util.CommonUtils;

@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface UIFieldLayout {

    String[] options();

    String rows() default "1:3";

    String columns() default "1:6";

    enum VerticalAlign {
        top,
        middle,
        bottom
    }

    enum HorizontalAlign {
        left,
        center,
        right
    }

    @Getter
    class LayoutBuilder {

        private final List<ColBuilder> c = new ArrayList<>();
        private final List<RowBuilder> r = new ArrayList<>();
        private final float[] columnWidthInPercent;

        public LayoutBuilder(float[] columnWidthInPercent) {
            float sum = 0;
            for (float width : columnWidthInPercent) {
                if (width < 10F) {
                    throw new IllegalArgumentException("Width must be at least 10%");
                }
                sum += width;
            }
            if (sum > 100F) {
                throw new IllegalArgumentException("Unable to set summ of column width > 100");
            }
            this.columnWidthInPercent = columnWidthInPercent;
        }

        /**
         * Specify column width Restrictions: sum(columnWidthInPercent) must be 100. columnWidthInPercent[i] must be >= 10%
         */
        public static LayoutBuilder builder(float... columnWidthInPercent) {
            return new LayoutBuilder(columnWidthInPercent);
        }

        public LayoutBuilder addRow(Consumer<RowBuilder> rowBuilder) {
            RowBuilder builder = new RowBuilder();
            r.add(builder);
            rowBuilder.accept(builder);
            return this;
        }

        @SneakyThrows
        public String build() {
            if (this.r.size() == 0) {
                throw new RuntimeException("Layout must have at least one row");
            }
            for (float width : this.columnWidthInPercent) {
                c.add(new ColBuilder(width));
            }
            for (RowBuilder rowBuilder : this.r) {
                buildRow(rowBuilder);
            }
            return CommonUtils.OBJECT_MAPPER.writeValueAsString(this);
        }

        private void buildRow(RowBuilder rowBuilder) {
            if (this.columnWidthInPercent.length != rowBuilder.getCell().size()) {
                throw new RuntimeException("Row columns must be equal to specified column size: " + this.columnWidthInPercent.length);
            }
            for (int i = 0; i < this.columnWidthInPercent.length; i++) {
                // hide next columns if colSPan > 1
                for (int j = 1; j < rowBuilder.cell.get(i).collSpan; j++) {
                    rowBuilder.cell.get(i + j).collSpan = 0;
                }
                int width = 0;
                for (int w = i; w < i + rowBuilder.cell.get(i).collSpan; w++) {
                    width += this.columnWidthInPercent[w];
                }

                rowBuilder.cell.get(i).width = width;
            }
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

        public RowBuilder addCol(String value, HorizontalAlign horizontalAlign) {
            return addCol(value, horizontalAlign, 1);
        }

        public RowBuilder addCol(String value, HorizontalAlign horizontalAlign, int colSpan) {
            RowBuilder rowBuilder = addCol(column -> column.setValue(value).setHorizontalAlign(horizontalAlign).setCollSpan(colSpan));
            for (int i = 1; i < colSpan; i++) {
                addCol(column -> {});
            }
            return rowBuilder;
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
}
