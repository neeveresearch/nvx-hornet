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
import com.neeve.rog.IRogMessage;

/**
 * Defines the interface for injecting messages into a Topic Oriented Applications.
 * <p>
 * Injection of a message into a {@link TopicOrientedApplication} directly enqueues
 * the message for processing by the underlying {@link AepEngine}'s event multiplexer
 * similar to how a message received from a message bus would be injected into the 
 * engine. 
 * 
 * <p>
 * <b>Threading</b>
 * @threading This method is safe for concurrent access by multiple threads. 
 * 
 * @see AepEngine#injectMessage(IRogMessage, boolean, int)
 * @see AepEngine
 * @see TopicOrientedApplication
 */
public interface MessageInjector {

    /**
     * Enqueue a message into an application's {@link AepEngine}'s event multiplexer. 
     * <p>
     * This method is the same as {@link #injectMessage(IRogMessage, boolean, int) injectMessage(message, false, defaultInjectionDelay)}.
     * (where default injection delay is set by {@link TopicOrientedApplication#PROP_DEFAULT_INJECTION_DELAY}).
     *  
     * @param message The message to enqueue. 
     * 
     * @see AepEngine#injectMessage(IRogMessage, boolean)
     */
    public void injectMessage(IRogMessage message);

    /**
     * Enqueue a message into an application's {@link AepEngine}'s event multiplexer. 
     * <p>
     * This method is the same as {@link #injectMessage(IRogMessage, boolean, int) injectMessage(message, nonBlocking, defaultInjectionDelay)} 
     * (where default injection delay is set by {@link TopicOrientedApplication#PROP_DEFAULT_INJECTION_DELAY}).
     * 
     * @param message The message to enqueue. 
     * 
     * @param nonBlocking Indicates whether the multiplexing should be a 
     * non-blocking action or not. If blocking, then the calling thread 
     * will block if the engine's input multiplexer queue is full and wait until 
     * space is available. If non-blocking, then the method will not
     * wait but rather enque the message in a feeder queue fronting the engine's
     * input multiplexer queue. 
     * 
     * @see AepEngine#injectMessage(IRogMessage, boolean)
     * @see TopicOrientedApplication#PROP_DEFAULT_INJECTION_DELAY
     */
    public void injectMessage(IRogMessage message, boolean nonBlocking);

    /**
     * Enqueue a message into an application's {@link AepEngine}'s event multiplexer. 
     * <p> 
     * This method is the same as the corresponding {@link AepEngine#injectMessage(IRogMessage, boolean, int)}
     * method <b>except</b> that this method disallows injection of message from the {@link AepEngine}'s 
     * dispatch thread (i.e. from a message handler). 
     * 
     * @param message The IRogMessage to enqueue. 
     *  
     * @param nonBlocking Indicates whether the multiplexing should be a 
     * non-blocking action or not. If blocking, then the calling thread 
     * will block if the engine's input multiplexer queue is full and wait until 
     * space is available. If non-blocking, then the method will not
     * wait but rather enque the message in a feeder queue fronting the engine's
     * input multiplexer queue. 
     * 
     * @param delay The delay in milliseconds at which the message should be injected.
     *  
     * @threading This method is safe for concurrent access by multiple threads. 
     * 
     * @throws IllegalStateException If the underlying AepEngine has not been started. 
     * @throws UnsupportedOperationException if this is called from the {@link AepEngine}'s dispatch therad (i.e. a message/event handler). 
     */
    void injectMessage(IRogMessage message, boolean nonBlocking, int delay);

}
