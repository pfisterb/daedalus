package de.tu_berlin.dos.arm.wordcount.processor;

import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.typeutils.TypeExtractor;
import org.apache.log4j.Logger;
import org.json.JSONObject;

public class LineEventSchema implements DeserializationSchema<LineEvent>, SerializationSchema<LineEvent> {

    private static final Logger LOG = Logger.getLogger(LineEventSchema.class);

    private static LineEvent fromString(String input) {
        JSONObject obj = new JSONObject(input);
        Long ts = Long.valueOf(obj.getString("ts"));
        LineEvent lineEvent =
            new LineEvent(
                ts,
                obj.getString("id"),
                obj.getString("et"));
        return lineEvent;
    }

    @Override
    public LineEvent deserialize(byte[] message) {
        return fromString(new String(message));
    }

    @Override
    public boolean isEndOfStream(LineEvent nextElement) {
        return false;
    }

    @Override
    public TypeInformation<LineEvent> getProducedType() {
        return TypeExtractor.getForClass(LineEvent.class);
    }

    @Override
    public byte[] serialize(LineEvent lineEvent) {
        return lineEvent.toString().getBytes();
    }

}
