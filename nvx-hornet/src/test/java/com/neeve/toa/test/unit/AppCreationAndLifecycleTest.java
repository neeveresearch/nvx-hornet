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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.neeve.aep.AepEngine;
import com.neeve.aep.AepEngine.HAPolicy;
import com.neeve.aep.AepEngine.MessagingState;
import com.neeve.aep.AepEngineDescriptor;
import com.neeve.aep.annotations.EventHandler;
import com.neeve.aep.event.AepEngineActiveEvent;
import com.neeve.aep.event.AepEngineCreatedEvent;
import com.neeve.aep.event.AepEngineStartedEvent;
import com.neeve.aep.event.AepMessagingPrestartEvent;
import com.neeve.aep.event.AepMessagingStartedEvent;
import com.neeve.server.app.SrvAppLoader;
import com.neeve.server.app.annotations.AppHAPolicy;
import com.neeve.server.app.annotations.AppInjectionPoint;

/**
 * Tests for MessageInjection via TOA
 */
public class AppCreationAndLifecycleTest extends AbstractToaTest {

    @AppHAPolicy(HAPolicy.EventSourcing)
    private static class InaccessableClassApp extends AbstractToaTestApp {
        @AppInjectionPoint
        public void configureAepEngine(final AepEngineDescriptor descriptor) {}
    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static class InvalidAepEngineDescriptorAppInjectionPointApp extends AbstractToaTestApp {
        @AppInjectionPoint
        public void configureAepEngine(final AepEngineDescriptor descriptor) {}
    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static class InvalidSrvAppLoaderInjectionPointApp extends AbstractToaTestApp {
        @AppInjectionPoint
        public void configureAepEngine(final SrvAppLoader loader) {}
    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static class InvalidAepEngineInjectionPointApp extends AbstractToaTestApp {
        @AppInjectionPoint
        public void configureAepEngine(final AepEngine loader) {}
    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static class StartupLifecycleTestApp extends AbstractToaTestApp {

        volatile SrvAppLoader appLoader;
        volatile AepEngineDescriptor engineDescriptor;
        volatile AepEngine engine;

        volatile Throwable error;

        private List<String> expectedOrder = Arrays.asList(
                new String[] {
                              "onAppLoaderInjected",
                              "onEngineDescriptorInjected",
                              "onConfigured",
                              "onAepEngineCreatedEvent",
                              "onEngineInjected",
                              "onAepEngineStartedEvent",
                              "onAepMessagingPrestartEvent",
                              "onAepMessagingStartedEvent",
                              "onAepEngineActiveEvent"

                });
        private List<String> actualOrder = new ArrayList<String>();

        @Override
        protected void onAppLoaderInjected(final SrvAppLoader appLoader) {
            super.onAppLoaderInjected(appLoader);
            actualOrder.add("onAppLoaderInjected");
            try {
                assertNull("Duplicate SrvAppLoader Injection in onAppLoaderInjected()", this.appLoader);
                this.appLoader = appLoader;
                assertNotNull("Null SrvAppLoader injected in onAppLoaderInjected()", appLoader);
                assertServiceModelsInaccessible();
                assertNull("AepEngineDescriptor was non null at onAppLoaderInjected()", engineDescriptor);
                assertNull("AepEngine was non null at onAppLoaderInjected()", engine);
            }
            catch (Throwable thrown) {
                error = thrown;
            }
        }

        @Override
        protected void onEngineDescriptorInjected(final AepEngineDescriptor engineDescriptor) {
            actualOrder.add("onEngineDescriptorInjected");
            try {
                assertNull("Duplicate AepEngineDescriptor Injection in onEngineInjected()", this.engineDescriptor);
                this.engineDescriptor = engineDescriptor;
                assertServiceModelsInaccessible();
                assertNotNull("Null AepEngineDescriptor injected in onEngineInjected()", engineDescriptor);
                assertNotNull("SrvAppLoader was null at onEngineInjected()", appLoader);
                assertNull("AepEngine was non null at onEngineInjected()", engine);
            }
            catch (Throwable thrown) {
                error = thrown;
            }
        }

        @Override
        protected void onConfigured() {
            actualOrder.add("onConfigured");
            try {
                assertNull("AepEngine was non null at onConfigured()", engine);
                assertNotNull("SrvAppLoader was null at onConfigured()", appLoader);
                assertNotNull("AepEngineDescriptor was null at onConfigured()", engineDescriptor);
                assertNotNull("Service models were null at onConfigured()", super.getServiceModels());
                assertFalse("Service model collection was empty at onConfigured()", getServiceModels().isEmpty());
            }
            catch (Throwable thrown) {
                error = thrown;
            }
        }

        @Override
        protected void onEngineInjected(final AepEngine engine) {
            actualOrder.add("onEngineInjected");
            try {
                assertNull("Duplicate Engine Injection in onEngineInjected()", this.engine);
                assertNotNull("Null engine injected in onEngineInjected()", engine);
                assertNotNull("SrvAppLoader was null at onEngineInjected()", appLoader);
                assertNotNull("AepEngineDescriptor was null at onEngineInjected()", engineDescriptor);
                this.engine = engine;
            }
            catch (Throwable thrown) {
                error = thrown;
            }
        }

        private void assertServiceModelsInaccessible() {
            try {
                getServiceModels();
                fail("Shouldn't be able to access service models");
            }
            catch (IllegalStateException ise) {}

            try {
                getServiceModel("forwarder");
                fail("Shouldn't be able to access service model");
            }
            catch (IllegalStateException ise) {}
        }

        @EventHandler
        public void onAepEngineStartedEvent(AepEngineStartedEvent started) {
            actualOrder.add("onAepEngineStartedEvent");
        }

        @EventHandler
        public void onAepMessagingPrestartEvent(AepMessagingPrestartEvent event) {
            actualOrder.add("onAepMessagingPrestartEvent");
        }

        @EventHandler
        public void onAepMessagingStartedEvent(AepMessagingStartedEvent event) {
            actualOrder.add("onAepMessagingStartedEvent");
        }

        @EventHandler
        public void onAepEngineCreatedEvent(AepEngineCreatedEvent event) {
            actualOrder.add("onAepEngineCreatedEvent");
        }

        @EventHandler
        public void onAepEngineActiveEvent(AepEngineActiveEvent event) {
            actualOrder.add("onAepEngineActiveEvent");
        }

        public void assertLoadedAsExpected() throws Throwable {
            if (error != null) {
                throw error;
            }
            assertNotNull("SrvAppLoader was not injected!", appLoader);
            assertNotNull("AepEngineDescriptor was not injected!", engineDescriptor);
            assertNotNull("AepEngine was not injected!", engine);
            assertEquals("Lifecycle calls not as expected", expectedOrder, actualOrder);
        }
    }

    @Test
    public void testInaccessableClassApp() throws Throwable {
        SingleAppToaServer<InaccessableClassApp> server = createServer(testcaseName.getMethodName(), "standalone", InaccessableClassApp.class);
        try {
            server.start();
            fail("App shouldn't have loaded");
        }
        catch (Throwable thrown) {
            assertNotNull("Expected a startup error from the server", server.getStartupError());
            final String expectedExceptionText = "the default constructor could not be found";
            final String actualMessage = server.getStartupError().getMessage();
            assertTrue("Unexpected exception: " + actualMessage + " expected '" + expectedExceptionText + "'", actualMessage.indexOf(expectedExceptionText) >= 0);
        }

    }

    @Test
    public void testInvalidAepEngineDescriptorAppInjectionPointApp() throws Throwable {
        SingleAppToaServer<InvalidAepEngineDescriptorAppInjectionPointApp> server = createServer(testcaseName.getMethodName(), "standalone", InvalidAepEngineDescriptorAppInjectionPointApp.class);
        try {
            server.start();
            fail("App shouldn't have loaded");
        }
        catch (Throwable thrown) {
            assertNotNull("Expected a startup error from the server", server.getStartupError());
            final String expectedExceptionText = "Usage of AppInjectionPoint annotation is unsupported";
            final String actualMessage = server.getStartupError().getMessage();
            assertTrue("Unexpected exception: " + actualMessage + " expected '" + expectedExceptionText + "'", actualMessage.indexOf(expectedExceptionText) >= 0);
        }
    }

    @Test
    public void testInvalidSrvAppLoaderDescriptorAppInjectionPointApp() throws Throwable {
        SingleAppToaServer<InvalidSrvAppLoaderInjectionPointApp> server = createServer(testcaseName.getMethodName(), "standalone", InvalidSrvAppLoaderInjectionPointApp.class);
        try {
            server.start();
            fail("App shouldn't have loaded");
        }
        catch (Throwable thrown) {
            assertNotNull("Expected a startup error from the server", server.getStartupError());
            final String expectedExceptionText = "Usage of AppInjectionPoint annotation is unsupported";
            final String actualMessage = server.getStartupError().getMessage();
            assertTrue("Unexpected exception: " + actualMessage + " expected '" + expectedExceptionText + "'", actualMessage.indexOf(expectedExceptionText) >= 0);
        }
    }

    @Test
    public void testInvalidAepEngineAppInjectionPointApp() throws Throwable {
        SingleAppToaServer<InvalidAepEngineInjectionPointApp> server = createServer(testcaseName.getMethodName(), "standalone", InvalidAepEngineInjectionPointApp.class);
        try {
            server.start();
            fail("App shouldn't have loaded");
        }
        catch (Throwable thrown) {
            assertNotNull("Expected a startup error from the server", server.getStartupError());
            final String expectedExceptionText = "Usage of AppInjectionPoint annotation is unsupported";
            final String actualMessage = server.getStartupError().getMessage();
            assertTrue("Unexpected exception: " + actualMessage + " expected '" + expectedExceptionText + "'", actualMessage.indexOf(expectedExceptionText) >= 0);
        }
    }

    @Test
    public void testAppStartupLifecycle() throws Throwable {
        SingleAppToaServer<StartupLifecycleTestApp> server = createServer(testcaseName.getMethodName(), "standalone", StartupLifecycleTestApp.class);
        server.start();
        server.getApplication().engine.waitForMessagingToStart();
        server.getApplication().assertLoadedAsExpected();
    }

    @Test
    public void testMalformedBusUrl() throws Throwable {
        Map<String, String> configOverrides = new HashMap<String, String>();
        configOverrides.put("transport.descriptor", "solace://dummy:2000&amp;foo=foo=bar");
        SingleAppToaServer<StartupLifecycleTestApp> server = createServer(testcaseName.getMethodName(), "standalone", StartupLifecycleTestApp.class, configOverrides);
        server.start();
        try {
            server.getApplication().getAepEngine().waitForMessagingToStart();
        }
        catch (Exception e) {
            System.out.println("Wait for message start threw error: " + e.getMessage());
        }
        finally {
            assertEquals("Expected messaging to be stopped", MessagingState.Stopped, server.getApplication().getAepEngine().getMessagingState());
        }
    }
}
