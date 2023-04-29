package de.tu_berlin.dos.arm.wordcount.processor;

import com.fasterxml.jackson.annotation.JsonFormat;

public class LineEvent {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone="UTC")
    private Long ts;
    private String line;

    public LineEvent(Long ts, String id, String et) {
        this.ts = ts;
        this.line = line;
    }

    public Long getTs() {
        return ts;
    }

    @Override
    public String toString() {
        return "{ts:" + ts +
                ", line:" + line +
                '}';
    }
}
