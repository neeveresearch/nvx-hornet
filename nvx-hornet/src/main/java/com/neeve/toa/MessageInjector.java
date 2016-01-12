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
package com.neeve.toa;

import com.neeve.aep.AepEngine;
import com.neeve.rog.IRogMessage;
import com.neeve.sma.MessageView;

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
 * @see AepEngine#multiplexMessage(IRogMessage)
 * @see AepEngine#multiplexMessage(IRogMessage, boolean)
 * @see AepEngine
 * @see TopicOrientedApplication
 */
public interface MessageInjector {

    /**
     * Enqueue a message into an application's {@link AepEngine}'s event multiplexer. 
     * <p>
     * This method is the same as {@link #injectMessage(IRogMessage, boolean)} with
     * a value of false for nonBlocking. 
     * 
     * @param message The message to enqueue. 
     */
    public void injectMessage(IRogMessage message);

    /**
     * Enqueue a message into an application's {@link AepEngine}'s event multiplexer. 
     * <p>
     * This method is the same as {@link #injectMessage(IRogMessage, boolean)} with
     * a value of false for nonBlocking and a delay of 0.
     * 
     * @param message The message to enqueue. 
     */
    public void injectMessage(IRogMessage message, boolean nonBlocking);

    /**
     * Enqueue a message into an application's {@link AepEngine}'s event multiplexer. 
     * <p> 
     * This method enqueues a message into an engine's event multiplexer 
     * event loop and returns to the caller. The message is then dispatched 
     * subsequently using the normal message dispatch mechanisms.
     * 
     * <h2>Injection from an Event/Message Handler</h2>
     * Injection of messages from an event handler is not currently a safe operation
     * from an HA standpoint and as such it is currently disallowed. 
     * 
     * <h2>Delayed or Priority Injection</h2>
     * The value of the delay parameter is interpreted as follows:
     * <ul>
     * <li>A value of 0 causes the message to be added to the end of the underlying engine's 
     * event multiplexer queue for immediate dispatch after already enqueued events. 
     * <li>A positive value is interpreted in milliseconds and will cause the execution of the
     * event to be delay no sooner than the delay period. 
     * <li>A negative value is treated as a high priority dispatch and are added to the 
     * beginning of the engine's event multiplexer queue, a lower delay represents a higher
     * priority dispatch.   
     * </ul>
     * 
     * <h2>Behavior when not in HA Active Role</h2>
     * The message will only be injected on Started engines operating in the Primary role.
     * Calls to inject are ignored on backup instances as the expectation is that the
     * injected message will be replicated from the primary. Calls made while an engine
     * is replaying from a transaction log are similarly ignored as they would interfere
     * with the stream being replayed. An application that injects messages from an
     * external source may call {@link AepEngine#waitForMessagingToStart()} to avoid
     * an injected message being discarded while an engine is transitioning to a started,
     * primary role. 
     * <b>It is important to note that message injection is thus effectively a BestEffor 
     * operation because injections of message that are in the event multiplexer queue
     * at the time of failure will be lost</b>
     * 
     * <h2>Message Pooling Considerations</h2>
     * This method transfers ownership of the message to the platform, the method
     * caller must not modify the message subsequent to this call. The platform
     * will call {@link IRogMessage#dispose()} on the message once it has been dispatched, 
     * so an application must call {@link MessageView#acquire()} if it will hold on to
     * a (read-only) reference to the message. 
     * 
     * <h2>Blocking vs. Non Blocking Considerations</h2>
     * Care must be taken when considering whether to use using blocking
     * or non blocking injection.  If the injecting thread is injecting to a engine 
     * multiplexer that may itself block on a resource held by the thread trying to inject,
     * is can cause a deadlock. Conversely, using non blocking dispatch can result in excessive 
     * memory growth, increased latency and fairness issues, so if the injecting thread 
     * is drawing events from an external source, blocking dispatch is generally the right choice. 
     * 
     * @param message The IRogMessage to enqueue. 
     *  
     * @param nonBlocking Indicates whether the multiplexing should be a 
     * non-blocking action or not. If blocking, then the calling thread 
     * will block if the main multiplexer queue is full and wait until 
     * space is available. If non-blocking, then the method will not
     * wait but rather enque the message in a multiplexer feeder queue 
     * and return. 
     * 
     * @param delay The delay in milliseconds at which the message should be injected.
     *  
     * @threading This method is safe for concurrent access by multiple threads. 
     */
    void injectMessage(IRogMessage message, boolean nonBlocking, int delay);

}
