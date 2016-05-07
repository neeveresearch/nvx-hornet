/**
 * Copyright 2016 Neeve Research, LLC
 *
 * This product includes software developed at Neeve Research, LLC
 * (http://www.neeveresearch.com/) as well as software licenced to
 * Neeve Research, LLC under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Neeve Research licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.neeve.toa.test.unit;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Map;

import org.junit.After;

import com.neeve.rog.IRogMessage;
import com.neeve.rog.log.RogLogUtil;
import com.neeve.rog.log.RogLogUtil.JsonPrettyPrintStyle;
import com.neeve.server.embedded.EmbeddedServer;
import com.neeve.test.UnitTest;
import com.neeve.toa.TopicOrientedApplication;

/**
 * 
 */
public abstract class AbstractToaTest extends UnitTest {
    private static final HashSet<String> DIVERGENT_PRIMARY_BACKUP_FIELD_EXCEPTIONS = new HashSet<String>();
    private static final HashSet<String> DIVERGENT_SENDER_RECEIVER_FIELD_EXCEPTIONS = new HashSet<String>();
    static
    {
        //Ownership count can diverge between backup and primary because we lazily
        //recycle commit queue entries
        DIVERGENT_PRIMARY_BACKUP_FIELD_EXCEPTIONS.add("ownershipCount");

        DIVERGENT_SENDER_RECEIVER_FIELD_EXCEPTIONS.add("messageBus");
        DIVERGENT_SENDER_RECEIVER_FIELD_EXCEPTIONS.add("messageChannel");
        DIVERGENT_SENDER_RECEIVER_FIELD_EXCEPTIONS.add("messageKey");
        DIVERGENT_SENDER_RECEIVER_FIELD_EXCEPTIONS.add("messageBusAsRaw");
        DIVERGENT_SENDER_RECEIVER_FIELD_EXCEPTIONS.add("messageChannelAsRaw");
        DIVERGENT_SENDER_RECEIVER_FIELD_EXCEPTIONS.add("messageKeyAsRaw");
        DIVERGENT_SENDER_RECEIVER_FIELD_EXCEPTIONS.add("transactionInSequenceNumber");
        DIVERGENT_SENDER_RECEIVER_FIELD_EXCEPTIONS.add("transactionOutSequenceNumber");
        DIVERGENT_SENDER_RECEIVER_FIELD_EXCEPTIONS.add("inMsgsInTransaction");
        DIVERGENT_SENDER_RECEIVER_FIELD_EXCEPTIONS.add("outMsgsInTransaction");
        DIVERGENT_SENDER_RECEIVER_FIELD_EXCEPTIONS.add("ownershipCount");
        DIVERGENT_SENDER_RECEIVER_FIELD_EXCEPTIONS.add("outTs");
        DIVERGENT_SENDER_RECEIVER_FIELD_EXCEPTIONS.add("transactionInSequenceNumber");
        DIVERGENT_SENDER_RECEIVER_FIELD_EXCEPTIONS.add("isInboundMessage");
        DIVERGENT_SENDER_RECEIVER_FIELD_EXCEPTIONS.add("isOutboundMessage");
        DIVERGENT_SENDER_RECEIVER_FIELD_EXCEPTIONS.add("transactionId");
        DIVERGENT_SENDER_RECEIVER_FIELD_EXCEPTIONS.add("preProcessingTs");
    }

    private static final RogLogUtil.FieldFilter PRIMARY_BACKUP_FILTER = new RogLogUtil.FieldFilter() {
        RogLogUtil.FieldFilter defaultFilter;

        @Override
        public final boolean filter(Class<?> type, String path) {

            if (defaultFilter == null) {
                try {
                    defaultFilter = RogLogUtil.loadComparisonFilter();
                }
                catch (IOException e) {
                    throw new RuntimeException("Error loading comparison filter", e);
                }
            }

            return defaultFilter.filter(type, path) || DIVERGENT_PRIMARY_BACKUP_FIELD_EXCEPTIONS.contains(path);
        }

    };

    private static final RogLogUtil.FieldFilter SENDER_RECEIVER_FILTER = new RogLogUtil.FieldFilter() {
        RogLogUtil.FieldFilter defaultFilter;

        @Override
        public final boolean filter(Class<?> type, String path) {

            if (defaultFilter == null) {
                try {
                    defaultFilter = RogLogUtil.loadComparisonFilter();
                }
                catch (IOException e) {
                    throw new RuntimeException("Error loading comparison filter", e);
                }
            }

            return defaultFilter.filter(type, path) || DIVERGENT_SENDER_RECEIVER_FIELD_EXCEPTIONS.contains(path);
        }

    };

    protected final HashSet<EmbeddedServer> servers = new HashSet<EmbeddedServer>();

    @After
    public void cleanup() throws Throwable {
        Throwable error = null;
        for (EmbeddedServer server : servers) {
            try {
                server.shutdown();
                if (server.getStartupError() == null) {
                    if (server instanceof SingleAppToaServer<?>) {
                        Object app = ((SingleAppToaServer<?>)server).getApplication();
                        if (app instanceof AbstractToaTestApp) {
                            ((AbstractToaTestApp)app).cleanup();
                        }
                    }
                }
            }
            catch (Throwable thrown) {
                if (error != null) {
                    error = thrown;
                }
                thrown.printStackTrace();
            }
        }

        if (error != null) {
            throw error;
        }
    }

    protected <T extends TopicOrientedApplication> SingleAppToaServer<T> createServer(final String appName, final Class<T> applicationClass) {
        SingleAppToaServer<T> server = SingleAppToaServer.create(appName, null, applicationClass, null);
        servers.add(server);
        return server;
    }

    protected <T extends TopicOrientedApplication> SingleAppToaServer<T> createServer(final String appName, final String instanceId, final Class<T> applicationClass) {
        SingleAppToaServer<T> server = SingleAppToaServer.create(appName, instanceId, applicationClass, null);
        servers.add(server);
        return server;
    }

    protected <T extends TopicOrientedApplication> SingleAppToaServer<T> createServer(final String appName, final String instanceId, final Class<T> applicationClass, Map<String, String> configOverrides) {
        SingleAppToaServer<T> server = SingleAppToaServer.create(appName, instanceId, applicationClass, configOverrides);
        servers.add(server);
        return server;
    }

    protected <T extends TopicOrientedApplication> T createApp(final String appName, final String instanceId, final Class<T> applicationClass) throws Throwable {
        SingleAppToaServer<T> server = createServer(appName, instanceId, applicationClass);
        server.start();
        if (server.getApplication().getEngine().isPrimary()) {
            server.getApplication().getEngine().waitForMessagingToStart();
        }
        return server.getApplication();
    }

    protected <T extends TopicOrientedApplication> T createApp(final String appName, final String instanceId, final Class<T> applicationClass, Map<String, String> configOverrides) throws Throwable {
        SingleAppToaServer<T> server = createServer(appName, instanceId, applicationClass, configOverrides);
        server.start();
        if (server.getApplication().getEngine().isPrimary()) {
            server.getApplication().getEngine().waitForMessagingToStart();
        }
        return server.getApplication();
    }

    protected final void assertPrimaryAndBackupMessageEqual(AbstractToaTestApp primary, AbstractToaTestApp backup) throws Exception {
        assertEquals("Backup has different number of messages than primary.", primary.receivedMessageCount, backup.receivedMessageCount);

        StringBuffer diffBuffer = new StringBuffer();
        for (int i = 1; i <= primary.received.size(); i++) {
            if (!RogLogUtil.compareRogNodes(primary.received.get(i - 1), backup.received.get(i - 1), PRIMARY_BACKUP_FILTER, diffBuffer)) {
                throw new Exception("Primary (source) and backup (target) messages diverge at message #" + i + ": " + diffBuffer);
            }
        }
    }

    protected final void assertSentAndReceivedMessageEqual(AbstractToaTestApp sender, AbstractToaTestApp receiver) throws Exception {
        if (!sender.holdMessages || !receiver.holdMessages) {
            assertEquals("Backup has different number of messages than primary.", sender.receivedMessageCount, receiver.receivedMessageCount);
            return;
        }

        for (int i = 1; i <= sender.sent.size(); i++) {
            if (receiver.received.size() < i) {
                IRogMessage sent = sender.sent.get(i - 1);
                StringWriter writer = new StringWriter();
                RogLogUtil.dumpObjectAsJson(sent.getMetadata(), sent, true, true, true, JsonPrettyPrintStyle.Default, writer);
                throw new Exception("Receiver didn't receive message #" + i + ":\n " + writer.toString());
            }
            assertSentAndReceivedMessagesEqual("Sender (source) and receiver (target) messages diverge at message #" + i + ": ", sender.sent.get(i - 1), receiver.received.get(i - 1));
        }

        if (receiver.received.size() > sender.sent.size()) {
            IRogMessage received = receiver.received.get(sender.sent.size());
            StringWriter writer = new StringWriter();
            RogLogUtil.dumpObjectAsJson(received.getMetadata(), received, true, true, true, JsonPrettyPrintStyle.Default, writer);
            throw new Exception("Receiver received " + (receiver.received.size() - sender.sent.size()) + " additional messages. First message:\n " + writer.toString());
        }
    }

    protected final void assertSentAndReceivedMessagesEqual(String text, IRogMessage sentMessage, IRogMessage receivedMessage) throws Exception {
        StringBuffer diffBuffer = new StringBuffer();
        if (!RogLogUtil.compareRogNodes(sentMessage, receivedMessage, SENDER_RECEIVER_FILTER, diffBuffer)) {
            throw new Exception(text + ":" + diffBuffer);
        }
    }

}
