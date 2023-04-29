package de.tu_berlin.dos.arm.traffic_monitoring.common.data;

public class Observation implements Comparable<Observation> {

    public final long timestamp;
    public final int value;

    public Observation(long timestamp, int value) {

        this.timestamp = timestamp;
        this.value = value;
    }

    @Override
    public String toString() {
        return "Observation{" +
                "timestamp=" + timestamp +
                ", value=" + value +
                '}';
    }

    @Override
    public int compareTo(Observation that) {

        return Long.compare(this.timestamp, that.timestamp);
    }
}
