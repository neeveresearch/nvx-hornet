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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.PrintWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.Ignore;
import org.junit.Test;

import com.neeve.aep.AepEngine;
import com.neeve.aep.AepEngine.HAPolicy;
import com.neeve.aep.IAepPostdispatchMessageHandler;
import com.neeve.aep.IAepPredispatchMessageHandler;
import com.neeve.aep.annotations.EventHandler;
import com.neeve.aep.event.AepApplicationExceptionEvent;
import com.neeve.aep.event.AepChannelUpEvent;
import com.neeve.aep.event.AepUnhandledMessageEvent;
import com.neeve.event.alert.AlertEvent;
import com.neeve.rog.IRogMessage;
import com.neeve.rog.log.RogLogUtil;
import com.neeve.rog.log.RogLogUtil.JsonPrettyPrintStyle;
import com.neeve.server.app.annotations.AppHAPolicy;
import com.neeve.server.app.annotations.AppMain;
import com.neeve.server.mon.alert.SrvMonUnhandledMessageMessage;
import com.neeve.sma.MessageChannel;
import com.neeve.sma.MessageChannel.Qos;
import com.neeve.sma.event.UnhandledMessageEvent;
import com.neeve.toa.service.ToaService;
import com.neeve.toa.service.ToaServiceChannel;
import com.neeve.toa.test.unit.injectiontests.UnhandledInjectionMessage;

public class ToaMessagingTest extends AbstractToaTest {
    private static volatile Qos qos = Qos.BestEffort;

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static final class SenderApp extends AbstractToaTestApp {

        private volatile MessageChannel receiverChannel1;

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

        @EventHandler
        public void onChannelUp(AepChannelUpEvent event) {
            if (event.getMessageChannel().getName().equals("receiverservice-ReceiverChannel1")) {
                receiverChannel1 = event.getMessageChannel();
            }
        }

        @Override
        protected Qos getChannelQos(ToaService service, ToaServiceChannel channel) {
            return qos;
        }

    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static final class ForwarderApp extends AbstractToaTestApp {

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
    public static final class PrioritizingForwarderApp extends AbstractToaTestApp {

        @EventHandler
        public void onForwarder1Message(ForwarderMessage1 message) {
            recordReceipt(message);
            sendMessage(recordSend(ReceiverMessage1.create()));
        }

        @EventHandler
        public void onForwarder2Message(ForwarderMessage2 message) {
            recordReceipt(message);
            ReceiverMessage2 forward = ReceiverMessage2.create();
            forward.setLongField(message.getLongField());
            if (receivedMessageCount > 2) {
                forward.setAsPriority();
            }
            System.out.println("Forwading message " + message.getLongField() + (forward.getIsPriority() ? " as priority " : " normal priority"));
            sendMessage(recordSend(forward));
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
    public static final class DelayedPriorityInjectionForwarderApp extends AbstractToaTestApp {
        ScheduledExecutorService timer = Executors.newScheduledThreadPool(1);
        volatile int numSent = 0;

        @Override
        public void onEngineInjected(AepEngine engine) {
            timer.scheduleAtFixedRate(new Runnable() {

                @Override
                public void run() {
                    injectMessage(ForwarderMessage2.create(), false, -1);
                }

            }, 0, 1, TimeUnit.SECONDS);
        }

        @EventHandler
        public void onForwarder1Message(ForwarderMessage1 message) {
            System.out.println(new Date() + " FORWARDER Received message");
            recordReceipt(message);
        }

        @EventHandler
        public void onForwarder2Message(ForwarderMessage2 message) {
            while (numSent < receivedMessageCount) {
                ReceiverMessage1 outbound = ReceiverMessage1.create();
                outbound.setIntField(++numSent);
                System.out.println(new Date() + " FORWARDER Sending receiver message: " + numSent);
                sendMessage(recordSend(outbound));
            }
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

        /**
         * Called at the end of a testcase to perform cleanup
         */
        @Override
        public void cleanup() {
            timer.shutdown();
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
            return Qos.Guaranteed;
        }
    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static final class IntfBasedReceiverApp extends AbstractToaTestApp {
        @EventHandler
        public void onForwarder2Message(IReceiverMessage1 message) {
            recordReceipt(message);
        }

        @EventHandler
        public void onForwarder2Message(IReceiverMessage2 message) {
            recordReceipt(message);
        }
    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static final class PreAndPostDispatchTestApp extends AbstractToaTestApp implements IAepPredispatchMessageHandler, IAepPostdispatchMessageHandler {
        private volatile IRogMessage predispatchMessage;
        private volatile IRogMessage receivedMessage;
        private volatile IRogMessage postdispatchMessage;
        private volatile IRogMessage unhandledMessage;
        private volatile IRogMessage appExceptionMessage;
        private volatile Throwable failure;

        public volatile boolean exceptionOnReceipt;
        public volatile boolean exceptionOnPreMessageHandler;
        public volatile boolean exceptionOnPostMessageHandler;

        private final SecondaryMessageDispatchHandler secondary = new SecondaryMessageDispatchHandler();

        @EventHandler
        public void onReceiverMessage1(ReceiverMessage1 message) {
            receivedMessage = message;
            if (exceptionOnReceipt) {
                throw new RuntimeException("ReceiverMessage1 intentional exception");
            }
            recordReceipt(message);
        }

        @EventHandler
        public void onReceiverMessage2(ReceiverMessage2 message) {
            receivedMessage = message;
            if (exceptionOnReceipt) {
                throw new RuntimeException("ReceiverMessage2 intentional exception");
            }
            recordReceipt(message);
        }

        @Override
        public void onEngineInjected(AepEngine engine) {
            addPredispatchMessageHandler(this);
            addPredispatchMessageHandler(secondary);
            addPostdispatchMessageHandler(this);
            addPostdispatchMessageHandler(secondary);
        }

        @EventHandler
        public void onUnhandledMessage(AepUnhandledMessageEvent event) {
            unhandledMessage = (IRogMessage)event.getTriggeringMessage();
            if (!exceptionOnPostMessageHandler) {
                recordReceipt(unhandledMessage);
            }
        }

        @EventHandler
        public void onAppException(AepApplicationExceptionEvent event) {
            appExceptionMessage = event.getActiveMessage();
            if (!exceptionOnPostMessageHandler) {
                recordReceipt(appExceptionMessage);
            }
        }

        /* (non-Javadoc)
         * @see com.neeve.aep.IAepPredispatchMessageHandler#onMessage(com.neeve.rog.IRogMessage)
         */
        @Override
        public void onMessage(IRogMessage message) {
            predispatchMessage = message;
            if (postdispatchMessage != null) {
                failure = new Exception("Predispatch happened before post dispatch!");
            }
            if (exceptionOnPreMessageHandler) {
                throw new RuntimeException("onMessage intentional exception");
            }
        }

        /* (non-Javadoc)
         * @see com.neeve.aep.IAepPostdispatchMessageHandler#postMessage(com.neeve.rog.IRogMessage)
         */
        @Override
        public void postMessage(IRogMessage message) {
            postdispatchMessage = message;
            if (predispatchMessage == null && unhandledMessage == null && appExceptionMessage == null) {
                failure = new Exception("postDispatch happened without predispatch!");
            }

            if (exceptionOnPostMessageHandler) {
                throw new RuntimeException("postMessage intentional exception");
            }
        }

        public void validate() throws Throwable {
            if (failure != null) {
                throw failure;
            }

            if (predispatchMessage != null && !exceptionOnPreMessageHandler) {
                assertSame("Secondary pre dispatch handler not invoked", predispatchMessage, secondary.predispatchMessage);
            }

            if (postdispatchMessage != null) {
                if (!exceptionOnPostMessageHandler) {
                    assertSame("Secondary post dispatch handler not invoked", postdispatchMessage, secondary.postdispatchMessage);
                }
                else {
                    assertNull("Secondary post dispatch handler should not haven been invoked when first throws an exception", secondary.postdispatchMessage);
                }
            }
        }
    }

    private static class SecondaryMessageDispatchHandler implements IAepPredispatchMessageHandler, IAepPostdispatchMessageHandler {
        private volatile IRogMessage predispatchMessage;
        private volatile IRogMessage postdispatchMessage;

        /* (non-Javadoc)
         * @see com.neeve.aep.IAepPostdispatchMessageHandler#postMessage(com.neeve.rog.IRogMessage)
         */
        @Override
        public void postMessage(IRogMessage message) {
            postdispatchMessage = message;
        }

        /* (non-Javadoc)
         * @see com.neeve.aep.IAepPredispatchMessageHandler#onMessage(com.neeve.rog.IRogMessage)
         */
        @Override
        public void onMessage(IRogMessage message) {
            predispatchMessage = message;
        }
    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static class UnhandledMessageEventTestApp extends AbstractToaTestApp {

        private UnhandledMessageEvent event;
        private final CountDownLatch latch = new CountDownLatch(1);

        @EventHandler
        public void onForwarder2Message(ReceiverMessage1 message) {
            recordReceipt(message);
        }

        @EventHandler
        public void onForwarder2Message(ReceiverMessage2 message) {
            recordReceipt(message);
        }

        @EventHandler
        public void onAlert(AlertEvent event) {
            if (event instanceof UnhandledMessageEvent) {
                this.event = (UnhandledMessageEvent)event;
                event.acquire();
                latch.countDown();
            }
        }

        public final UnhandledMessageEvent waitForUnhandledMessageEvent(long timeout) throws InterruptedException {
            latch.await(timeout, TimeUnit.SECONDS);
            if (event == null) {
                fail("Didn't get expected UnhandledMessageEvent");
            }
            return event;
        }

    }

    private final void testSenderForwarderReceiver(Qos qos) throws Throwable {
        ToaMessagingTest.qos = qos;
        ReceiverApp receiver = createApp("testSenderForwarderReceiverReceiver" + qos, "standalone", ReceiverApp.class);
        ForwarderApp forwarder = createApp("testSenderForwarderReceiverFowarder" + qos, "standalone", ForwarderApp.class);
        SenderApp sender = createApp("testSenderForwarderReceiverSender" + qos, "standalone", SenderApp.class);
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
    }

    @Test
    public final void testSenderForwarderReceiverBestEffort() throws Throwable {
        testSenderForwarderReceiver(Qos.BestEffort);
    }

    @Test
    public final void testSenderForwarderReceiverGuaranteed() throws Throwable {
        testSenderForwarderReceiver(Qos.Guaranteed);
    }

    @Test
    @Ignore
    public final void testSenderForwarderReceiverLongDuration() throws Throwable {
        ReceiverApp receiver = createApp("receiver", "standalone", ReceiverApp.class);
        ForwarderApp forwarder = createApp("forwarder", "standalone", ForwarderApp.class);
        SenderApp sender = createApp("sender", "standalone", SenderApp.class);

        sender.holdMessages = false;
        receiver.holdMessages = false;
        forwarder.holdMessages = false;

        final int numSends = 10000000;
        for (int i = 0; i < numSends; i++) {
            sender.sendMessage(ForwarderMessage1.create());
            Thread.sleep(100);
        }

        forwarder.assertExpectedSends(10, sender.sentMessageCount + numSends);
        forwarder.assertExpectedReceipt(0, sender.sentMessageCount + numSends);
        receiver.assertExpectedReceipt(10, sender.sentMessageCount + numSends);

        sender.waitForTransactionStability(2);
        forwarder.waitForTransactionStability(2);
        receiver.waitForTransactionStability(2);

        forwarder.assertExpectedSends(numSends + 4, sender.sentMessageCount + numSends);
        forwarder.assertExpectedReceipt(0, sender.sentMessageCount + numSends);
        receiver.assertExpectedReceipt(numSends + 4, sender.sentMessageCount + numSends);
    }

    @Test
    public final void testPreAndPostMessageHandlers() throws Throwable {
        PreAndPostDispatchTestApp app = createApp(testcaseName.getMethodName(), "standalone", PreAndPostDispatchTestApp.class);
        app.injectMessage(ReceiverMessage1.create());

        app.assertExpectedReceipt(2, 1);

        assertNotNull("App should have received message", app.receivedMessage);
        assertNotNull("PredispatchMessageHandler was not invoked", app.predispatchMessage);
        assertSame(app.received.get(0), app.predispatchMessage);
        if (app.postdispatchMessage == null) {
            Thread.sleep(1000);
        }
        assertNotNull("PostdispatchMessageHandler was not invoked", app.postdispatchMessage);
        assertSame(app.received.get(0), app.postdispatchMessage);

        app.validate();
    }

    /**
     * Tests pre and post dispatch handlers are called even if no message handler handles the message.
     */
    @Test
    public final void testPreAndPostMessageHandlersWithUnhandledMessage() throws Throwable {
        PreAndPostDispatchTestApp app = createApp(testcaseName.getMethodName(), "standalone", PreAndPostDispatchTestApp.class);
        ForwarderMessage1 message = ForwarderMessage1.create();
        message.acquire();
        app.injectMessage(message);

        //Note that the app records unhandled and appException messages as received.
        app.assertExpectedReceipt(5, 1);

        assertNull("App should not have received message of type " + ForwarderMessage1.class.getSimpleName(), app.receivedMessage);

        assertNotNull("PredispatchMessageHandler was not invoked", app.predispatchMessage);
        assertSame(app.received.get(0), app.predispatchMessage);
        if (app.postdispatchMessage == null) {
            Thread.sleep(1000);
        }
        assertNotNull("PostdispatchMessageHandler was not invoked", app.postdispatchMessage);
        assertSame(app.received.get(0), app.postdispatchMessage);

        assertNotNull("UnhandledMessageEvent was not invoked", app.unhandledMessage);
        assertNotNull("UnhandledMessageEvent was not invoked", app.unhandledMessage);

        app.validate();
    }

    /**
     * Tests pre and post dispatch handlers are called even if no message handler handles the message.
     */
    @Test
    public final void testPostDispatchMessageHandlerNotCalledWithAppException() throws Throwable {
        PreAndPostDispatchTestApp app = createApp(testcaseName.getMethodName(), "standalone", PreAndPostDispatchTestApp.class);
        ReceiverMessage1 message = ReceiverMessage1.create();
        message.acquire();
        app.exceptionOnReceipt = true;
        app.injectMessage(message);

        //Note that the app records unhandled and appException messages as received.
        app.assertExpectedReceipt(2, 1);

        assertNotNull("App message handler was not invoked.", app.receivedMessage);

        assertNotNull("PredispatchMessageHandler was not invoked", app.predispatchMessage);
        assertSame(app.received.get(0), app.predispatchMessage);

        long timeout = System.currentTimeMillis() + 5000;
        while (app.appExceptionMessage == null && System.currentTimeMillis() < timeout) {
            Thread.sleep(100);
        }
        assertNotNull("AppException was not invoked", app.appExceptionMessage);
        assertSame(app.received.get(0), app.appExceptionMessage);

        assertNull("PostdispatchMessageHandler was invoked when there was an app exception", app.postdispatchMessage);

        app.validate();
    }

    /**
     * Tests pre and post dispatch handlers are called even if no message handler handles the message.
     */
    @Test
    public final void testPostDispatchMessageHandlerNotCalledWithPredispatchException() throws Throwable {
        PreAndPostDispatchTestApp app = createApp(testcaseName.getMethodName(), "standalone", PreAndPostDispatchTestApp.class);
        ReceiverMessage1 message = ReceiverMessage1.create();
        message.acquire();
        app.exceptionOnPreMessageHandler = true;
        app.injectMessage(message);

        //Note that the app records unhandled and appException messages as received.
        app.assertExpectedReceipt(2, 1);

        assertNotNull("PredispatchMessageHandler was not invoked", app.predispatchMessage);
        assertNull("App should not have received message when pre dispatch handler throws exception", app.receivedMessage);
        assertNull("PostdispatchMessageHandler was invoked after pre dispatch handler failed.", app.postdispatchMessage);

        long timeout = System.currentTimeMillis() + 5000;
        while (app.appExceptionMessage == null && System.currentTimeMillis() < timeout) {
            Thread.sleep(100);
        }

        assertNotNull("AppException was not invoked", app.appExceptionMessage);
        assertSame(app.received.get(0), app.appExceptionMessage);

        app.validate();
    }

    /**
     * Tests pre and post dispatch handlers are called even if no message handler handles the message.
     */
    @Test
    public final void testSecondaryPostDispatchMessageHandlerNotCalledWithPostDispatchException() throws Throwable {
        PreAndPostDispatchTestApp app = createApp(testcaseName.getMethodName(), "standalone", PreAndPostDispatchTestApp.class);
        ReceiverMessage1 message = ReceiverMessage1.create();
        message.acquire();
        app.exceptionOnPostMessageHandler = true;
        app.injectMessage(message);

        //Note that the app records unhandled and appException messages as received.
        app.assertExpectedReceipt(2, 1);

        assertNotNull("PredispatchMessageHandler was not invoked", app.predispatchMessage);
        assertNotNull("App have received message", app.receivedMessage);
        assertNotNull("PostdispatchMessageHandler was not invoked.", app.postdispatchMessage);

        long timeout = System.currentTimeMillis() + 5000;
        while (app.appExceptionMessage == null && System.currentTimeMillis() < timeout) {
            Thread.sleep(100);
        }

        assertNotNull("AppException was not invoked", app.appExceptionMessage);
        assertSame(app.received.get(0), app.appExceptionMessage);

        app.validate();
    }

    /**
     * Tests that attempting to used interface based event handlers throws an error.
     */
    @Test
    public void testInterfaceBasedHandlersThrowError() throws Throwable {
        try {
            createApp("receiver", "standalone", IntfBasedReceiverApp.class);
            fail("Receiver app with interfaced based message handlers should have raised an exception.");
        }
        catch (Exception e) {
            assertTrue("Startup exception should contain 'Interface based message EventHandler'", e.getMessage().indexOf("Interface based message EventHandler") >= 0);
        }
    }

    /**
     * Tests that an UnhandledMessageEvent is dispatched for an unregistered message type
     * sent to an application.
     */
    @Test
    public void testUnhandledMessageEvent() throws Throwable {
        SenderApp sender = createApp("testUnhandledMessageEventSender", "standalone", SenderApp.class);
        UnhandledMessageEventTestApp receiver = createApp("testUnhandledMessageEventReceiver", "standalone", UnhandledMessageEventTestApp.class);

        UnhandledInjectionMessage message = UnhandledInjectionMessage.create();
        message.acquire();
        message.setMessageBus(sender.receiverChannel1.getMessageBusBinding().getName());
        message.setMessageChannel(sender.receiverChannel1.getName());
        sender.getAepEngine().sendMessage(sender.receiverChannel1, message);

        UnhandledMessageEvent event = receiver.waitForUnhandledMessageEvent(5);

        //<16,16296,colins-asus> 20151020-20:04:29:757 (wrn)...<nv.toa> [testUnhandledMessageEventReceiver] ALERT: [event=UNHANDLED_MESSAGE, type=111, source=testUnhandledMessageEventReceiver, owners=1, endOfBatch=true, delay=0, scheduledTime=0, dispatchTime=0, time=0, metadata=[2,3,-201,1,-1996505383,0,0,-1,receiverservice-ReceiverChannel1], key=null, reason=java.lang.RuntimeException: View factory '-201' could not be found]: SrvMonUnhandledMessageMessage {XRogType=13,TriggeringMessageMessageSno=0,TriggeringMessageMessageFlowId=0,TriggeringMessageMessageSenderId=-1996505383,TriggeringMessageMessageFactoryId=-201,TriggeringMessageMessageViewId=1,TriggeringMessageMessageEncodingType=3,Timestamp=0,TriggeringMessageMessageBusName=null,TriggeringMessageMessageChannelName=receiverservice-ReceiverChannel1,TriggeringMessageMessageChannelId=null,TriggeringMessageMessageKey=null,Exception=java.lang.RuntimeException: View factory '-201' could not be found
        assertNotNull("Didn't get expected UnhandledMessageEvent", event);
        assertNotNull("Didn't get expected UnhandledMessageEvent's backing message is null", event);
        SrvMonUnhandledMessageMessage backingMessage = (SrvMonUnhandledMessageMessage)event.getBackingMessage();
        RogLogUtil.dumpObjectAsJson(backingMessage.getMetadata(), backingMessage, false, true, false, JsonPrettyPrintStyle.PrettyPrint, new PrintWriter(System.out));

        assertTrue("Wrong exception, expected 'View factory '-201' could not be found', but was " + backingMessage.getException(), backingMessage.getException().indexOf("View factory '-201' could not be found") >= 0);
        assertEquals("Unexpected timestamp", event.getEventTime(), backingMessage.getEventTimestampAsTimestamp());
        assertEquals("Unexpected triggeringMessageMessageBusName", "testUnhandledMessageEventReceiver", backingMessage.getTriggeringMessageMessageBusName());
        assertEquals("Unexpected triggeringMessageMessageChannelName", sender.receiverChannel1.getName(), backingMessage.getTriggeringMessageMessageChannelName());
        assertEquals("Unexpected triggeringMessageMessageEncodingType", message.getMessageEncodingType(), backingMessage.getTriggeringMessageMessageEncodingType());
        assertEquals("Unexpected triggeringMessageMessageFactoryId", message.getVfid(), backingMessage.getTriggeringMessageMessageFactoryId());
        assertEquals("Unexpected triggeringMessageMessageFlowId", 0, backingMessage.getTriggeringMessageMessageFlowId());
        assertEquals("Unexpected triggeringMessageMessageSenderId", message.getMetadata().getMessageSender(), backingMessage.getTriggeringMessageMessageSenderId());
        assertEquals("Unexpected triggeringMessageMessageSno", 0, backingMessage.getTriggeringMessageMessageSno());
        assertEquals("Unexpected triggeringMessageMessageViewId", message.getMessageType(), backingMessage.getTriggeringMessageMessageViewId());
    }

    /**
     * Tests that an UnhandledMessageEvent is dispatched for an unregistered message type
     * sent to an application.
     */
    @Test
    @Ignore
    public void testPriorityMessageWithAdaptiveBatching() throws Throwable {
        SenderApp sender = createApp("testPriorityMessageWithAdaptiveBatchingSender", "standalone", SenderApp.class);
        Map<String, String> overrides = new HashMap<String, String>();
        overrides.put("adaptiveCommitBatchCeiling", "3");
        PrioritizingForwarderApp forwarder = createApp("testPriorityMessageWithAdaptiveBatchingForwarder", "standalone", PrioritizingForwarderApp.class, overrides);
        ReceiverApp receiver = createApp("testPriorityMessageWithAdaptiveBatchingReceiver", "standalone", ReceiverApp.class);
        receiver.getEngine().waitForMessagingToStart();
        receiver.setHoldMessages(true);

        //forwarder batch 1:
        ForwarderMessage2 m1 = ForwarderMessage2.create();
        m1.setLongField(1);
        // forwarder batch 2:
        ForwarderMessage2 m2 = ForwarderMessage2.create();
        // ... messages after 2 will all be prioritized:
        m2.setLongField(2);
        ForwarderMessage2 m3 = ForwarderMessage2.create();
        m3.setLongField(3);
        ForwarderMessage2 m4 = ForwarderMessage2.create();
        m4.setLongField(4);
        //forwarder batch 3
        ForwarderMessage2 m5 = ForwarderMessage2.create();
        m5.setLongField(5);
        ForwarderMessage2 m6 = ForwarderMessage2.create();
        m6.setLongField(6);
        ForwarderMessage2 m7 = ForwarderMessage2.create();
        m7.setLongField(7);

        sender.sendMessage(m1);
        sender.sendMessage(m2);
        sender.sendMessage(m3);
        sender.sendMessage(m4);
        sender.sendMessage(m5);
        sender.sendMessage(m6);
        sender.sendMessage(m7);

        assertTrue("Forwarder didn't receive all messages", forwarder.waitForMessages(5, 7));
        assertTrue("Receiver didn't receive all messages", receiver.waitForMessages(5, 7));

        assertEquals("Wrong message for message 1", 1, ((ReceiverMessage2)receiver.received.get(0)).getLongField());
        assertEquals("Wrong message for message 2", 3, ((ReceiverMessage2)receiver.received.get(1)).getLongField());
        assertEquals("Wrong message for message 3", 4, ((ReceiverMessage2)receiver.received.get(2)).getLongField());
        assertEquals("Wrong message for message 4", 2, ((ReceiverMessage2)receiver.received.get(3)).getLongField());
        assertEquals("Wrong message for message 5", 5, ((ReceiverMessage2)receiver.received.get(4)).getLongField());
        assertEquals("Wrong message for message 6", 6, ((ReceiverMessage2)receiver.received.get(5)).getLongField());
        assertEquals("Wrong message for message 7", 7, ((ReceiverMessage2)receiver.received.get(6)).getLongField());

    }

    /**
     * Tests that a message injected with priority will close out a transaction batch. 
     */
    @Test
    public final void testPriorityInjectionForwarderAdaptiveBatch() throws Throwable {
        ToaMessagingTest.qos = qos;
        ReceiverApp receiver = createApp("testSenderForwarderAdaptiveBatchTimeoutReceiver" + qos, "standalone", ReceiverApp.class);
        Map<String, String> overrides = new HashMap<String, String>();
        overrides.put("adaptiveCommitBatchCeiling", "64");
        DelayedPriorityInjectionForwarderApp forwarder = createApp("testSenderForwarderAdaptiveBatchTimeoutForwarder" + qos, "standalone", DelayedPriorityInjectionForwarderApp.class, overrides);

        forwarder.injectMessage(forwarder.populateMessage(ForwarderMessage1.create()));

        forwarder.waitForTransactionStability(2);
        receiver.waitForTransactionStability(2);
        assertTrue("Receiver didn't receive message in allotted time", receiver.waitForMessages(10, 1));

        assertSentAndReceivedMessageEqual(forwarder, receiver);
    }
}
