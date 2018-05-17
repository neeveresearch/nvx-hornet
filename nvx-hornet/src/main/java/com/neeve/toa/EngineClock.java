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

import com.neeve.aep.AepEngine;
import com.neeve.aep.AepEngine.HAPolicy;
import com.neeve.util.UtlTime;

/**
 * Provides access to time in an HA Consistent fashion for Topic Oriented
 * applications.
 */
public interface EngineClock {

    /**
     * Returns the current time in an HA consistent fashion. 
     * <p>
     * This method is intended for use by applications using {@link HAPolicy#EventSourcing EventSourcing} that do time 
     * dependent message processing. This method provides the current wall time (in millisecond resolution) 
     * as perceived by the {@link AepEngine}. If invoked from within a message processor handler and the HA policy 
     * is set to {@link HAPolicy#EventSourcing EventSourcing}, this method returns the time stamped on the message event (stamped just 
     * before the method is dispatched to the application for processing). Since, for {@link HAPolicy#EventSourcing EventSourcing} applications, 
     * the message is also replicated for parallel processing on the backup, the backup will receive the same 
     * time when invoking this method thus ensuring identical processing. If this method is called on an engine 
     * operating in state replication mode or called from outside a message processor, then this method will 
     * return the value returned by {@link System#currentTimeMillis()}.
     * 
     * @return The current time in milliseconds. 
     * @see AepEngine#getEngineTime()
     */
    public long getTime();

    /**
     * Returns the current time in microseconds in an HA consistent fashion. 
     * <p>
     * This method is intended for use by applications using {@link HAPolicy#EventSourcing EventSourcing} that do time 
     * dependent message processing. This method provides the current time (in microsecond resolution) 
     * as perceived by the {@link AepEngine}. If invoked from within a message processor handler and the HA policy 
     * is set to {@link HAPolicy#EventSourcing EventSourcing}, this method returns the time stamped on the message event (stamped just 
     * before the method is dispatched to the application for processing). Since, for {@link HAPolicy#EventSourcing EventSourcing} applications, 
     * the message is also replicated for parallel processing on the backup, the backup will receive the same 
     * time when invoking this method thus ensuring identical processing. If this method is called on an engine 
     * operating in state replication mode or called from outside a message processor, then this method will 
     * return the value returned by {@link UtlTime#nowSinceEpoch()}.
     * 
     * @return The current time in milliseconds. 
     * @see AepEngine#getEngineTime()
     * @see UtlTime#nowSinceEpoch()
     */
    public long getTimeMicros();
}
