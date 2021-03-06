/**
 * DingDing.com Inc.
 * Copyright (c) 2000-2016 All Rights Reserved.
 */
package com.dingding.open.achelous.example.old;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import kafka.consumer.ConsumerConfig;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import kafka.javaapi.consumer.ConsumerConnector;

/**
 * TODO will be deleted soon
 * 
 * @author surlymo
 * @date Oct 27, 2015
 */
public class OldConsumerDemo {
    private final ConsumerConnector consumer;
    private final String topic;
    private ExecutorService executor;

    public OldConsumerDemo(String a_zookeeper, String a_groupId, String a_topic) {
        consumer = kafka.consumer.Consumer.createJavaConsumerConnector(
                createConsumerConfig(a_zookeeper, a_groupId));
        this.topic = a_topic;
    }

    public void shutdown() {
        if (consumer != null)
            consumer.shutdown();
        if (executor != null)
            executor.shutdown();
        try {
            if (!executor.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
                System.out.println("Timed out waiting for consumer threads to shut down, exiting uncleanly");
            }
        } catch (InterruptedException e) {
            System.out.println("Interrupted during shutdown, exiting uncleanly");
        }
    }

    public void run(int a_numThreads) {
        Map<String, Integer> topicCountMap = new HashMap<String, Integer>();
        topicCountMap.put(topic, new Integer(a_numThreads));
        Map<String, List<KafkaStream<byte[], byte[]>>> consumerMap = consumer.createMessageStreams(topicCountMap);
        List<KafkaStream<byte[], byte[]>> streams = consumerMap.get(topic);

        // now launch all the threads
        //
        executor = Executors.newFixedThreadPool(a_numThreads);

        // now create an object to consume the messages
        //
        int threadNumber = 0;
        for (final KafkaStream stream : streams) {
            System.out.println(stream.toString());
            executor.execute(new ConsumerTest(stream, threadNumber));
            System.out.println(threadNumber);
            threadNumber++;
        }
    }

    private static ConsumerConfig createConsumerConfig(String a_zookeeper, String a_groupId) {
        Properties props = new Properties();
        props.put("zookeeper.connect", a_zookeeper);
        props.put("group.id", a_groupId);
        props.put("zookeeper.session.timeout.ms", "400");
        props.put("zookeeper.sync.time.ms", "200");
        props.put("auto.commit.interval.ms", "1000");

        return new ConsumerConfig(props);
    }

    public static void main(String[] args) throws Exception {

        // Properties prop = new Properties();
        // InputStream input = ClassLoader.getSystemResourceAsStream("seda.properties");
        // System.out.println(input);
        // prop.load(input);

        // String zooKeeper = (String) prop.get("kafka.consumer.zkconfig");
        // String groupId = (String) prop.get("kafka.consumer.group.id");
        // String topic = (String) prop.get("kafka.consumer.from");
        // int threads = Integer.valueOf(prop.get("kafka.consumer.streams").toString());

        String zooKeeper = "192.168.200.128:2181";
        String groupId = "consumer-seda-prop";
        String topic = "my-replicated-topic";
        int threads = 4;

        OldConsumerDemo example = new OldConsumerDemo(zooKeeper, groupId, topic);
        example.run(threads);

        try {
            Thread.sleep(1000000);
        } catch (InterruptedException ie) {

        }

        // example.shutdown();
    }

    public class ConsumerTest implements Runnable {
        private KafkaStream m_stream;
        private int m_threadNumber;

        public ConsumerTest(KafkaStream a_stream, int a_threadNumber) {
            m_threadNumber = a_threadNumber;
            m_stream = a_stream;
        }

        public void run() {
            System.out.println("Thread " + m_threadNumber + ": ");
            System.out.println("=====");
            ConsumerIterator<byte[], byte[]> it = m_stream.iterator();

            System.out.println("Thread " + m_threadNumber + ": " + it.hasNext());
            System.out.println("----------");
            while (it.hasNext())
                System.out.println("Thread " + m_threadNumber + ": " + new String(it.next().message()));
            System.out.println("Shutting down Thread: " + m_threadNumber);
        }
    }
}
