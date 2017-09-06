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

import static com.neeve.toa.test.unit.SingleAppToaServer.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.neeve.aep.AepEngine.HAPolicy;
import com.neeve.aep.annotations.EventHandler;
import com.neeve.ods.IStoreBinding;
import com.neeve.server.app.annotations.AppHAPolicy;

/**
 * 
 */
public class ClusteringTest extends AbstractToaTest {

    /**
     * Tests a non clusterd app that does not need to be annotated
     * with an {@link AppHAPolicy}
     */
    public static class NonClusteredApp extends AbstractToaTestApp {

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
    public static class ClusteredEventSourcingApp extends AbstractToaTestApp {

        @EventHandler
        public void onForwarderMessage1(ForwarderMessage1 message) {
            recordReceipt(message);
        }

        @EventHandler
        public void onForwarderMessage2(ForwarderMessage2 message) {
            recordReceipt(message);
        }
    }

    @Test
    public void testClusteredApp() throws Throwable {
        Map<String, String> configOverrides = new HashMap<String, String>();
        configOverrides.put(PROP_NAME_STORE_ENABLED, "true");
        configOverrides.put(PROP_NAME_STORE_CLUSTERING_ENABLED, "true");

        SingleAppToaServer<ClusteredEventSourcingApp> primaryServer = createServer(testcaseName.getMethodName(), "primary", ClusteredEventSourcingApp.class, configOverrides);
        primaryServer.start();
        ClusteredEventSourcingApp primaryApp = primaryServer.getApplication();
        assertNotNull("Primary app should have a store", primaryApp.getAepEngine().getStore());

        SingleAppToaServer<ClusteredEventSourcingApp> backupServer = createServer(testcaseName.getMethodName(), "backup", ClusteredEventSourcingApp.class, configOverrides);
        backupServer.start();
        ClusteredEventSourcingApp backupApp = backupServer.getApplication();
        assertNotNull("Backup app should have a store", backupApp.getAepEngine().getStore());

        assertEquals("Primary app doesn't have expected role", IStoreBinding.Role.Primary, primaryApp.getAepEngine().getStore().getRole());
        assertEquals("Backup app doesn't have expected role", IStoreBinding.Role.Backup, backupApp.getAepEngine().getStore().getRole());

        assertNotNull("Primary app doesn't have a persister", primaryApp.getAepEngine().getStore().getPersister());
        assertNotNull("Backup app doesn't have a persister", primaryApp.getAepEngine().getStore().getPersister());

        primaryApp.injectMessage(ForwarderMessage1.create());
        primaryApp.injectMessage(ForwarderMessage2.create());

        System.out.println("Waiting for messages receipt...");
        backupApp.waitForMessages(5, 2);
        primaryApp.waitForMessages(5, 2);

        //Wait for transaction stability:
        System.out.println("Waiting for transaction stability...");
        primaryApp.waitForTransactionStability(5);
        backupApp.waitForTransactionStability(5);

        //System.out.println("Commits on backup: " + backupApp.getAepEngine().getStats().getNumCommitsCompleted() + "/" + +backupApp.getAepEngine().getStats().getNumCommitsStarted());
        System.out.println("Comparing primary and backup messages...");
        assertPrimaryAndBackupMessageEqual(primaryApp, backupApp);
    }

    @Test
    public void testNonClusteredApp() throws Throwable {
        Map<String, String> configOverrides = new HashMap<String, String>();
        configOverrides.put(PROP_NAME_STORE_CLUSTERING_ENABLED, "false");

        SingleAppToaServer<NonClusteredApp> server = createServer(testcaseName.getMethodName(), "standalone", NonClusteredApp.class, configOverrides);
        server.start();
        NonClusteredApp app = server.getApplication();
        assertNull("Standalone app should not have a store", app.getAepEngine().getStore());
    }

}
