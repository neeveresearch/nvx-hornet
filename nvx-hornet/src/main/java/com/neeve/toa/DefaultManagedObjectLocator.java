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
package com.neeve.toa;

import java.util.Set;

import com.neeve.managed.AbstractManagedObjectLocator;

/**
 * The default managed object locator for TOA applications.
 * <p>
 * This locator simply merges the set of objects returned by
 * <ul>
 * <li>{@link TopicOrientedApplication#addHandlerContainers(java.util.Set)}
 * <li>{@link TopicOrientedApplication#addAppCommandHandlerContainers(java.util.Set)}
 * <li>{@link TopicOrientedApplication#addAppStatContainers(java.util.Set)}
 * <li>{@link TopicOrientedApplication#addChannelFilterProviders(java.util.Set)}
 * <li>{@link TopicOrientedApplication#addChannelQosProviders(java.util.Set)}
 * <li>{@link TopicOrientedApplication#addChannelInitialKeyResolutionTableProviders(java.util.Set)}
 * <li>{@link TopicOrientedApplication#addTopicResolverProviders(java.util.Set)}
 * <li>{@link TopicOrientedApplication#addConfiguredContainers(java.util.Set)}
 * </ul>
 */
public class DefaultManagedObjectLocator extends AbstractManagedObjectLocator {

    private final TopicOrientedApplication app;

    DefaultManagedObjectLocator(TopicOrientedApplication app) {
        this.app = app;
    }

    /* (non-Javadoc)
     * @see com.neeve.toa.spi.ManagedObjectLocator#locateManagedObjects(java.util.Set)
     */
    @Override
    public void locateManagedObjects(Set<Object> managedObjects) throws Exception {
        app.addHandlerContainers(managedObjects);
        app.addAppCommandHandlerContainers(managedObjects);
        app.addAppStatContainers(managedObjects);
        app.addChannelFilterProviders(managedObjects);
        app.addChannelQosProviders(managedObjects);
        app.addChannelJoinProviders(managedObjects);
        app.addChannelInitialKeyResolutionTableProviders(managedObjects);
        app.addTopicResolverProviders(managedObjects);
        app.addConfiguredContainers(managedObjects);
    }

}
