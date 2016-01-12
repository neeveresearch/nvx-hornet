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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import com.neeve.util.UtlText;
import com.neeve.managed.ManagedObjectLocator;
import com.neeve.root.RootConfig;
import com.neeve.toa.service.ToaService;
import com.neeve.toa.service.ToaServiceChannel;
import com.neeve.toa.spi.TopicResolver;
import com.neeve.toa.spi.TopicResolverProvider;
import com.neeve.toa.tools.ToaCodeGenerator;
import com.neeve.trace.Tracer;

/**
 * This {@link TopicResolverProvider} attempts to load {@link ToaCodeGenerator} generated {@link TopicResolverProvider}s.
 * <p>
 * This provider is automatically registered by {@link TopicOrientedApplication#addTopicResolverProviders(java.util.Set)}.
 * Applications that use their own {@link ManagedObjectLocator} may add this provider manually to discover generated
 * {@link TopicResolverProvider} classes.
 */
public class GeneratedTopicResolverProvider implements TopicResolverProvider {
    final protected static Tracer _tracer = RootConfig.ObjectConfig.createTracer(RootConfig.ObjectConfig.get("nv.toa"));
    private HashSet<ToaService> lookedUpProviders = new HashSet<ToaService>();
    private Map<ToaService, TopicResolverProvider> loadedProviders = new HashMap<ToaService, TopicResolverProvider>();

    public GeneratedTopicResolverProvider() {}

    /* (non-Javadoc)
     * @see com.neeve.toa.spi.TopicResolverProvider#getTopicResolver(com.neeve.toa.service.ToaService, com.neeve.toa.service.ToaServiceChannel, java.lang.Class)
     */
    @Override
    public TopicResolver<?> getTopicResolver(ToaService service, ToaServiceChannel channel, Class<?> messageType) {
        TopicResolverProvider provider = loadedProviders.get(service);
        if (provider == null && !lookedUpProviders.add(service)) {
            final String providerClassName = service.getNameSpace() + "." + UtlText.toFirstLetterUppercase(service.getName()) + "TopicResolverProvider";
            try {
                Class<?> providerClass = Class.forName(providerClassName);
                if (TopicResolver.class.isAssignableFrom(providerClass)) {
                    provider = (TopicResolverProvider)providerClass.newInstance();
                    loadedProviders.put(service, provider);
                }
            }
            catch (ClassNotFoundException e) {
                if (_tracer.debug) _tracer.log("Generated topic resolver provider class name not found: " + e.getMessage(), Tracer.Level.DEBUG);
            }
            catch (InstantiationException e) {
                _tracer.log("Generated topic resolver provider class could not be instantiated: " + e.getMessage(), Tracer.Level.WARNING);
            }
            catch (IllegalAccessException e) {
                _tracer.log("Generated topic resolver provider class could not be accessed: " + e.getMessage(), Tracer.Level.WARNING);
            }
        }

        if (provider != null) {
            return provider.getTopicResolver(service, channel, messageType);
        }
        else {
            return null;
        }

    }

}
