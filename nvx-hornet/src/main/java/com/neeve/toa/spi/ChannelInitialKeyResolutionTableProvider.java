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

import java.util.Properties;

import com.neeve.sma.MessageChannel;
import com.neeve.toa.TopicOrientedApplication;
import com.neeve.toa.service.ToaService;
import com.neeve.toa.service.ToaServiceChannel;

/**
 * Called by a Topic Oriented Application at the time it is configured which allows all or a portion 
 * of a channel's dynamic key parts to be determined statically at configuration time by returning a 
 * initial Key Resolution Table (KRT).
 * 
 * @see TopicOrientedApplication TopicOrientedApplication messaging configuration
 */
public interface ChannelInitialKeyResolutionTableProvider {

    /**
     * Called by a Topic Oriented Application at the time it is configured which allows all or a portion 
     * of a channel's dynamic key parts to be determined statically at configuration time by returning a 
     * initial Key Resolution Table (KRT).
     * <p>
     * Example:
     * 
     * <BLOCKQUOTE>
     * Given:
     * <ul>
     * <li>A channel, channel1 with a configured key of: ORDERS/${Region}/${Product}
     * <li>An initial KRT returned by this method of: {"Region": "US", "HostName": MyPC}
     * </ul>
     * 
     * With the above KRT, the channel would be initialized with a key of
     * <code>ORDERS/US/${Product}</code>. The dynamic 'Region' portion of the
     * key has become static while the 'Product' portion remains dynamic and 
     * eligible for substitution with a runtime KRT or from values reflected
     * from a message reflector.
     * </BLOCKQUOTE>
     * <p>
     * <b>NOTE:</b><br>
     * The returned key resolution table is not used for individual send calls, if 
     * the channel key still contains dynamic portions then dynamic key resolution
     * can be done on a per send basis using either the message's message reflector
     * or a key resolution table provide as a argument to the send call.  
     * <p>
     * If more than one service share the same channel on the same bus, they will 
     * share the same channel key; at this time it is not possible to perform individual 
     * channel key resolution on a per service basis. In this sense the initial channel key
     * resolution is global to a channel name. The serviceName is provided here as a hint
     * to assist the application in locating a key resolution table for a channel. 
     * <p>
     * Initial Key Resolution adheres to the key resolution properties:
     * <ul>
     * <li> {@link MessageChannel#PROP_TREAT_EMPTY_KEY_FIELD_AS_NULL} if set to true, then 
     * initial channel key resolution will ignore values that are 0 length strings.  
     * <li> Otherwise if {@link MessageChannel#PROP_ALLOW_EMPTY_KEY_FIELD} is set then 
     * initial channel key resolution will fail with a ToaException if the key resolution
     * table contains any value that are 0 length Strings.
     * </ul>
     * 
     * @param service The service name. 
     * @param channel The channel for which to perform key resolution
     * @return A key resolution table to substitute some or all of the configured channel key.
     * 
     * @see TopicOrientedApplication TopicOrientedApplication messaging configuration
     */
    public Properties getInitialChannelKeyResolutionTable(ToaService service, ToaServiceChannel channel);
}
