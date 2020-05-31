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

import static com.neeve.server.embedded.EmbeddedXVM.State.Started;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import com.neeve.aep.AepEngine;
import com.neeve.config.VMConfigurer;
import com.neeve.server.Configurer;
import com.neeve.server.Main;
import com.neeve.server.app.SrvAppLoader;
import com.neeve.server.config.ESrvConfigException;
import com.neeve.server.config.SrvConfigDescriptor;
import com.neeve.server.embedded.EmbeddedXVM;
import com.neeve.toa.TopicOrientedApplication;
import com.neeve.util.UtlStr;
import com.neeve.util.UtlTailoring.PropertySource;

/**
 * Wraps an embedded Talon server for a single {@link TopicOrientedApplication}. 
 * <p>
 * The server uses loopback for both discovery and messaging. The server is
 * not created until start is called which narrows the window for multiple
 * servers with conflicting {@link VMConfigurer} values to conflict. 
 */
public class SingleAppToaServer<T extends TopicOrientedApplication> extends EmbeddedXVM {

    public static final String PROP_NAME_STORE_ENABLED = "store.enabled";
    public static final String PROP_NAME_STORE_CLUSTERING_ENABLED = "store.clustering.enabled";

    private final Class<T> applicationClass;
    private final String appName;
    private final String instanceId;
    private final String serverName;

    private T application;

    private static class ToaSingleAppServerConfigurer<T extends TopicOrientedApplication> implements Configurer, PropertySource {
        private final String appName;

        private final String instanceId;
        private final String serverName;
        private final Class<T> applicationClass;
        private final PropertySource envResolver = new UtlStr.SubstResolverFromEnv();
        final Map<String, String> overrides = new HashMap<String, String>();

        ToaSingleAppServerConfigurer(final String appName, final String instanceId, final Class<T> applicationClass, Map<String, String> configOverrides) {
            this.appName = appName;
            this.instanceId = instanceId;
            this.serverName = appName + "-" + instanceId;
            this.applicationClass = applicationClass;

            System.setProperty("nv.server.autostop.onlastappstop", "false");

            overrides.put("application.name", appName);
            overrides.put("application.server.name", appName + "-" + instanceId);
            overrides.put("application.main.class", applicationClass.getName());
            overrides.put("transport.descriptor", "loopback://.");
            overrides.put("store.discovery.descriptor", "loopback://clusterdiscovery&initWaitTime=2");
            overrides.put("server.discoveryDescriptor", "local://serverdiscovery&initWaitTime=0");
            if (configOverrides != null) {
                overrides.putAll(configOverrides);
                System.getProperties().putAll(overrides);
            }
        }

        /* (non-Javadoc)
         * @see com.neeve.server.Configurer#configure(java.lang.String[])
         */
        @Override
        public String[] configure(String[] args) throws Exception {
            URL overlayUrl = SingleAppToaServer.class.getResource("/conf/platform.xml");
            File overlayConfig = new File(overlayUrl.toURI());
            VMConfigurer.configure(overlayConfig, this);
            return new String[] { "--name", serverName };
        }

        /* (non-Javadoc)
         * @see com.neeve.util.UtlTailoring.PropertySource#getValue(java.lang.String, java.lang.String)
         */
        @Override
        public String getValue(String key, String defaultValue) {
            String override = overrides.get(key);
            if (override != null) {
                return override;
            }
            return envResolver.getValue(key, defaultValue);
        }

        public final String getAppName() {
            return appName;
        }

        public final String getInstanceId() {
            return instanceId;
        }

        public final String getServerName() {
            return serverName;
        }

        public final Class<T> getApplicationClass() {
            return applicationClass;
        }
    }

    /**
     * Private access constructor used by the static creation method. 
     * 
     * @param appName The name of the application, used to name the app and server. 
     * @param applicationClass The application class. 
     * @throws ESrvConfigException If there is a con
     */
    private SingleAppToaServer(final ToaSingleAppServerConfigurer<T> configurer, SrvConfigDescriptor descriptor) {
        super(configurer, descriptor);
        this.appName = configurer.getAppName();
        this.instanceId = configurer.getInstanceId();
        this.serverName = configurer.getServerName();
        this.applicationClass = configurer.getApplicationClass();
    }

    /**
     * Creates an {@link SingleAppToaServer} for the provided {@link TopicOrientedApplication}. 
     * <p>
     * The provided appName is used to create the application's underlying {@link AepEngine}. The
     * application name is also used as the store name which serves as the unit of clustering for applications.
     * <p>
     * A server name must be unique within a servers discovery domain. The instanceId provided
     * here is used in conjunction with the appName to create a server name as {appName}-{instanceId}.
     * This allows applications to create multiple {@link SingleAppToaServer}s with the same appName.
     * <p>
     * A particular app instance can thus be uniquely identified via the coordinates: {serverName},{appName}, 
     * so to create a 'primary' and 'backup' server instance one could do:
     * 
     * <pre>
     * final EmbeddedServer<OrderProcessingApp> primary = EmbeddedServer.create("orderProcessing", "primary", OrderProcessing.class);
     * primary.setProperty(PROP_NAME_STORE_ENABLED, true);
     * primary.start();
     * final EmbeddedServer<OrderProcessingApp> backup = EmbeddedServer.create("orderProcessing", "backup", OrderProcessing.class);
     * primary.setProperty(PROP_NAME_STORE_ENABLED, true);
     * backup.start();
     * </pre>
     * 
     * @param appName The appName to use. The appName is used for the name of the underlying engine and is used to identify the application for clustering purposes. 
     * @param instanceId An instanceId. The instanceId identifies the particular application instance within a cluster.  
     * @param applicationClass The application class.
     * @param configOverrides Additional configuration overrides
     *  
     * @return The server.
     */
    public static final <T extends TopicOrientedApplication> SingleAppToaServer<T> create(final String appName, final String instanceId, final Class<T> applicationClass, Map<String, String> configOverrides) {
        ToaSingleAppServerConfigurer<T> configurer = new ToaSingleAppServerConfigurer<T>(appName, instanceId, applicationClass, configOverrides);
        try {
            SrvConfigDescriptor descriptor = Main.seedServerConfig(configurer, new String[] {});
            return new SingleAppToaServer<T>(configurer, descriptor);
        }
        catch (Exception e) {
            throw new RuntimeException("Error loading toa server", e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onStartEnd(Throwable error) throws Exception {
        if (error == null) {
            SrvAppLoader loader = getServerController().getAppManager().getAppLoader(appName);

            if (loader != null) {
                Object main = loader.getAppMain();
                if (applicationClass == main.getClass()) {
                    application = (T)main;
                }
                else {
                    throw new Exception("Application '" + appName + "' main class was expected to be type '"
                            + applicationClass.getCanonicalName() + "' but loaded class was '" + main.getClass().getCanonicalName() + ".");
                }
            }
        }
    }

    /**
     * Gets the name of the application this server is hosting. 
     * 
     * @return The server's name.
     */
    public String getAppName() {
        return appName;
    }

    /**
     * Gets the instanceid of the application. 
     * 
     * @return The server's name.
     */
    public String getInstanceId() {
        return instanceId;
    }

    /**
     * Gets the server name ({appNAme}-{instanceId})
     * @return The server's name.
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Gets the loaded application.
     * 
     * @return The application
     */
    public T getApplication() {
        if (application != null) {
            return application;
        }
        if (getState() != Started) {
            throw new IllegalStateException("Application not available until server started");
        }
        throw new IllegalStateException("Application not loaded.");
    }

    @Override
    public String toString() {
        return "ToaTestServer " + getServerName() + " (" + applicationClass.getSimpleName() + ")";
    }
}