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

import static com.neeve.toa.test.unit.ManagedObjectLocatorTest.TestEvent.CommandHandlerContainerCommand;
import static com.neeve.toa.test.unit.ManagedObjectLocatorTest.TestEvent.ContainerWithCommandAndEventHandlerCommand;
import static com.neeve.toa.test.unit.ManagedObjectLocatorTest.TestEvent.ContainerWithCommandAndEventHandlerMessage;
import static com.neeve.toa.test.unit.ManagedObjectLocatorTest.TestEvent.EventHandlerContainerMessage;
import static com.neeve.toa.test.unit.ManagedObjectLocatorTest.TestEvent.EventManagedObjectLocatorAccessed;
import static com.neeve.toa.test.unit.ManagedObjectLocatorTest.TestEvent.StaticManagedObjectLocatorLocateManagedObjects;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.neeve.aep.AepEngine.HAPolicy;
import com.neeve.aep.annotations.EventHandler;
import com.neeve.managed.AbstractManagedObjectLocator;
import com.neeve.managed.ManagedObjectLocator;
import com.neeve.server.app.annotations.AppCommandHandler;
import com.neeve.server.app.annotations.AppHAPolicy;
import com.neeve.server.app.annotations.AppStat;
import com.neeve.server.mon.SrvMonAppStats;
import com.neeve.server.mon.SrvMonHeartbeatMessage;
import com.neeve.server.mon.SrvMonUserCounterStat;
import com.neeve.stats.IStats.Counter;
import com.neeve.stats.StatsFactory;
import com.neeve.toa.TopicOrientedApplication;

/**
 * Test {@link ManagedObjectLocator} API
 */
public class ManagedObjectLocatorTest extends AbstractToaTest {

    public static enum TestEvent {
        EventManagedObjectLocatorAccessed("getManagedObjectLocator()"),
        StaticManagedObjectLocatorLocateManagedObjects("StaticManagedObjectLocator.locateManagedObjects()"),
        CommandHandlerContainerCommand("CommandHandlerContainerCommand.testCommand()"),
        EventHandlerContainerMessage("EventHandlerContainer.onMessageCalled()"),
        ContainerWithCommandAndEventHandlerCommand("ContainerWithCommandAndEventHandler.testCommand()"),
        ContainerWithCommandAndEventHandlerMessage("ContainerWithCommandAndEventHandlerCommand.onMessage()");

        private final String description;

        private TestEvent(String description) {
            this.description = description;
        }

        public String toString() {
            return description;
        }
    }

    private static List<TestEvent> events = new ArrayList<TestEvent>();

    private static final void recordEvent(TestEvent event) {
        synchronized (events) {
            events.add(event);
            events.notifyAll();
        }
    }

    private static final void assertEventsReceived(long timeoutSeconds, int numEvents) throws InterruptedException {
        long timeout = System.currentTimeMillis() + (timeoutSeconds * 1000);
        synchronized (events) {
            while (events.size() < numEvents && System.currentTimeMillis() < timeout) {
                events.wait(Math.max(1, timeout - System.currentTimeMillis()));
            }

            assertTrue("Didn't received enough events. Expected: " + numEvents + " but got: " + events.size(), events.size() >= numEvents);
        }
    }

    /**
     * A static managed object locator. 
     */
    public static class StaticManagedObjectLocator extends AbstractManagedObjectLocator {
        ContainerWithCommandAndEventHandler commandAndEventHandlerContainer = new ContainerWithCommandAndEventHandler();
        CommandHandlerContainer commandHandlerContainer = new CommandHandlerContainer();
        EventHandlerContainer eventHandlerContainer = new EventHandlerContainer();

        /* (non-Javadoc)
         * @see com.neeve.toa.spi.ManagedObjectLocator#locateManagedObjects(java.util.Set)
         */
        @Override
        public void locateManagedObjects(Set<Object> managedObjects) throws Exception {
            recordEvent(StaticManagedObjectLocatorLocateManagedObjects);
            managedObjects.add(commandAndEventHandlerContainer);
            managedObjects.add(commandHandlerContainer);
            managedObjects.add(eventHandlerContainer);
        }
    }

    public static class ContainerWithCommandAndEventHandler {
        volatile boolean commandFired = false;
        volatile boolean messageReceived = false;

        @AppCommandHandler(command = "testCommand")
        public String testCommand(String command, String[] args) {
            recordEvent(ContainerWithCommandAndEventHandlerCommand);
            commandFired = true;
            return "ok";
        }

        @EventHandler
        public void onMessage(ReceiverMessage1 message) {
            recordEvent(ContainerWithCommandAndEventHandlerMessage);
            messageReceived = true;
        }
    }

    public static class CommandHandlerContainer {
        volatile boolean commandFired = false;

        @AppCommandHandler(command = "testCommandOnly")
        public String testCommand(String command, String[] args) {
            recordEvent(CommandHandlerContainerCommand);
            commandFired = true;
            return "ok";
        }
    }

    public static class EventHandlerContainer {
        volatile boolean messageReceived = false;

        @EventHandler
        public void onMessage(ReceiverMessage1 message) {
            recordEvent(EventHandlerContainerMessage);
            messageReceived = true;
        }
    }

    /**
     * Tests an app with a custom {@link ManagedObjectLocator}
     */
    @AppHAPolicy(HAPolicy.EventSourcing)
    public static class ManagedObjectLocatorTestApp extends AbstractToaTestApp {
        StaticManagedObjectLocator locator = new StaticManagedObjectLocator();

        @Override
        public ManagedObjectLocator getManagedObjectLocator() {
            recordEvent(EventManagedObjectLocatorAccessed);
            return locator;
        }
    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static class TestUserStatContainerApp extends TopicOrientedApplication {

        StatContainer statContainer = new StatContainer();

        CountDownLatch heartbeatLatch = new CountDownLatch(1);
        int collectionIndex = 0;
        private volatile long[] collectedCounts = new long[5];

        public TestUserStatContainerApp() {
            super();
        }

        @EventHandler
        public void onHeartbeat(SrvMonHeartbeatMessage message) {
            for (SrvMonAppStats appStats : message.getAppsStats()) {
                if (appStats.getAppName().equals(getEngine().getName())) {
                    if (appStats.getUserStats() == null) {
                        continue;
                    }
                    for (SrvMonUserCounterStat counter : appStats.getUserStats().getCounters()) {
                        if (counter.getName().equalsIgnoreCase(statContainer.numHeartbeats.getName())) {
                            if (collectionIndex < collectedCounts.length) {
                                collectedCounts[collectionIndex++] = counter.getCount();
                            }
                            statContainer.numHeartbeats.increment();
                        }
                    }
                }
            }

            if (collectionIndex == collectedCounts.length) {
                heartbeatLatch.countDown();
            }
        }

        /* (non-Javadoc)
         * @see com.neeve.toa.spi.ManagedObjectLocator#locateManagedObjects(java.util.Set)
         */
        @Override
        protected void addAppStatContainers(Set<Object> containers) {
            containers.add(statContainer);
        }

        public boolean waitForHeartbeat(long seconds) throws InterruptedException {
            return heartbeatLatch.await(seconds, TimeUnit.SECONDS);
        }

    }

    private static class StatContainer {
        @AppStat
        Counter numHeartbeats = StatsFactory.createCounterStat("Heartbeats Received");

        StatContainer() {

        }

    }

    @Test
    public void testManagedObjectDiscovery() throws Throwable {
        ManagedObjectLocatorTestApp app = createApp(testcaseName.getMethodName(), "standalone", ManagedObjectLocatorTestApp.class);

        //Inject a message and wait for both handlers to get called.
        //The ContainerWithCommandAndEventHandler should be called first
        //since it is supplied first as a container:
        app.injectMessage(ReceiverMessage1.create());
        assertEventsReceived(5, 4);

        //Invoke commands and make sure they are dispatched appropriately:
        app.getAppLoader().getAppManager().issueAppCommand(testcaseName.getMethodName(), "testCommandOnly", null);
        app.getAppLoader().getAppManager().issueAppCommand(testcaseName.getMethodName(), "testCommand", null);
        List<TestEvent> expected = Arrays.asList(new TestEvent[] {
                                                                  EventManagedObjectLocatorAccessed,
                                                                  StaticManagedObjectLocatorLocateManagedObjects,
                                                                  ContainerWithCommandAndEventHandlerMessage,
                                                                  EventHandlerContainerMessage,
                                                                  CommandHandlerContainerCommand,
                                                                  ContainerWithCommandAndEventHandlerCommand
        });

        try
        {
            assertEventsReceived(5, 6);
        }
        finally {
            assertEquals("Didn't get expected events", expected, events);
        }

    }

    @Test
    public final void testUserCounterStatInContainer() throws Throwable {
        Map<String, String> config = new HashMap<String, String>();
        config.put("nv.server.stats.enable", "true");
        config.put("nv.server.stats.interval", "1000");
        TestUserStatContainerApp testApp = createApp("testUserCounterStatInContainer", "standalone", TestUserStatContainerApp.class, config);

        Thread.sleep(10000);
        assertTrue("Didn't get server heartbeat", testApp.waitForHeartbeat(10));
        //Check the values:
        for (int i = 0; i < testApp.collectedCounts.length; i++) {
            assertEquals("Unexpected stat count for heartbeat " + (i + 1), i, testApp.collectedCounts[i]);
        }
    }
}
