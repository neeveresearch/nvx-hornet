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

import java.util.Properties;

import com.neeve.lang.XString;
import com.neeve.sma.MessageChannel.RawKeyResolutionTable;
import com.neeve.sma.MessageView;
import com.neeve.toa.TopicOrientedApplication;
import com.neeve.toa.service.ToaServiceChannel;

/**
 * A {@link TopicResolver} resolves a channel key for a message being sent
 * on a given channel.
 * 
 * <h2>Threading</h2>
 * TopicResolvers are <b><i>NOT</i></b> and their methods are not safe for
 * concurrent access by multiple threads.
 */
public interface TopicResolver<T extends MessageView> {

    /**
     * Will be called by {@link TopicOrientedApplication} at startup to initialize
     * the channel key. 
     * <p>
     * Note that at the time this method is called, {@link TopicOrientedApplication} will
     * have set the initial key resolution table via {@link ToaServiceChannel#getInitialKRT()}.
     * <p>
     * Variable components of the channel key provided by an {@link ChannelInitialKeyResolutionTableProvider}
     * will already have been substituted in the key set in the serviceChannel.  
     */
    public void initialize(ToaServiceChannel serviceChannel);

    /**
     * Resolves the topic for the given {@link MessageView}. 
     * <p>
     * This key resolution method is intended for low latency applications. As 
     * such implementors of this method should not produce garbage.
     * <p>
     * This variant of key resolution can be faster since than 
     * {@link #resolveTopic(MessageView, Properties)} because the values
     * in a {@link RawKeyResolutionTable} can be preserialized and thus 
     * don't require character encoding.
     * 
     * @threading This method is not safe for concurrent access by multiple threads.
     * 
     * @param message The message for which to resolve the topic. 
     * @param krt The {@link RawKeyResolutionTable}. 
     * 
     * @return The resolved topic.
     * 
     * @throws Exception If the key can't be resolved completely this method must thrown an Exception.
     */
    public XString resolveTopic(T message, RawKeyResolutionTable krt) throws Exception;

    /**
     * Resolves the topic for the given {@link MessageView}. 
     * 
     * @threading This method is not safe for concurrent access by multiple threads.
     * 
     * @param message The message for which to resolve the topic. 
     * 
     * @return The resolved topic.
     * 
     * @throws Exception If the key can't be resolved completely this method must thrown an Exception.
     */
    public XString resolveTopic(T message, Properties krt) throws Exception;
}
