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
package com.neeve.managed;

import java.util.Set;

import com.neeve.aep.annotations.EventHandler;
import com.neeve.server.app.annotations.AppCommandHandler;

/**
 * Provider interface for locating a Topic Oriented Application's 'managed' objects. 
 * <p>
 * A 'managed' object is one that is of interest to the Talon server or TopicOrientedApplication
 * for introspection (e.g @{@link EventHandler} or @{@link AppCommandHandler} annotated
 * methods.)
 * <p>
 * It legal (though potentially inefficient) for a {@link ManagedObjectLocator} to return
 * objects that end up not being of interest to the application. 
 * <p>
 * A {@link ManagedObjectLocator} <i>may</i> return itself as a managed object in which case
 * the {@link ManagedObjectLocator} itself will be eligible to serve as an event or command
 * handler for the application. This may be useful, for example if the locator assumes responsibility
 * for cleaning up any objects it constructs. 
 */
public interface ManagedObjectLocator {

    /**
     * Populates the set of managed objects for a TopicOrientedApplication. 
     * <p>
     * It is illegal to add <code>null</code> objects to the set of managed objects - doing
     * so will caus application load to fail with an exception. 
     * 
     * @param managedObjects The collection into which to add the service urls. 
     */
    public void locateManagedObjects(final Set<Object> managedObjects) throws Exception;

}
