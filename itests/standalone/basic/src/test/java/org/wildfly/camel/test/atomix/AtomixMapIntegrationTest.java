/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.camel.test.atomix;

import java.util.Collections;
import java.util.UUID;

import org.apache.camel.CamelContext;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.atomix.AtomixHelper;
import org.apache.camel.component.atomix.client.AtomixClientConstants;
import org.apache.camel.component.atomix.client.map.AtomixMap;
import org.apache.camel.component.atomix.client.map.AtomixMapComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.camel.test.common.utils.AvailablePortFinder;
import org.wildfly.extension.camel.CamelAware;

import io.atomix.AtomixClient;
import io.atomix.AtomixReplica;
import io.atomix.catalyst.transport.Address;
import io.atomix.collections.DistributedMap;

@CamelAware
@RunWith(Arquillian.class)
public class AtomixMapIntegrationTest {

    private static final String MAP_NAME = UUID.randomUUID().toString();

    private Address replicaAddress;
    private AtomixReplica replica;
    private AtomixClient client;
    private DistributedMap<Object, Object> map;

    @Deployment
    public static JavaArchive createdeployment() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "atomix-map-tests");
        archive.addClasses(AvailablePortFinder.class);
        return archive;
    }

    @Before
    public void before() throws Exception {
        replicaAddress = AtomixFactory.address("127.0.0.1");
        replica = AtomixFactory.replica(replicaAddress);
        client = AtomixFactory.client(replicaAddress);
        map = client.getMap(MAP_NAME).join();
    }

    @After
    public void after() throws Exception {
        if (map != null) {
            map.close();
        }
        if (client != null) {
            client.close().join();
            client = null;
        }
        if (replica != null) {
            replica.shutdown().join();
            replica.leave().join();
            replica = null;
        }
    }

    @Test
    public void testPutAndGet() throws Exception {

        CamelContext camelctx = new DefaultCamelContext();
        camelctx.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").toF("atomix-map:%s", MAP_NAME);
            }
        });

        final String key = camelctx.getUuidGenerator().generateUuid();
        final String val = camelctx.getUuidGenerator().generateUuid();

        AtomixMapComponent component = camelctx.getComponent("atomix-map", AtomixMapComponent.class);
        component.setNodes(Collections.singletonList(replicaAddress));

        camelctx.start();
        try {
            Message result;

            FluentProducerTemplate fluent = camelctx.createFluentProducerTemplate().to("direct:start");

            result = fluent.clearAll()
                    .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMap.Action.PUT)
                    .withHeader(AtomixClientConstants.RESOURCE_KEY, key)
                    .withBody(val)
                    .request(Message.class);

            Assert.assertFalse(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
            Assert.assertEquals(val, result.getBody());
            Assert.assertEquals(val, map.get(key).join());

            result = fluent.clearAll()
                    .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMap.Action.GET)
                    .withHeader(AtomixClientConstants.RESOURCE_KEY, key)
                    .request(Message.class);

            Assert.assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
            Assert.assertEquals(val, result.getBody(String.class));
            Assert.assertTrue(map.containsKey(key).join());
        } finally {
            camelctx.stop();
        }

    }

    static class AtomixFactory {

        static Address address(String host) {
            return new Address(host, AvailablePortFinder.getNextAvailable());
        }

        static AtomixReplica replica(Address address) {
            AtomixReplica replica = AtomixReplica.builder(address).withStorage(AtomixHelper.inMemoryStorage()).build();
            replica.bootstrap().join();
            return replica;
        }

        static AtomixClient client(Address address) {
            AtomixClient client = AtomixClient.builder().build();
            client.connect(address).join();
            return client;
        }
    }
}
