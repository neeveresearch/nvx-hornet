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

import com.neeve.aep.annotations.EventHandler;
import com.neeve.toa.TopicOrientedApplication;
import com.neeve.toa.TopicOrientedApplication.ChannelJoin;
import com.neeve.toa.service.ToaService;
import com.neeve.toa.service.ToaServiceChannel;

/**
 * A {@link ChannelJoinProvider} can be used to specify the whether or not a channel should be joined. 
 * <p>
 * Normally a {@link TopicOrientedApplication} will automatically join a channel if it
 * finds an {@link EventHandler} for a message type that is mapped to the channel in a 
 * service definition. An application may provide a {@link ChannelJoinProvider} to override 
 * this behavior. 
 */
public interface ChannelJoinProvider {

    /**
     * Indicates whether or not a channel should be joined. 
     * <h2>Return Values</h2>
     * A {@link ChannelJoinProvider} may return a {@link ChannelJoin} value
     * to indicate whether or not a channel should be joined:
     * <ul> A value of {@link ChannelJoin#Default Default} or <code>null</code> indicates
     * that the provider has no opinion and will defer to the default behavior of Hornet
     * or other {@link ChannelJoinProvider}s
     * <li> A value of {@link ChannelJoin#Join Join} indicates that the the channel should
     * be joined even if there is no {@link EventHandler} handler registered for any of the
     * channel's types. <i>Overriding the default behavior to join a channel should be rare,
     * since applications should not be attracting messages without handlers or sending 
     * messages on channels other than those that they are mapped to in a service definition.</i>
     * <li>  {@link ChannelJoin#NoJoin NoJoin} indicates that even if there is a message handler
     * defined for a type mapped to the channel, that the channel should not be joined.
     * </ul>
     * 
     * <h2>ChannelJoinProvider Conflicts</h2>
     * It is illegal for one {@link ChannelJoinProvider} returns {@link ChannelJoin#Join Join} 
     * and another to return {@link ChannelJoin#NoJoin NoJoin}. The application will fail to 
     * start in such a case. 
     * 
     * @param service The service that defined the channel.
     * @param channel The channel. 
     * 
     * @return A value indicating whether or not the channel should be joined. 
     */
    public ChannelJoin getChannelJoin(ToaService service, ToaServiceChannel channel);
}
