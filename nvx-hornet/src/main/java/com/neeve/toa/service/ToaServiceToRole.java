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
package com.neeve.toa.service;

import java.util.Collection;
import java.util.HashMap;

import com.neeve.adm.AdmMessage;
import com.neeve.sma.MessageView;
import com.neeve.trace.Tracer.Level;

/**
 * Modesl a TOA Service 'To' Role. 
 * <p>
 * A 'To' Role groups a collection of messages to be sent
 * by a role name.
 */
public class ToaServiceToRole {

    private final String name;
    private final HashMap<String, AdmMessage> messagesByName = new HashMap<String, AdmMessage>();
    private final HashMap<String, ToaServiceChannel> messageChannelMap = new HashMap<String, ToaServiceChannel>();
    private final HashMap<String, Class<? extends MessageView>> messageClassMap = new HashMap<String, Class<? extends MessageView>>();

    /**
     * Creates a 'to' role.
     * 
     * @param name the role name.
     */
    ToaServiceToRole(String name) {
        this.name = name;
    }

    /**
     * @param admMessage The adm Message
     * @param toaChannel The channel model for the the message. 
     */
    final void addMessage(final AdmMessage admMessage, final ToaServiceChannel toaChannel) {
        messagesByName.put(admMessage.getFullName(), admMessage);
        messageChannelMap.put(admMessage.getFullName(), toaChannel);
        try {
            @SuppressWarnings("unchecked")
            Class<? extends MessageView> messageClass = (Class<? extends MessageView>)Thread.currentThread().getContextClassLoader().loadClass(admMessage.getFullName());
            messageClassMap.put(admMessage.getFullName(), messageClass);
        }
        catch (Exception e) {
            if (ToaService._tracer.debug) ToaService._tracer.log("Couldn't resolve message class: " + admMessage.getFullName(), Level.DEBUG);
        }
    }

    /**
     * Gets the role name.
     * 
     * @return the role name;
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the messages declared by this role. 
     *  
     * @return The messages declared by the role. 
     */
    public Collection<AdmMessage> getMessages() {
        return messagesByName.values();
    }

    /**
     * Returns the channel model for the given message. 
     *  
     * @param fullMessageName The fully qualified messages name.
     * @return The the channel model for the given message or null if the role doesn't have such a message. 
     */
    public ToaServiceChannel getChannel(String fullMessageName) {
        return messageChannelMap.get(fullMessageName);
    }

    /**
     * Returns the resolved message classes declared by this role. 
     *  
     * @return The resolved message classes declared by the role. 
     */
    public Collection<Class<? extends MessageView>> getMessageClasses() {
        return messageClassMap.values();
    }

}
