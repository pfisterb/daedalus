package de.tu_berlin.dos.arm.yahoo_streaming_benchmark.processor;

import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.typeutils.TypeExtractor;
import org.apache.log4j.Logger;
import org.json.JSONObject;

public class AdEventSchema implements DeserializationSchema<AdEvent>, SerializationSchema<AdEvent> {

    private static final Logger LOG = Logger.getLogger(AdEventSchema.class);

    private static AdEvent fromString(String input) {
        JSONObject obj = new JSONObject(input);
        Long ts = Long.valueOf(obj.getString("ts"));
        AdEvent adEvent =
            new AdEvent(
                ts,
                obj.getString("id"),
                obj.getString("et"));
        return adEvent;
    }

    @Override
    public AdEvent deserialize(byte[] message) {
        return fromString(new String(message));
    }

    @Override
    public boolean isEndOfStream(AdEvent nextElement) {
        return false;
    }

    @Override
    public TypeInformation<AdEvent> getProducedType() {
        return TypeExtractor.getForClass(AdEvent.class);
    }

    @Override
    public byte[] serialize(AdEvent adEvent) {
        return adEvent.toString().getBytes();
    }

}
