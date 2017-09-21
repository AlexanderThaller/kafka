/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kafka.common.security.authenticator;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.internals.BrokerSecurityConfigs;
import org.apache.kafka.common.errors.AuthenticationFailedException;
import org.apache.kafka.common.network.ListenerName;
import org.apache.kafka.common.network.NetworkTestUtils;
import org.apache.kafka.common.network.NioEchoServer;
import org.apache.kafka.common.protocol.SecurityProtocol;
import org.apache.kafka.common.security.TestSecurityConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ClientAuthenticationFailureTest {

    private NioEchoServer server;
    private Map<String, Object> saslServerConfigs;
    private Map<String, Object> saslClientConfigs;
    private final String topic = "test";
    private TestJaasConfig testJaasConfig;

    @Before
    public void setup() throws Exception {
        SecurityProtocol securityProtocol = SecurityProtocol.SASL_PLAINTEXT;

        saslServerConfigs = new HashMap<>();
        saslServerConfigs.put(BrokerSecurityConfigs.SASL_ENABLED_MECHANISMS_CONFIG, Arrays.asList("PLAIN"));

        saslClientConfigs = new HashMap<>();
        saslClientConfigs.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT");
        saslClientConfigs.put(SaslConfigs.SASL_MECHANISM, "PLAIN");

        testJaasConfig = TestJaasConfig.createConfiguration("PLAIN", Arrays.asList("PLAIN"));
        testJaasConfig.setClientOptions("PLAIN", TestJaasConfig.USERNAME, "anotherpassword");
        server = createEchoServer(securityProtocol);
    }

    @After
    public void teardown() throws Exception {
        if (server != null)
            server.close();
    }

    @Test
    public void testConsumerWithInvalidCredentials() {
        saslClientConfigs.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:" + server.port());
        saslClientConfigs.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        saslClientConfigs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(saslClientConfigs)) {
            consumer.subscribe(Arrays.asList(topic));
            consumer.poll(100);
            fail("Expected an authentication error!");
        } catch (AuthenticationFailedException e) {
            // OK
        } catch (Exception e) {
            fail("Expected only an authentication error, but another error occurred: " + e.getMessage());
        }
    }

    @Test
    public void testProducerWithInvalidCredentials() {
        saslClientConfigs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:" + server.port());
        saslClientConfigs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        saslClientConfigs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");

        ProducerRecord<String, String> record = new ProducerRecord<>(topic, "message");
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(saslClientConfigs)) {
            producer.send(record).get();
            fail("Expected an authentication error!");
        } catch (Exception e) {
            assertTrue("Expected an exception of type AuthenticationFailedException", e.getCause() instanceof AuthenticationFailedException);
        }
    }

    private NioEchoServer createEchoServer(SecurityProtocol securityProtocol) throws Exception {
        return createEchoServer(ListenerName.forSecurityProtocol(securityProtocol), securityProtocol);
    }

    private NioEchoServer createEchoServer(ListenerName listenerName, SecurityProtocol securityProtocol) throws Exception {
        return NetworkTestUtils.createEchoServer(listenerName, securityProtocol,
                new TestSecurityConfig(saslServerConfigs), new CredentialCache());
    }
}