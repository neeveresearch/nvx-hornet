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
package com.neeve.toa.spi;

import com.neeve.sma.MessageChannel.Qos;
import com.neeve.toa.TopicOrientedApplication;
import com.neeve.toa.service.ToaService;
import com.neeve.toa.service.ToaServiceChannel;

/**
 * A {@link ChannelQosProvider} resolves the channel filter for a given {@link ToaServiceChannel} channel.
 * 
 * <h2>Channel QoS Resolution in Hornet</h2>
 * A {@link TopicOrientedApplication} in hornet will automatically create buses and channels that 
 * have have not already been configured for the application. 
 * <p>
 * In the absense of a {@link ChannelQosProvider}:
 * <ul>
 * <li>If the bus and channel is defined already in DDL the configured QoS will be used as the default.
 * <li>If the channel is not defined then QoS default is set to Guaranteed
 * </ul>
 * <p>
 * ChannelQosProviders can be used to override the QoS determined above:
 * <ul>
 * <li>If any ChannelQosProviders returns a {@link Qos} via {@link #getChannelQos(ToaService, ToaServiceChannel)} that value
 *     is used to update the {@link Qos} for the channel. 
 * <li>If one provider returns `BestEffort` and another `Guaranteed`, then Guaranteed is chosen.
 * </ul>
 * 
 * The above logic only applies to channels discovered in Hornet services. Any preconfigured channels 
 * for a bus that are unrelated to a service for the application are not modified. 
 * 
 * @see TopicOrientedApplication TopicOrientedApplication messaging configuration
 */
public interface ChannelQosProvider {

    /**
     * Returns the channel {@link Qos} for the given {@link ToaServiceChannel}.
     * <p>
     * If multiple {@link ChannelQosProvider}s return differing {@link Qos} values 
     * for the same service and channel the highest quality of service will be 
     * selected (e.g. Guaranteeed).
     * 
     * @param service The service that defined the channel.
     * @param channel The channel. 
     * 
     * @return The channel {@link Qos} or <code>null</null> if the provider either doesn't provide
     * {@link Qos} for this channel or if the channel should not be filtered. 
     * 
     * @see TopicOrientedApplication TopicOrientedApplication messaging configuration
     */
    public Qos getChannelQos(ToaService service, ToaServiceChannel channel);
}
