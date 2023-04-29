package de.tu_berlin.dos.arm.wordcount.processor;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.spark.metrics.source.Source;
import org.apache.spark.sql.streaming.StreamingQueryProgress;

/**
 * Metrics source for structured streaming query.
 */
public class StreamingQuerySource implements Source {
    private String appName;
    private MetricRegistry metricRegistry = new MetricRegistry();
    private final Progress progress = new Progress();

    public StreamingQuerySource(String appName) {
        this.appName = appName;
        registerGuage("batchId", () -> progress.batchId());
        registerGuage("numInputRows", () -> progress.numInputRows());
        registerGuage("inputRowsPerSecond", () -> progress.inputRowsPerSecond());
        registerGuage("processedRowsPerSecond", () -> progress.processedRowsPerSecond());
    }

    private <T> Gauge<T> registerGuage(String name, Gauge<T> metric) {
        return metricRegistry.register(MetricRegistry.name(name), metric);
    }

    @Override
    public String sourceName() {
        return String.format("%s.streaming", appName);
    }


    @Override
    public MetricRegistry metricRegistry() {
        return metricRegistry;
    }

    public void updateProgress(StreamingQueryProgress queryProgress) {
        progress.batchId(queryProgress.batchId())
                .numInputRows(queryProgress.numInputRows())
                .inputRowsPerSecond(queryProgress.inputRowsPerSecond())
                .processedRowsPerSecond(queryProgress.processedRowsPerSecond());
    }

    @Data
    @Accessors(fluent = true)
    private static class Progress {
        private long batchId = -1;
        private long numInputRows = 0;
        private double inputRowsPerSecond = 0;
        private double processedRowsPerSecond = 0;
    }
}
