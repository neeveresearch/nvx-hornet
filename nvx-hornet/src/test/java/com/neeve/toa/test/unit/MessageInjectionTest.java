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
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.neeve.aep.AepEngine;
import com.neeve.aep.AepEngine.HAPolicy;
import com.neeve.aep.annotations.EventHandler;
import com.neeve.aep.event.AepEngineStoppedEvent;
import com.neeve.aep.event.AepMessagingPrestartEvent;
import com.neeve.rog.IRogMessage;
import com.neeve.server.app.annotations.AppHAPolicy;
import com.neeve.sma.MessageViewFactoryRegistry;
import com.neeve.toa.ToaException;
import com.neeve.toa.test.unit.injectiontests.ConflictingFactoryMessages1;
import com.neeve.toa.test.unit.injectiontests.FirstMessage1;
import com.neeve.toa.test.unit.injectiontests.FirstMessageFactory;
import com.neeve.toa.test.unit.injectiontests.InitialMessage1;
import com.neeve.toa.test.unit.injectiontests.InitialMessageFactory;
import com.neeve.toa.test.unit.injectiontests.InjectionFactory;
import com.neeve.toa.test.unit.injectiontests.InjectionMessage1;
import com.neeve.toa.test.unit.injectiontests.UnhandledInjectionMessage;

/**
 * Tests for MessageInjection via TOA
 */
public class MessageInjectionTest extends AbstractToaTest {

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static class MessageInjectionTestApp extends AbstractToaTestApp {

        @EventHandler
        public void onForwarderMessage1(ForwarderMessage1 message) {
            recordReceipt(message);
        }

        @EventHandler
        public void onForwarderMessage2(ForwarderMessage2 message) {
            recordReceipt(message);
        }
    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static class EventHandlerDrivenFactoryRegistrationTestApp extends AbstractToaTestApp {

        @EventHandler
        public void onMessage(InjectionMessage1 message) {
            recordReceipt(message);
        }
    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static class FailFastInjectionTestApp extends AbstractToaTestApp {

    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static class FirstMessageTestApp extends AbstractToaTestApp {

        @EventHandler
        public final void onFirstMessage(FirstMessage1 message) {
            recordReceipt(message);
        }

        @EventHandler
        public final void onMessagePrestartEvent(AepMessagingPrestartEvent event) {
            event.setFirstMessage(FirstMessage1.create());
        }
    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static class InitialMessageTestApp extends AbstractToaTestApp {

        @EventHandler
        public final void onInitialMessage(InitialMessage1 message) {
            recordReceipt(message);
        }

        @EventHandler
        public final void onMessagePrestartEvent(AepMessagingPrestartEvent event) {
            event.setFirstMessage(InitialMessage1.create());
        }
    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static class FailFastFirstMessageTest extends AbstractToaTestApp {
        CountDownLatch latch = new CountDownLatch(1);
        volatile Exception cause;

        @EventHandler
        public final void onMessagePrestartEvent(AepMessagingPrestartEvent event) {
            event.setFirstMessage(UnhandledInjectionMessage.create());
        }

        @EventHandler
        public final void onEngineStopped(AepEngineStoppedEvent event) {
            cause = event.getCause();
            latch.countDown();
        }
    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static class FailFastInitialMessageTest extends AbstractToaTestApp {
        CountDownLatch latch = new CountDownLatch(1);
        volatile Exception cause;

        @EventHandler
        public final void onMessagePrestartEvent(AepMessagingPrestartEvent event) {
            event.addInitialMessage(UnhandledInjectionMessage.create());
        }

        @EventHandler
        public final void onEngineStopped(AepEngineStoppedEvent event) {
            cause = event.getCause();
            latch.countDown();
        }
    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static class ConflictingFactoryTestApp extends AbstractToaTestApp {

        @EventHandler
        public final void onTestModelMessage(ForwarderMessage1 message) {
            recordReceipt(message);
        }

        @EventHandler
        public final void onConflictingFactoryMessage(ConflictingFactoryMessages1 message) {
            recordReceipt(message);
        }
    }

    @Test
    public void testBlockingInjection() throws Throwable {
        SingleAppToaServer<MessageInjectionTestApp> server = createServer(testcaseName.getMethodName(), "standalone", MessageInjectionTestApp.class);
        server.start();
        MessageInjectionTestApp app = server.getApplication();
        ArrayList<IRogMessage> toInject = new ArrayList<IRogMessage>();
        toInject.add(ForwarderMessage1.create());
        toInject.add(ForwarderMessage2.create());
        for (IRogMessage message : toInject) {
            app.getMessageInjector().injectMessage(message);
        }

        app.waitForMessages(5, toInject.size());

        assertEquals("Didn't get expected number of injected messages", toInject.size(), app.received.size());
        for (int i = 0; i < toInject.size(); i++) {
            assertSame("Wrong message received by application", toInject.get(i), app.received.get(i));
            assertEquals("Wrong reference count for injected message", 1, app.received.get(i).getOwnershipCount());
        }
    }

    @Test
    public void testNonBlockingInjection() throws Throwable {
        SingleAppToaServer<MessageInjectionTestApp> server = createServer(testcaseName.getMethodName(), "standalone", MessageInjectionTestApp.class);
        server.start();
        MessageInjectionTestApp app = server.getApplication();
        ArrayList<IRogMessage> toInject = new ArrayList<IRogMessage>();
        toInject.add(ForwarderMessage1.create());
        toInject.add(ForwarderMessage2.create());
        for (IRogMessage message : toInject) {
            app.getMessageInjector().injectMessage(message, true);
        }

        app.waitForMessages(10, toInject.size());

        assertEquals("Didn't get expected number of injected messages", toInject.size(), app.received.size());
        for (int i = 0; i < toInject.size(); i++) {
            assertSame("Wrong message received by application", toInject.get(i), app.received.get(i));
            assertEquals("Wrong reference count for injected message", 1, app.received.get(i).getOwnershipCount());
        }
    }

    @Test
    public void testFailFastInjectionOfMessageWithoutHandler() throws Throwable {
        FailFastInjectionTestApp app = createApp("testFailFastInjectionOfMessageWithoutHandler", "standalone", FailFastInjectionTestApp.class);
        try {
            app.injectMessage(UnhandledInjectionMessage.create());
            Thread.sleep(1000);
            fail("Should not have been able to inject message with no handler (should fail fast)");
        }
        catch (ToaException te) {
            if (te.getMessage().indexOf("it was not registered with the application") == -1) {
                fail("Wrong exception text, expected 'it was not registered with the application' to be present but was: " + te.getMessage());
            }
        }
    }

    @Test
    public void testConflictFactoriesDetected() throws Throwable {
        try {
            createApp("testConflictFactoriesDetected", "standalone", ConflictingFactoryTestApp.class);
            fail("Should not have been able to create app. Factory conflict should have caused failure.");
        }
        catch (Exception e) {
            if (e.getMessage().indexOf("conflicts with") == -1) {
                fail("Wrong exception text, expected 'conflicts with' to be present but was: " + e.getMessage());
            }
        }
    }

    @Test
    public void testAutoRegistrationOfEventHandlerFactory() throws Throwable {
        assertNull("InjectionFactory already in registry", MessageViewFactoryRegistry.getInstance().getMessageViewFactory(InjectionFactory.VFID));
        EventHandlerDrivenFactoryRegistrationTestApp app = createApp("testAutoRegistrationOfEventHandlerFactory", "standalone", EventHandlerDrivenFactoryRegistrationTestApp.class);
        app.getMessageInjector().injectMessage(InjectionMessage1.create());

        app.waitForMessages(10, 1);
        assertEquals("Wrong message type received", InjectionMessage1.class, app.received.get(0).getClass());
    }

    @Test
    public void testAutoRegistrationOfFirstMessageFactory() throws Throwable {
        assertNull("FirstMessageFactory already in registry", MessageViewFactoryRegistry.getInstance().getMessageViewFactory(FirstMessageFactory.VFID));
        FirstMessageTestApp app = createApp("testAutoRegistrationOfFirstMessageFactory", "standalone", FirstMessageTestApp.class);

        app.waitForMessages(10, 1);
        assertEquals("Wrong message type received", FirstMessage1.class, app.received.get(0).getClass());
    }

    @Test
    public void testAutoRegistrationOfInitialMessageFactory() throws Throwable {
        assertNull("InitialMessageFactory already in registry", MessageViewFactoryRegistry.getInstance().getMessageViewFactory(InitialMessageFactory.VFID));
        InitialMessageTestApp app = createApp("testAutoRegistrationOfFirstMessageFactory", "standalone", InitialMessageTestApp.class);
        app.waitForMessages(10, 1);
        assertEquals("Wrong message type received", InitialMessage1.class, app.received.get(0).getClass());
    }

    @Test
    public void testFailFastOfFirstMessage() throws Throwable {
        try {
            FailFastFirstMessageTest app = createApp("testFailFastOfFirstMessage", "standalone", FailFastFirstMessageTest.class);
            app.latch.await(5, TimeUnit.SECONDS);
            assertEquals("Engine should have stopped", AepEngine.State.Stopped, app.getAepEngine().getState());
            assertNotNull("Should have got exception on stop", app.cause);
            if (app.cause.getMessage().indexOf("Can't use 'com.neeve.toa.test.unit.injectiontests.UnhandledInjectionMessage' as a first message") == -1) {
                fail("Wrong exception text, expected 'Can't use 'com.neeve.toa.test.unit.injectiontests.UnhandledInjectionMessage' as a first message' to be present but was: " + app.cause.getMessage());
            }
        }
        catch (IllegalStateException ie) {
            return;
        }

    }

    @Test
    public void testFailFastOfInitialMessage() throws Throwable {
        try {
            FailFastInitialMessageTest app = createApp("testFailFastOfInitialMessage", "standalone", FailFastInitialMessageTest.class);
            app.latch.await(5, TimeUnit.SECONDS);
            assertEquals("Engine should have stopped", AepEngine.State.Stopped, app.getAepEngine().getState());
            assertNotNull("Should have got exception on stop", app.cause);
            if (app.cause.getMessage().indexOf("Can't use 'com.neeve.toa.test.unit.injectiontests.UnhandledInjectionMessage' as an initial message") == -1) {
                fail("Wrong exception text, expected 'Can't use 'com.neeve.toa.test.unit.injectiontests.UnhandledInjectionMessage' as an initial message' to be present but was: " + app.cause.getMessage());
            }
        }
        catch (IllegalStateException ie) {
            return;
        }
    }

    @Test
    public void testBlockingPriorityInjection() throws Throwable {
        SingleAppToaServer<MessageInjectionTestApp> server = createServer(testcaseName.getMethodName(), "standalone", MessageInjectionTestApp.class);
        server.start();
        MessageInjectionTestApp app = server.getApplication();
        ArrayList<IRogMessage> toInject = new ArrayList<IRogMessage>();

        for (int i = 0; i < 5; i++) {
            ForwarderMessage1 m1 = ForwarderMessage1.create();
            m1.setIntField(i);
            ForwarderMessage2 m2 = ForwarderMessage2.create();
            m2.setIntField(i);
            toInject.add(m1);
            toInject.add(m2);
        }

        for (IRogMessage message : toInject) {
            app.getMessageInjector().injectMessage(message, false, -10);
        }

        app.waitForMessages(10, toInject.size());

        assertEquals("Didn't get expected number of injected messages", toInject.size(), app.received.size());
        for (int i = 0; i < toInject.size(); i++) {
            assertSame("Wrong message received by application", toInject.get(i), app.received.get(i));
            assertEquals("Wrong reference count for injected message", 1, app.received.get(i).getOwnershipCount());
        }
    }

    @Test
    public void testNonBlockingPriorityInjection() throws Throwable {
        SingleAppToaServer<MessageInjectionTestApp> server = createServer(testcaseName.getMethodName(), "standalone", MessageInjectionTestApp.class);
        server.start();
        MessageInjectionTestApp app = server.getApplication();
        ArrayList<IRogMessage> toInject = new ArrayList<IRogMessage>();

        for (int i = 0; i < 5; i++) {
            ForwarderMessage1 m1 = ForwarderMessage1.create();
            m1.setIntField(i);
            ForwarderMessage2 m2 = ForwarderMessage2.create();
            m2.setIntField(i);
            toInject.add(m1);
            toInject.add(m2);
        }

        for (IRogMessage message : toInject) {
            app.getMessageInjector().injectMessage(message, true, 10);
        }

        app.waitForMessages(10, toInject.size());

        assertEquals("Didn't get expected number of injected messages", toInject.size(), app.received.size());
        for (int i = 0; i < toInject.size(); i++) {
            assertSame("Wrong message received by application", toInject.get(i), app.received.get(i));
            assertEquals("Wrong reference count for injected message", 1, app.received.get(i).getOwnershipCount());
        }
    }
}
