package de.tu_berlin.dos.arm.traffic_monitoring.common.utils;

import de.tu_berlin.dos.arm.traffic_monitoring.common.data.Observation;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.LinkedList;

public enum FileParser { GET;

    public static final Logger LOG = Logger.getLogger(FileParser.class);

    public LinkedList<Observation> csv(File csvFile, String sep, boolean header) throws Exception {

        LinkedList<Observation> records = new LinkedList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            boolean headerRead = false;
            while ((line = br.readLine()) != null) {
                if (header && !headerRead) {
                    headerRead = true;
                    continue;
                }
                String[] values = line.split(sep);
                records.add(new Observation(Long.parseLong(values[0]), Integer.parseInt(values[1])));
            }
        }
        return records;
    }
}
