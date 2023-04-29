package de.tu_berlin.dos.arm.traffic_monitoring.common.data;

import java.util.Collections;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Optional;

public class TimeSeries {

    public final LinkedList<Observation> observations;
    public final double frequency;

    public TimeSeries(LinkedList<Observation> observations, double frequency) {

        this.observations = observations;
        this.frequency = frequency;
    }

    public int size() {

        return this.observations.size();
    }

    public TimeSeries resample(int sampleRate, Optional<Integer> limit) {

        long last = this.observations.get(this.observations.size() - 1).timestamp;
        return resample(last, sampleRate, limit);
    }

    public TimeSeries resample(long timestamp, int sampleRate, Optional<Integer> limit) {

        // find timestamp in observations using binary search
        int i = Collections.binarySearch(observations, new Observation(timestamp, -1));

        LinkedList<Observation> result = new LinkedList<>();
        if (observations.get(i).timestamp == timestamp) {
            // calculate the number of samples in new time series
            int count = limit.orElse(i + 1);
            // iterate backwards of linked list from matching timestamp
            int j = 0;
            ListIterator<Observation> iterator = observations.listIterator(i + 1);
            while(iterator.hasPrevious() && result.size() < count) {
                // retrieve sample
                Observation obs = iterator.previous();
                // test if current sample index is within sample rate
                if (j % sampleRate == 0) {
                    // append valid sample to front of results
                    result.addFirst(obs);
                }
                ++j;
            }
        }
        return new TimeSeries(result, this.frequency / sampleRate);
    }

    public Observation getObservation(long timestamp) {

        int i = Collections.binarySearch(observations, new Observation(timestamp, -1));
        return observations.get(i);
    }

    public double[] values() {

        return this.observations.stream().mapToDouble(v -> v.value).toArray();
    }

    @Override
    public String toString() {
        return "TimeSeries{" +
                "observations=" + observations +
                ", frequency=" + frequency +
                ", count=" + observations.size() +
                '}';
    }
}
