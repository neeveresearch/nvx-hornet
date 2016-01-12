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

import java.util.Properties;

import com.neeve.aep.AepEngine;
import com.neeve.aep.annotations.EventHandler;
import com.neeve.lang.XString;
import com.neeve.rog.IRogMessage;
import com.neeve.sma.MessageChannel;
import com.neeve.sma.MessageChannel.RawKeyResolutionTable;

/**
 * Interface for sending messages in a Topic Oriented Applications. 
 * <p>
 * In topic oriented applications, service definitions bind message types
 * to {@link MessageChannel} that encapsulate the topics on which 
 * messages are transported by default. 
 * <p>
 * The {@link MessageSender}
 * interface provides methods that lookup up the channel associated with a given
 * message type as defined by the application's service definitions. Additionally
 * it provides sendMessage variants allowing the caller to override the topic
 * and provide a customized key resolution table for resolving dynamic portions
 * of the channel key.
 * <p>
 * <b>NOTE:</b>
 * A {@link MessageSender} may be backed by one and only one {@link TopicOrientedApplication}
 * and is tied to that application's lifecycle and transaction context. Applications
 * that send outbound messages via a {@link MessageSender} as the result of processing
 * an inbound message must have received the message from the same {@link AepEngine} that
 * backs the {@link MessageSender}'s {@link TopicOrientedApplication}, failure to do so
 * would break the atomicity guarantees of inbound/outbound message and can in 
 * certain situations lead to deadlocks. 
 * <p>
 * <b>Threading</b>
 * Implementations of this class are not thread safe. Send calls may only be
 * called from an {@link EventHandler} that originates from the {@link AepEngine} that backs
 * the {@link TopicOrientedApplication} from whence this MessageSender came, or in a non concurrent 
 * fashion by an unsolicited 'sender' thread if this is a purely producer application. 
 * 
 * @see AepEngine#sendMessage(MessageChannel, IRogMessage)
 * @see AepEngine
 * @see TopicOrientedApplication
 * @see MessageChannel
 */
public interface MessageSender {

    /**
     * Sends a message using the message's default channel. This method has the same semantics as {@link AepEngine#sendMessage(MessageChannel, IRogMessage)},
     * with the message channel resolved based on this application's service definition for the message type and the provided message.
     * 
     * @threading This method is not safe for concurrent access by multiple threads with itself or any of the other engine / TOA methods.
     * 
     * @see AepEngine#sendMessage(MessageChannel, IRogMessage)
     * @param message The message to send. 
     */
    public void sendMessage(final IRogMessage message);

    /**
     * Sends a message using the given topic as the channel key. This method has the same semantics as {@link AepEngine#sendMessage(MessageChannel, IRogMessage, String, Properties)},
     * with the message channel resolved based on this application's service definition for the message type, the provided message, the provided 
     * topic as the channel key, and no key resolution table.
     * 
     * @threading This method is not safe for concurrent access by multiple threads with itself or any of the other engine / TOA methods.
     * 
     * @see AepEngine#sendMessage(MessageChannel, IRogMessage)
     * @param message The message to send. 
     */
    public void sendMessage(final IRogMessage message, final String topic);

    /**
     * Sends a message using the provided key resolution table. This method has the same semantics as {@link AepEngine#sendMessage(MessageChannel, IRogMessage, Properties)},
     * with the message channel resolved based on this application's service definition for the message type, the provided message,
     * key, and the provided message key.
     * 
     * @threading This method is not safe for concurrent access by multiple threads with itself or any of the other engine / TOA methods.
     * 
     * @see AepEngine#sendMessage(MessageChannel, IRogMessage)
     * @param message The message to send. 
     */
    public void sendMessage(final IRogMessage message, final Properties keyResolutionTable);

    /**
     * Sends a message using the given topic as the channel key (zero garbage variant). This method has the same semantics as {@link AepEngine#sendMessage(MessageChannel, IRogMessage, XString, MessageChannel.RawKeyResolutionTable)},
     * with the message channel resolved based on this application's service definition for the message type, the provided message, the provided 
     * topic as the channel key, and no key resolution table.
     * 
     * @threading This method is not safe for concurrent access by multiple threads with itself or any of the other engine / TOA methods.
     * 
     * @see AepEngine#sendMessage(MessageChannel, IRogMessage)
     * @param message The message to send. 
     */
    public void sendMessage(final IRogMessage message, final XString topic);

    /**
     * Sends a message using the provide zero garbage key resolution table. This method has the same semantics as {@link AepEngine#sendMessage(MessageChannel, IRogMessage, MessageChannel.RawKeyResolutionTable)},
     * with the message channel resolved based on this application's service definition for the message type, the provided message, 
     * and no key resolution table.
     * 
     * @threading This method is not safe for concurrent access by multiple threads with itself or any of the other engine / TOA methods.
     * 
     * @see AepEngine#sendMessage(MessageChannel, IRogMessage)
     * @param message The message to send. 
     */
    public void sendMessage(final IRogMessage message, final RawKeyResolutionTable rawKeyResolutionTable);
}
