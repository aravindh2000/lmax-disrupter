package kafka.consumer;

import java.util.Properties;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.*;
import org.rocksdb.RocksDB;
import org.rocksdb.Options;


public class ConsumerWithState {

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "change-tracker-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.STATE_DIR_CONFIG, "/tmp/kafka-rocksdb-state"); // RocksDB store location

        StreamsBuilder builder = new StreamsBuilder();
        KTable<String, String> records = builder.table("data-stream", Materialized.as("rocksdb-store"));

        // Detect changes for key "124379248"
        records.toStream()
                .filter((key, value) -> key.equals("124379248"))
                .peek((key, value) -> System.out.println("Change detected: " + key + " -> " + value));

        KafkaStreams streams = new KafkaStreams(builder.build(), props);
        streams.start();

        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));

    }
}
