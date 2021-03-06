/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.queue.rabbitmq;

import java.nio.charset.StandardCharsets;

import org.apache.james.util.Runnables;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.testcontainers.containers.Network;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;
import com.rabbitmq.client.Address;

public class DockerClusterRabbitMQExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    public static final String RABBIT_1 = "rabbit1";
    public static final String RABBIT_2 = "rabbit2";
    public static final String RABBIT_3 = "rabbit3";
    private DockerRabbitMQCluster cluster;
    private Network network;

    @Override
    public void beforeEach(ExtensionContext context) {
        String cookie = Hashing.sha256().hashString("secret cookie here", StandardCharsets.UTF_8).toString();

        network = Network.NetworkImpl.builder()
            .enableIpv6(false)
            .createNetworkCmdModifiers(ImmutableList.of())
            .build();

        DockerRabbitMQ rabbitMQ1 = DockerRabbitMQ.withCookieAndNodeName(RABBIT_1, cookie, "rabbit@rabbit1", network);
        DockerRabbitMQ rabbitMQ2 = DockerRabbitMQ.withCookieAndNodeName(RABBIT_2, cookie, "rabbit@rabbit2", network);
        DockerRabbitMQ rabbitMQ3 = DockerRabbitMQ.withCookieAndNodeName(RABBIT_3, cookie, "rabbit@rabbit3", network);

        Runnables.runParallel(
            rabbitMQ1::start,
            rabbitMQ2::start,
            rabbitMQ3::start);

        Runnables.runParallel(
            Throwing.runnable(() -> rabbitMQ2.join(rabbitMQ1)),
            Throwing.runnable(() -> rabbitMQ3.join(rabbitMQ1)));



        Runnables.runParallel(
            Throwing.runnable(rabbitMQ2::startApp),
            Throwing.runnable(rabbitMQ3::startApp));

        cluster = new DockerRabbitMQCluster(rabbitMQ1, rabbitMQ2, rabbitMQ3);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        cluster.stop();
        network.close();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == DockerRabbitMQCluster.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return cluster;
    }

    public static class DockerRabbitMQCluster {

        private final DockerRabbitMQ rabbitMQ1;
        private final DockerRabbitMQ rabbitMQ2;
        private final DockerRabbitMQ rabbitMQ3;

        public DockerRabbitMQCluster(DockerRabbitMQ rabbitMQ1, DockerRabbitMQ rabbitMQ2, DockerRabbitMQ rabbitMQ3) {
            this.rabbitMQ1 = rabbitMQ1;
            this.rabbitMQ2 = rabbitMQ2;
            this.rabbitMQ3 = rabbitMQ3;
        }

        public void stop() {
            Runnables.runParallel(
                Throwing.runnable(rabbitMQ1::stop).orDoNothing(),
                Throwing.runnable(rabbitMQ2::stop).orDoNothing(),
                Throwing.runnable(rabbitMQ3::stop).orDoNothing());
        }

        public DockerRabbitMQ getRabbitMQ1() {
            return rabbitMQ1;
        }

        public DockerRabbitMQ getRabbitMQ2() {
            return rabbitMQ2;
        }

        public DockerRabbitMQ getRabbitMQ3() {
            return rabbitMQ3;
        }

        public ImmutableList<Address> getAddresses() {
            return ImmutableList.of(
                new Address(rabbitMQ1.getHostIp(), rabbitMQ1.getPort()),
                new Address(rabbitMQ2.getHostIp(), rabbitMQ2.getPort()),
                new Address(rabbitMQ3.getHostIp(), rabbitMQ3.getPort()));
        }
    }
}
