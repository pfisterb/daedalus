package de.tu_berlin.dos.arm.wordcount.processor;

import de.tu_berlin.dos.arm.wordcount.common.utils.FileReader;
import org.apache.spark.SparkEnv;
import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.sql.*;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.apache.spark.sql.streaming.StreamingQueryListener;

import java.util.Arrays;
import java.util.Properties;


public final class WordCount {

    public static void main(String[] args) throws Exception {

        // retrieve properties from file
        Properties props = FileReader.GET.read("wc.properties", Properties.class);

        SparkSession spark = SparkSession
                .builder()
                .appName("JavaStructuredNetworkWordCount")
                .getOrCreate();
        spark.conf().set("spark.sql.streaming.metricsEnabled", "true");

        StreamingQuerySource querySource = new StreamingQuerySource(spark.sparkContext().appName());
        SparkEnv.get().metricsSystem().registerSource(querySource);


        spark.streams().addListener(new StreamingQueryListener() {
            @Override
            public void onQueryStarted(QueryStartedEvent queryStarted) {
                System.out.println("Query started: " + queryStarted.id());
            }
            @Override
            public void onQueryTerminated(QueryTerminatedEvent queryTerminated) {
                System.out.println("Query terminated: " + queryTerminated.id());
            }
            @Override
            public void onQueryProgress(QueryProgressEvent queryProgress) {
                System.out.println("Query made progress: " + queryProgress.progress());
                querySource.updateProgress(queryProgress.progress());
            }
        });

        // Create DataFrame representing the stream of input lines from connection to host:port
        Dataset<Row> lines = spark
                .readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", props.getProperty("kafka.brokers"))
                .option("subscribe", props.getProperty("kafka.consumer.topic"))
                .load()
                .selectExpr("CAST(value AS STRING)");



        // Split the lines into words
        Dataset<String> words = lines.as(Encoders.STRING()).flatMap(
                (FlatMapFunction<String, String>) x -> Arrays.asList(x.split(" ")).iterator(),
                Encoders.STRING());

        // Generate running word count
        Dataset<Row> wordCounts = words.groupBy("value").count();

        // Start running the query that prints the running counts to the console
        StreamingQuery query = wordCounts.writeStream()
                .outputMode("complete")
                .option("checkpointLocation", props.getProperty("hdfs.backupFolder"))
                .format("console")
                .start();

        query.awaitTermination();
    }


}
