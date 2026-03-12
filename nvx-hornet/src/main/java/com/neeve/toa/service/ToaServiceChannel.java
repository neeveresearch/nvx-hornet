/**
 * Copyright 2022 N5 Technologies, Inc
 *
 * This product includes software developed at N5 Technologies, Inc
 * (http://www.n5corp.com/) as well as software licenced to N5 Technologies,
 * Inc under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding
 * copyright ownership.
 *
 * N5 Technologies licenses this file to you under the Apache License,
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

import java.util.Properties;

/**
 * Models a Toa Service Channel.
 */
public class ToaServiceChannel {
    private final ToaService service;
    private String busName;
    private final String name;
    private String key;
    private final boolean receiveOnly;
    private Properties initialKRT;
    private String resolvedKey;

    /**
     * Create a new {@link ToaServiceChannel}
     * @param service The owning service.
     * @param busName The bus name.
     * @param name The channel name.
     * @param key The channel key.
     */
    public ToaServiceChannel(final ToaService service, final String busName, final String name, final String key) {
        this(service, busName, name, key, false);
    }

    /**
     * Create a new {@link ToaServiceChannel}
     * @param service The owning service.
     * @param busName The bus name.
     * @param name The channel name.
     * @param key The channel key.
     * @param receiveOnly Whether this channel is receive-only.
     */
    public ToaServiceChannel(final ToaService service, final String busName, final String name, final String key, final boolean receiveOnly) {
        if (service == null) {
            throw new IllegalArgumentException("ToaService cannot be null");
        }
        this.service = service;
        this.busName = busName;
        this.name = name;
        this.key = key;
        this.receiveOnly = receiveOnly;
    }

    /**
     * Sets the busName for this channel.
     * 
     * @param busName The busName.
     */
    public void setBusName(String busName) {
        this.busName = busName;
    }

    /**
     * Gets the bus name. 
     * 
     * @return The bus name. 
     */
    public final String getBusName() {
        return busName;
    }

    /**
     * @return The service that defined this channel.
     */
    public ToaService getService() {
        return service;
    }

    /**
     * Gets the channel name without it's service name prefix. 
     * 
     * @return The channel name without the service name prefix. 
     */
    public final String getSimpleName() {
        return name;
    }

    /**
     * Gets the channel name possibly qualified by prepending the service name in lowercase.
     * <p> 
     * For example if the service is 'OrderProcessingService' and the channel name 
     * is 'Orders', and the service is set to prefix its channel name, then return 
     * 'orderprocessingservice-Orders', otherwise return 'Orders'.
     * 
     * @return The channel name. 
     */
    public final String getName() {
        if (service.isPrefixChannelNames()) {
            return service.getSimpleName().toLowerCase() + "-" + name;
        }
        else {
            return name;
        }
    }

    /**
     * Returns whether this channel is receive-only.
     * <p>
     * A receive-only channel cannot be used for sending messages.
     *
     * @return {@code true} if this channel is receive-only.
     */
    public final boolean isReceiveOnly() {
        return receiveOnly;
    }

    /**
     * Gets the channel key. After a
     *
     * @return The channel key.
     */
    public final String getKey() {
        return key;
    }

    /**
     * Sets the channel key.
     */
    public final void setKey(String key) {
        this.key = key;
    }

    /**
     * Sets the initial key resolution table for this service channel.
     * <p>
     * An initial KRT can be used to substitute in variable key portions
     * of the channel key.
     * 
     * @param initialKRT The Initial KRT.
     */
    public final void setInitialKRT(final Properties initialKRT) {
        this.initialKRT = initialKRT;
    }

    /**
     * Gets the initial key resolution table for this channel. 
     * <p>
     * Gets the initial KRT that was resolved for this channel (if 
     * one has been resolved). 
     * 
     * @return The initialKRT. 
     */
    public final Properties getInitialKRT() {
        return initialKRT;
    }

    /**
     * Sets the key as resolved by the initial key resolution. 
     * <p>
     * This is the key that is used to create the channel with AepEngine which is
     * done after initial key resolution.
     * <p>
     * This key may still have variable components if initial key resolution didn't 
     * resolve all of the variable components.  
     * <p>
     * It may also be the same as the {@link #getKey()} if initial key resolution didn't 
     * result in any variable key component resolution. 
     * 
     * @param resolvedKey The initial key resolution table resolved key. 
     */
    public void setInitiallyResolvedKey(final String resolvedKey) {
        this.resolvedKey = resolvedKey;
    }

    /**
     * Gets the key as resolved by initial key resolution. 
     * <p>
     * This is the key that is used to create the channel with AepEngine which is
     * done after initial key resolution. This values can be derived by taking 
     * {@link #getInitialKRT()} and applying it against {@link #getKey()} 
     * <p>
     * This key may still have variable components if initial key resolution didn't 
     * resolve all of the variable components.  
     * <p>
     * It may also be the same as the {@link #getKey()} if initial key resolution didn't 
     * result in any variable key component resolution.
     *  
     * @return The key with initial channel key resolution substitutions.
     */
    public String getInitiallyResolvedKey() {
        return resolvedKey;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    public String toString() {
        return "ToaServiceChannel [name=" + getName() + ", bus=" + getBusName() + ", key=" + getKey() + ", receiveOnly=" + receiveOnly + ", service=" + (service != null ? service : "null") + "]";
    }
}
