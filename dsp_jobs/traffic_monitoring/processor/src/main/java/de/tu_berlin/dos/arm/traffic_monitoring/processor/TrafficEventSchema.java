package de.tu_berlin.dos.arm.traffic_monitoring.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import de.tu_berlin.dos.arm.traffic_monitoring.common.events.TrafficEvent;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.typeutils.TypeExtractor;
import org.apache.log4j.Logger;

import java.io.IOException;

public class TrafficEventSchema implements DeserializationSchema<TrafficEvent>, SerializationSchema<TrafficEvent> {

    private static final Logger LOG = Logger.getLogger(TrafficEventSchema.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    static {
        MAPPER.registerModule(new JodaModule());
    }

    private static TrafficEvent fromString(String line) {
        String[] tokens = line.split(",");
        // 4 commas for TrafficEvent + 1 for Point
        if (tokens.length != 4) {
            throw new RuntimeException("Invalid record: " + line);
        }
        try {
            return MAPPER.readValue(line, TrafficEvent.class);
        }
        catch (IOException ex) {
            LOG.error("Mapping error: " + ex.getMessage());
            LOG.error(ex.getStackTrace());
        }
        return null;
    }

    @Override
    public TrafficEvent deserialize(byte[] message) {
        return fromString(new String(message));
    }

    @Override
    public boolean isEndOfStream(TrafficEvent nextElement) {
        return false;
    }

    @Override
    public TypeInformation<TrafficEvent> getProducedType() {
        return TypeExtractor.getForClass(TrafficEvent.class);
    }

    @Override
    public byte[] serialize(TrafficEvent trafficEvent) {
        return trafficEvent.toString().getBytes();
    }
}
