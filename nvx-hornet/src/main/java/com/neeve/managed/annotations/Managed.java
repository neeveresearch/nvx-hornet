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
package com.neeve.managed.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.inject.Qualifier;

import com.neeve.aep.annotations.EventHandler;
import com.neeve.server.app.annotations.AppCommandHandler;

/**
 * The {@link Managed} qualifier indicates an object is of interest to the Rumi Server runtime for fulfilling application functionality.  
 * 
 * <h2>Managed Objects</h2>
 * <p>A Rumi Application provides certain objects such as Event Handler Containers to the Rumi Server. These objects will then by 
 * used by the Rumi Server; for example, Event Handler Containers will have their {@link EventHandler} methods invoked each time the 
 * Rumi Server receives a message from a messaging bus. These objects that are made available to the Rumi Server and will be primarily
 * invoked by the Rumi Server are called <strong>Managed Objects</strong>.</p>
 * 
 * <p>There are a couple of different types of Managed Objects that may be made available to the Rumi Server for introspect. They include:</p>
 * 
 * <ul>
 *   <li><p><strong>Event Handler Containers:</strong> Objects that have annotated {@link EventHandler} methods.</p></li>
 *   <li><p><strong>AppCommand Handler Containers:</strong> Objects that have annotated {@link AppCommandHandler} methods.</p></li>
 * </ul> 
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Qualifier
public @interface Managed {}