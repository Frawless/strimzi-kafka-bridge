/*
 * Copyright 2016, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.strimzi.kafka.bridge.amqp.converter;

import io.strimzi.kafka.bridge.amqp.AmqpBridge;
import io.strimzi.kafka.bridge.converter.DefaultSerializer;
import io.strimzi.kafka.bridge.converter.MessageConverter;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.consumer.KafkaConsumerRecords;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import org.apache.qpid.proton.Proton;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.message.Message;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation class for the message conversion
 * between Kafka record and AMQP message
 */
public class AmqpDefaultMessageConverter implements MessageConverter<String, byte[], Message, Collection<Message>> {

    @Override
    public KafkaProducerRecord<String, byte[]> toKafkaRecord(String kafkaTopic, Integer partition, Message message) {

        Object partitionFromMessage = null, key = null;
        byte[] value = null;

        // get topic and body from AMQP message
        String topic = (message.getAddress() == null) ?
                kafkaTopic :
                message.getAddress().replace('/', '.');

        Section body = message.getBody();

        // check body null
        if (body != null) {

            // section is AMQP value
            if (body instanceof AmqpValue) {

                Object amqpValue = ((AmqpValue) body).getValue();

                // encoded as String
                if (amqpValue instanceof String) {
                    String content = (String) ((AmqpValue) body).getValue();
                    value = content.getBytes(StandardCharsets.UTF_8);
                // encoded as a List
                } else if (amqpValue instanceof List) {
                    List<?> list = (List<?>) ((AmqpValue) body).getValue();
                    DefaultSerializer<List<?>> serializer = new DefaultSerializer<>();
                    value = serializer.serialize(kafkaTopic, list);
                // encoded as an array
                } else if (amqpValue.getClass().isArray()) {
                    DefaultSerializer serializer = new DefaultSerializer();
                    value = serializer.serialize(kafkaTopic, amqpValue);
                // encoded as a Map
                } else if (amqpValue instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) ((AmqpValue) body).getValue();
                    DefaultSerializer<Map<?, ?>> serializer = new DefaultSerializer<>();
                    value = serializer.serialize(kafkaTopic, map);
                }

            // section is Data (binary)
            } else if (body instanceof Data) {
                Binary binary = ((Data) body).getValue();
                value = binary.getArray();
            }
        }

        // get partition and key from AMQP message annotations
        // NOTE : they are not mandatory
        MessageAnnotations messageAnnotations = message.getMessageAnnotations();

        if (messageAnnotations != null) {

            partitionFromMessage = messageAnnotations.getValue().get(Symbol.getSymbol(AmqpBridge.AMQP_PARTITION_ANNOTATION));
            key = messageAnnotations.getValue().get(Symbol.getSymbol(AmqpBridge.AMQP_KEY_ANNOTATION));

            if (partitionFromMessage != null && !(partitionFromMessage instanceof Integer))
                throw new IllegalArgumentException("The partition annotation must be an Integer");

            if (key != null && !(key instanceof String))
                throw new IllegalArgumentException("The key annotation must be a String");
        }

        // build the record for the KafkaProducer and then send it
        KafkaProducerRecord<String, byte[]> record = KafkaProducerRecord.create(topic, (String) key, value, (Integer) partitionFromMessage);
        return record;
    }

    @Override
    public Message toMessage(String address, KafkaConsumerRecord<String, byte[]> record) {

        Message message = Proton.message();
        message.setAddress(address);

        // put message annotations about partition, offset and key (if not null)
        Map<Symbol, Object> map = new HashMap<>();
        map.put(Symbol.valueOf(AmqpBridge.AMQP_PARTITION_ANNOTATION), record.partition());
        map.put(Symbol.valueOf(AmqpBridge.AMQP_OFFSET_ANNOTATION), record.offset());
        map.put(Symbol.valueOf(AmqpBridge.AMQP_KEY_ANNOTATION), record.key());
        map.put(Symbol.valueOf(AmqpBridge.AMQP_TOPIC_ANNOTATION), record.topic());

        MessageAnnotations messageAnnotations = new MessageAnnotations(map);
        message.setMessageAnnotations(messageAnnotations);

        message.setBody(new Data(new Binary(record.value())));

        return message;
    }

    @Override
    public Collection<Message> toMessages(KafkaConsumerRecords<String, byte[]> records) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<KafkaProducerRecord<String, byte[]>> toKafkaRecords(String kafkaTopic, Integer partition, Collection<Message> messages) {
        throw new UnsupportedOperationException();
    }
}
