package de.tu_berlin.dos.arm.yahoo_streaming_benchmark.processor;

import com.fasterxml.jackson.annotation.JsonFormat;

public class AdEvent {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone="UTC")
    private Long ts;
    private String id;
    private String et;

    public AdEvent(Long ts, String id, String et) {
        this.ts = ts;
        this.id = id;
        this.et = et;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEt() {
        return et;
    }

    public void setEt(String et) {
        this.et = et;
    }

    public Long getTs() {
        return ts;
    }

    public void setTs(Long ts) {
        this.ts = ts;
    }

    @Override
    public String toString() {
        return "{ts:" + ts +
                ", id:" + id +
                ", et:" + et +
                '}';
    }
}
