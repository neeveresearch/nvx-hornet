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

import com.neeve.toa.TopicOrientedApplication;
import com.neeve.toa.service.ToaService;
import com.neeve.toa.service.ToaServiceChannel;

/**
 * A {@link ChannelFilterProvider} resolves the channel filter for a 
 * given {@link ToaServiceChannel} channel. 
 * 
 * @see TopicOrientedApplication TopicOrientedApplication messaging configuration
 */
public interface ChannelFilterProvider {

    /**
     * Returns the channel filter for the given {@link ToaServiceChannel}.
     * <p>
     * <h2>ChannelJoinProvider Conflicts</h2>
     * It is illegal for one {@link ChannelFilterProvider} to return a different channel
     * filter than another provider. The application will fail to 
     * start in such a case. 
     * 
     * @param service The service that defined the channel.
     * @param channel The channel. 
     * @return The channel filter or <code>null</null> if the provider either doesn't provide the
     * filter for this channel or if the channel should not be filtered. 
     * 
     * @see TopicOrientedApplication TopicOrientedApplication messaging configuration
     */
    public String getChannelFilter(ToaService service, ToaServiceChannel channel);
}
