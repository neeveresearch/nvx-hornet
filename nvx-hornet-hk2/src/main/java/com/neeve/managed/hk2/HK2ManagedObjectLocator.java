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
package com.neeve.managed.hk2;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.ServiceLocatorFactory;
import org.glassfish.hk2.api.ServiceLocatorFactory.CreatePolicy;
import org.glassfish.hk2.utilities.Binder;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;

import com.neeve.ci.XRuntime;
import com.neeve.managed.ManagedObjectLocator;
import com.neeve.managed.annotations.Managed;
import com.neeve.root.RootConfig;
import com.neeve.toa.TopicOrientedApplication;
import com.neeve.trace.Tracer;

/**
 * A {@link ManagedObjectLocator} that uses {@link Binder} HK2 Modules to discover the {@link Managed} objects in the {@link TopicOrientedApplication}. 
 */
public class HK2ManagedObjectLocator implements ManagedObjectLocator {

    /**
     * Property used to control whether the Immediate scrope is enabled on ServiceLocators.  
     * <p>
     * <b>Property name:</b> {@value #PROP_ENABLE_HK2_IMMEDIATE_SCOPE}
     * <br>
     * <b>Default value:</b> {@value #PROP_ENABLE_HK2_IMMEDIATE_SCOPE_DEFAULT}
     * <br>
     * @see #PROP_ENABLE_HK2_IMMEDIATE_SCOPE_DEFAULT
     */
    public static final String PROP_ENABLE_HK2_IMMEDIATE_SCOPE = "nv.toa.hk2.enableimmediatescope";

    /**
     * The default value for {@link #PROP_ENABLE_HK2_IMMEDIATE_SCOPE} ({@value #PROP_ENABLE_HK2_IMMEDIATE_SCOPE_DEFAULT}).
     */
    public static final boolean PROP_ENABLE_HK2_IMMEDIATE_SCOPE_DEFAULT = false;

    final protected static Tracer tracer = RootConfig.ObjectConfig.createTracer(RootConfig.ObjectConfig.get("nv.toa"));
    final private TopicOrientedApplication application;
    final private List<Binder> applicationModules;
    final private String applicationName;

    private ServiceLocator applicationServiceLocator;

    /**
     * Constructs an {@link HK2ManagedObjectLocator}. 
     * 
     * @param application The {@link TopicOrientedApplication} that owns this locator. 
     * @param applicationName The name of the application (which is passed to {@link #createServiceLocator(String)}. 
     * @param applicationModules The List of {@link Binder}s
     */
    public HK2ManagedObjectLocator(TopicOrientedApplication application, String applicationName, List<Binder> applicationModules) {
        this.application = application;
        this.applicationName = applicationName;
        this.applicationModules = applicationModules;
        applicationServiceLocator = initializeApplicationServiceLocator();
    }

    @Override
    public void locateManagedObjects(Set<Object> managedObjects) throws Exception {
        List<Object> applicationManagedObjects = findManagedObjects();

        if (applicationManagedObjects.isEmpty())
            tracer.log("No Managed Objects found", Tracer.Level.WARNING);

        if (tracer.debug) {
            for (Object object : applicationManagedObjects) {
                tracer.log(this + " found ManagedObject=" + object, Tracer.Level.DEBUG);
            }
        }

        //Inject Service Locator in managed objects
        for (Object object : applicationManagedObjects) {
            applicationServiceLocator.inject(object);
        }

        managedObjects.addAll(applicationManagedObjects);
    }

    private List<Object> findManagedObjects() {
        List<ServiceHandle<?>> serviceHandles = applicationServiceLocator.getAllServiceHandles(new ManagedImpl());
        List<Object> managedObjects = new ArrayList<Object>();
        for (ServiceHandle<?> serviceHandle : serviceHandles) {
            managedObjects.add(serviceHandle.getService());
        }
        return managedObjects;
    }

    private ServiceLocator initializeApplicationServiceLocator() {
        ServiceLocator serviceLocator = createServiceLocator(applicationName);
        if (XRuntime.getValue(PROP_ENABLE_HK2_IMMEDIATE_SCOPE, PROP_ENABLE_HK2_IMMEDIATE_SCOPE_DEFAULT)) {
            ServiceLocatorUtilities.enableImmediateScope(serviceLocator);
        }
        List<Binder> modules = new ArrayList<Binder>(createPlatformModules());
        modules.addAll(applicationModules);
        ServiceLocatorUtilities.bind(serviceLocator, modules.toArray(new Binder[modules.size()]));
        return serviceLocator;
    }

    /**
     * Construct the <strong>Platform Modules</strong>. These are the modules that are logically provided by the Platform and should be
     * included by default in every {@link TopicOrientedApplication}.
     * 
     * @return the platform modules
     */
    protected List<Binder> createPlatformModules() {
        List<Binder> binders = new ArrayList<Binder>();
        binders.add(new PlatformModules(application));
        return binders;
    }

    /**
     * Construct the {@link ServiceLocator} for the {@link TopicOrientedApplication}. 
     * 
     * @param applicationName The human-readable name of the {@link TopicOrientedApplication}. This should be used as the name for the  {@link ServiceLocator}.
     * @return the {@link ServiceLocator} for the {@link TopicOrientedApplication}
     */
    protected ServiceLocator createServiceLocator(String applicationName) {
        return ServiceLocatorFactory.getInstance().create(applicationName, null, null, CreatePolicy.ERROR);
    }

    /**
     * Destroy the Application Service Locator and any {@link HK2ManagedObjectLocator#locateManagedObjects(Set) Managed Objects} it may 
     * have created. 
     */

    public void destroy() {
        applicationServiceLocator.shutdown();
    }

    /**
     * Get the <strong>Application Service Locator</strong>. Each {@link TopicOrientedApplication} is provided with its own 
     * {@link ServiceLocator} that will be different from that used by any other {@link TopicOrientedApplication}.
     * 
     * @return the Application Service Locator
     */

    public ServiceLocator getApplicationServiceLocator() {
        return applicationServiceLocator;
    }
}
