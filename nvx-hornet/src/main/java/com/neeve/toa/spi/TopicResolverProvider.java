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
package com.neeve.toa.spi;

import com.neeve.toa.service.ToaService;
import com.neeve.toa.service.ToaServiceChannel;

/**
 * A {@link TopicResolverProvider} provides {@link TopicResolver} for messages being sent
 * over a service channel.
 * <p>
 * The platform provides code generation utilities to generate static topic resolvers based
 * on the channel key defined in the service xml, but in some cases the application can
 * more efficiently resolve the topic, and in such cases may want to provide their own 
 * {@link TopicResolver}s.
 */
public interface TopicResolverProvider {

    /**
     * Looks up a {@link TopicResolver} for the given service and channel. 
     * 
     * @param service The service that defines the channel. 
     * @param channel The channel. 
     */
    public TopicResolver<?> getTopicResolver(ToaService service, ToaServiceChannel channel, Class<?> messageType);
}
