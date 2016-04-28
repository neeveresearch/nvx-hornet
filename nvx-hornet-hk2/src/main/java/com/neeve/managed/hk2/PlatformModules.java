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
package com.neeve.managed.hk2;

import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.utilities.Binder;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import com.neeve.aep.AepEngine;
import com.neeve.toa.EngineClock;
import com.neeve.toa.MessageInjector;
import com.neeve.toa.MessageSender;
import com.neeve.toa.TopicOrientedApplication;

/**
 * Implements the {@link Binder} for a {@link TopicOrientedApplication}'s injectable
 * services. 
 * 
 * The {@link PlatformModules} provides access to the following:
 * <ul>
 * <li> {@link EngineClock} Provides HA consistent time. 
 * <li> {@link MessageSender} Provides access to the {@link MessageSender} interface for a {@link TopicOrientedApplication}.
 * <li> {@link MessageInjector} Provides access to the {@link MessageInjector} interface for a {@link TopicOrientedApplication}.
 * <li> {@link AepEngine} Provides access to the applications underlying {@link AepEngine}. Most applications will not need 
 * access to the AepEngine and will be using the other services provided by {@link TopicOrientedApplication}
 * </u>
 */
public final class PlatformModules extends AbstractBinder {

    final private TopicOrientedApplication application;

    public PlatformModules(TopicOrientedApplication application) {
        this.application = application;
    }

    @Override
    protected final void configure() {
        bind(application).to(TopicOrientedApplication.class);
        bindFactory(new EngineClockFactory()).to(EngineClock.class);
        bindFactory(new MessageSenderFactory()).to(MessageSender.class);
        bindFactory(new MessageInjectorFactory()).to(MessageInjector.class);
        bindFactory(new AepEngineFactory()).to(AepEngine.class);
    }

    private final class EngineClockFactory implements Factory<EngineClock> {

        @Override
        public final void dispose(EngineClock instance) {}

        @Override
        public final EngineClock provide() {
            return application.getEngineClock();
        }

    }

    private class MessageSenderFactory implements Factory<MessageSender> {

        @Override
        public final void dispose(MessageSender instance) {}

        @Override
        public final MessageSender provide() {
            return application.getMessageSender();
        }
    }

    private final class MessageInjectorFactory implements Factory<MessageInjector> {

        @Override
        public void dispose(MessageInjector instance) {}

        @Override
        public MessageInjector provide() {
            return application.getMessageInjector();
        }

    }

    private final class AepEngineFactory implements Factory<AepEngine> {

        @Override
        public void dispose(AepEngine instance) {}

        @Override
        public AepEngine provide() {
            return application.getEngine();
        }
    }
}
