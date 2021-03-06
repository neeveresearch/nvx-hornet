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
 * with the License.  You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.neeve.toa.test.unit;

import static com.neeve.toa.test.unit.SingleAppToaServer.PROP_NAME_STORE_CLUSTERING_ENABLED;
import static com.neeve.toa.test.unit.SingleAppToaServer.PROP_NAME_STORE_ENABLED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.Ignore;
import org.junit.Test;

import com.neeve.aep.AepEngine.HAPolicy;
import com.neeve.aep.IAepApplicationStateFactory;
import com.neeve.aep.annotations.EventHandler;
import com.neeve.ci.XRuntime;
import com.neeve.rog.IRogMessage;
import com.neeve.server.app.annotations.AppHAPolicy;
import com.neeve.server.app.annotations.AppMain;
import com.neeve.server.app.annotations.AppStateFactoryAccessor;
import com.neeve.sma.MessageChannel.Qos;
import com.neeve.sma.MessageView;
import com.neeve.toa.TopicOrientedApplication;
import com.neeve.toa.opt.DelayedAcknowledgmentController.DelayedAcknowledger;
import com.neeve.toa.service.ToaService;
import com.neeve.toa.service.ToaServiceChannel;

/**
 * Tests for delayed ack controller. 
 */
public class DelayedAckTest extends AbstractToaTest {
    private static volatile Qos qos = Qos.Guaranteed;

    static {
        XRuntime.getProps().setProperty(TopicOrientedApplication.PROP_ENABLED_DELAYED_ACK_CONTROLLER, "true");
    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static final class SenderApp extends AbstractToaTestApp {
        @AppMain
        public void appMain(String[] args) {
            sendMessage(recordSend(populateMessage(ForwarderMessage1.create())));
            sendMessage(recordSend(populateMessage(ForwarderMessage2.create())));
            sendMessage(recordSend(populateMessage(ForwarderMessage1.create())));
            sendMessage(recordSend(populateMessage(ForwarderMessage2.create())));
        }

        @Override
        protected Properties getInitialChannelKeyResolutionTable(ToaService service, ToaServiceChannel channel) {
            Properties props = new Properties();
            props.setProperty("IntField", "0");
            return props;
        }

        @Override
        protected Qos getChannelQos(ToaService service, ToaServiceChannel channel) {
            return qos;
        }
    }

    public static class ForwarderApp extends AbstractToaTestApp {
        volatile List<DelayedAcknowledger> delayedAcks = new ArrayList<DelayedAcknowledger>();

        @EventHandler
        public void onForwarder1Message(ForwarderMessage1 message) {
            recordReceipt(message);
            ReceiverMessage1 outbound = ReceiverMessage1.create();
            outbound.setIntField(receivedMessageCount);
            sendMessage(recordSend(outbound));
        }

        @EventHandler
        public void onForwarder2Message(ForwarderMessage2 message) {
            recordReceipt(message);
            ReceiverMessage2 outbound = ReceiverMessage2.create();
            outbound.setIntField(receivedMessageCount);
            sendMessage(recordSend(outbound));
        }

        public IRogMessage recordReceipt(IRogMessage message) {
            delayedAcks.add(getDelayedAcknowledgmentController().delayAcknowledgment());
            return super.recordReceipt(message);
        }

        protected IRogMessage onlyRecordReceipt(IRogMessage message) {
            return super.recordReceipt(message);
        }

        @Override
        protected Properties getInitialChannelKeyResolutionTable(ToaService service, ToaServiceChannel channel) {
            Properties props = new Properties();
            props.setProperty("IntField", "0");
            return props;
        }

        protected Qos getChannelQos(ToaService service, ToaServiceChannel channel) {
            return qos;
        }

    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static final class ForwarderESApp extends ForwarderApp {
    }

    @AppHAPolicy(HAPolicy.StateReplication)
    public static final class ForwarderNoStateSRApp extends ForwarderApp {
    }

    @AppHAPolicy(HAPolicy.StateReplication)
    public static final class ForwarderWithStateSRApp extends ForwarderApp {
        @AppStateFactoryAccessor
        final public IAepApplicationStateFactory getStateFactory() {
            return new IAepApplicationStateFactory() {
                @Override
                final public com.neeve.toa.test.unit.delayedacktests.state.Repository createState(MessageView view) {
                    return com.neeve.toa.test.unit.delayedacktests.state.Repository.create();
                }
            };
        }

        @Override
        public IRogMessage recordReceipt(IRogMessage message) {
            try {
                getDelayedAcknowledgmentController().delayAcknowledgment();
                fail("Expected an unsupported operation exception on delayAcknowledgement");
                return null;
            }
            catch (UnsupportedOperationException e) {
                e.printStackTrace();
                return super.onlyRecordReceipt(message);
            }
        }

    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static final class ReceiverApp extends AbstractToaTestApp {
        @EventHandler
        public void onForwarder2Message(ReceiverMessage1 message) {
            recordReceipt(message);
        }

        @EventHandler
        public void onForwarder2Message(ReceiverMessage2 message) {
            recordReceipt(message);
        }

        @Override
        protected Qos getChannelQos(ToaService service, ToaServiceChannel channel) {
            return qos;
        }
    }

    private final void testDelayedAcknowledgment(final Class<? extends ForwarderApp> forwarderAppClass) throws Throwable {
        ReceiverApp receiver = createApp("testSenderForwarderReceiverReceiver", "standalone", ReceiverApp.class);
        ForwarderApp forwarder = createApp("testSenderForwarderReceiverFowarder", "standalone", forwarderAppClass);
        SenderApp sender = createApp("testSenderForwarderReceiverSender", "standalone", SenderApp.class);
        sender.getEngine().waitForMessagingToStart();
        sender.assertExpectedSends(5, 4);

        if (verbose()) {
            for (IRogMessage message : sender.sent) {
                System.out.println("Sent: " + message.toString());
            }
        }

        sender.waitForTransactionStability(2);
        forwarder.waitForTransactionStability(2);
        receiver.waitForTransactionStability(2);

        assertSentAndReceivedMessageEqual(sender, forwarder);
        assertSentAndReceivedMessageEqual(forwarder, receiver);

        // forwarder should not have completed transactions since acks have been delayed. 
        assertEquals("Wrong number of incomplete transactions for forwarder", 4l,
                     forwarder.getAepEngine().getStats().getNumCommitsStarted() -
                             forwarder.getAepEngine().getStats().getNumCommitsCompleted());

        for (DelayedAcknowledger delayedAck : forwarder.delayedAcks) {
            delayedAck.acknowledge();
        }

        forwarder.delayedAcks.clear();

        forwarder.waitForTransactionStability(2);

        // forwarder should now have completed transactions
        assertEquals("Wrong number of incomplete transactions for forwarder", 0l,
                     forwarder.getAepEngine().getStats().getNumCommitsStarted() -
                             forwarder.getAepEngine().getStats().getNumCommitsCompleted());
    }

    @Test
    public final void testDelayedAcknowledgmentWithESApp() throws Throwable {
        testDelayedAcknowledgment(ForwarderESApp.class);
    }

    @Test
    public final void testDelayedAcknowledgmentWithNoStateSRApp() throws Throwable {
        testDelayedAcknowledgment(ForwarderNoStateSRApp.class);
    }

    @Test
    public final void testDelayedAcknowledgmentNack() throws Throwable {
        ReceiverApp receiver = createApp("testDelayedAcknowledgmentNackReceiver", "standalone", ReceiverApp.class);
        ForwarderApp forwarder = createApp("testDelayedAcknowledgmentNackFowarder", "standalone", ForwarderESApp.class);
        SenderApp sender = createApp("testDelayedAcknowledgmentNackSender", "standalone", SenderApp.class);
        sender.getEngine().waitForMessagingToStart();
        sender.assertExpectedSends(5, 4);

        if (verbose()) {
            for (IRogMessage message : sender.sent) {
                System.out.println("Sent: " + message.toString());
            }
        }

        receiver.waitForMessages(10, 4);

        sender.waitForTransactionStability(2, 4);
        forwarder.waitForTransactionStability(2, 4);
        receiver.waitForTransactionStability(2, 4);

        assertSentAndReceivedMessageEqual(sender, forwarder);
        assertSentAndReceivedMessageEqual(forwarder, receiver);

        // forwarder should not have completed transactions since acks have been delayed. 
        assertEquals("Wrong number of incomplete transactions for forwarder", 4l,
                     forwarder.getAepEngine().getStats().getNumCommitsStarted() -
                             forwarder.getAepEngine().getStats().getNumCommitsCompleted());

        for (DelayedAcknowledger delayedAck : forwarder.delayedAcks) {
            delayedAck.acknowledge(new Exception("Intentional Failure"));
        }

        forwarder.delayedAcks.clear();

        forwarder.waitForTransactionStability(2);

        // forwarder should now have completed transactions
        assertEquals("Wrong number of incomplete transactions for forwarder", 4l,
                     forwarder.getAepEngine().getStats().getNumCommitsStarted() -
                             forwarder.getAepEngine().getStats().getNumCommitsCompleted());
    }

    @Test
    public final void testDelayedAcknowledgmentRefCountingError() throws Throwable {
        ReceiverApp receiver = createApp("testDelayedAcknowledgmentRefCountingErrorReceiver", "standalone", ReceiverApp.class);
        ForwarderApp forwarder = createApp("testDelayedAcknowledgmentRefCountingErrorForwarder", "standalone", ForwarderESApp.class);
        SenderApp sender = createApp("testDelayedAcknowledgmentRefCountingErrorSender", "standalone", SenderApp.class);
        sender.getEngine().waitForMessagingToStart();
        sender.assertExpectedSends(5, 4);

        if (verbose()) {
            for (IRogMessage message : sender.sent) {
                System.out.println("Sent: " + message.toString());
            }
        }

        sender.waitForTransactionStability(2);
        forwarder.waitForTransactionStability(2);
        receiver.waitForTransactionStability(2);

        assertSentAndReceivedMessageEqual(sender, forwarder);
        assertSentAndReceivedMessageEqual(forwarder, receiver);

        // forwarder should not have completed transactions since acks have been delayed. 
        assertEquals("Wrong number of incomplete transactions for forwarder", 4l,
                     forwarder.getAepEngine().getStats().getNumCommitsStarted() -
                             forwarder.getAepEngine().getStats().getNumCommitsCompleted());

        Thread.sleep(1000);
        forwarder.delayedAcks.get(0).acknowledge();
        try {
            forwarder.delayedAcks.get(0).acknowledge();
            fail("Expected an illegal state exception on double acknowledge");
        }
        catch (IllegalStateException e) {
            assertEquals("Wrong execption text", "Attempt to acknowledge an already acknowledged delayed acknowledgment!", e.getMessage());
        }
        finally {
            forwarder.delayedAcks.remove(0);
            for (DelayedAcknowledger delayedAck : forwarder.delayedAcks) {
                delayedAck.acknowledge();
            }
        }
    }

    @Test
    public final void testDelayedAcknowledgmentNotSupportedFromNonEngineThread() throws Throwable {
        ForwarderApp forwarder = createApp("testDelayedAcknowledgmentNotSupportedFromNonEngineThread", "standalone", ForwarderESApp.class);
        forwarder.getEngine().waitForMessagingToStart();
        try {
            forwarder.getDelayedAcknowledgmentController().delayAcknowledgment();
            fail("Shouldn't have been able to delay ack from outside of engine thread.");
        }
        catch (Exception e) {
            assertEquals("Wrong execption text", "createDelayedAck cannot be called from outside of a message handler!", e.getMessage());
        }
    }

    @Test
    @Ignore("Failing when run along with other tests")
    public final void testDelayedAcknowledgmentNotSupportedWithSRAppWithState() throws Throwable {
        ReceiverApp receiver = createApp("testDelayedAcknowledgmentNackReceiver", "standalone", ReceiverApp.class);
        ForwarderApp forwarder = createApp("testDelayedAcknowledgmentNackFowarder", "standalone", ForwarderWithStateSRApp.class);
        SenderApp sender = createApp("testDelayedAcknowledgmentNackSender", "standalone", SenderApp.class);
        sender.getEngine().waitForMessagingToStart();
        sender.assertExpectedSends(5, 4);
        assertTrue(receiver.waitForMessages(10, 4));
        sender.waitForTransactionStability(2, 4);
        assertTrue(forwarder.waitForTransactionStability(2, 4));
        assertTrue(receiver.waitForTransactionStability(2, 4));
        assertSentAndReceivedMessageEqual(sender, forwarder);
        assertSentAndReceivedMessageEqual(forwarder, receiver);
    }
}
