package com.spark.falcon.shared.export;

import java.io.IOException;

@FunctionalInterface
public interface ExportRowSource {
    void forEach(RowConsumer consumer) throws IOException;

    @FunctionalInterface
    interface RowConsumer {
        void accept(Object... values) throws IOException;
    }
}
