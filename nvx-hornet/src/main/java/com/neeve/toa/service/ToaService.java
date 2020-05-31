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
package com.neeve.toa.service;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBContext;

import com.neeve.adm.AdmFactory;
import com.neeve.adm.AdmMessage;
import com.neeve.adm.AdmModel;
import com.neeve.adm.AdmType;
import com.neeve.adm.AdmXMLParser;
import com.neeve.config.Config;
import com.neeve.root.RootConfig;
import com.neeve.toa.ToaException;
import com.neeve.toa.service.jaxb.Service;
import com.neeve.trace.Tracer;
import com.neeve.trace.Tracer.Level;
import com.neeve.util.UtlFile;

/**
 * Models a Topic Oriented Application service. 
 */
public class ToaService {
    /**
     * Runtime property name to override whether or not channel names are prefixed with their service name. 
     * <p>
     * When an application uses multiple services there is a risk that channels with the same in two different
     * services would conflict with one another. To reduce the chances of this happening the service xml defaults
     * to prefixing the channel name with the service name. For example consider teh following two service definition
     * snippets:
     * 
     * <pre>
     * {@code
     *   <Service xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
     *            xmlns="http://www.neeveresearch.com/schema/x-tsml" 
     *            namespace="com.neeve.toa.example.orders" 
     *            name="OrderProcessorService">
     *     <Models>
     *       <Model file="com/neeve/toa/example/orders/orderMessages.xml"/>
     *     </Models>
     *     <Channels>
     *       <Channel name="Rejects" key="orderprocessing/rejects"/>
     *     </Channels>
     *     <Messages>
     *     </Messages>
     *   </Service>
     * }    
     * </pre>
     * 
     * and
     * 
     * <pre>
     * {@code
     *   <Service xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
     *            xmlns="http://www.neeveresearch.com/schema/x-tsml" 
     *            namespace="com.neeve.toa.example.shipping" 
     *            name="ShippingService">
     *     <Models>
     *       <Model file="com/neeve/toa/example/orders/shippingMessages.xml"/>
     *     </Models>
     *     <Channels>
     *       <Channel name="Rejects" key="shipping/rejects"/>
     *     </Channels>
     *   </Service>
     * }    
     * </pre>
     * 
     * In the above case both services define a channel named 'Rejects' which map to different underling
     * topics. To avoid a conflict between the 2 channels, Hornet, by default will prefix the channel names 
     * with the unqualified service name in lower case so that one ends up with:
     * 
     * <ul>
     * <li>orderprocessorservice-Rejects</li>
     * <li>shippingservice-Rejects</li>
     * </ul>
     * 
     * <p>
     * <b>Property name:</b> {@value #PROP_PREFIX_CHANNEL_NAMES}
     * <br>
     * <b>Default value:</b> Value read from service definition.
     * <br>
     */
    public static final String PROP_PREFIX_CHANNEL_NAMES = "nv.toa.prefixchannelnames";

    final protected static Tracer _tracer = RootConfig.ObjectConfig.createTracer(RootConfig.ObjectConfig.get("nv.toa"));
    private final String name;
    private final String namespace;
    private final Date lastModified;
    private final boolean prefixChannelNames;
    private final Map<String, AdmModel> messageModels = new HashMap<String, AdmModel>();
    private final Map<String, ToaServiceChannel> channelsBySimpleName = new HashMap<String, ToaServiceChannel>();
    private final Map<String, ToaServiceToRole> roles = new HashMap<String, ToaServiceToRole>();

    private final HashMap<Short, AdmFactory> factoriesById = new HashMap<Short, AdmFactory>();
    private ToaServiceChannel defaultChannel;

    public ToaService(final Date lastModified, final String namespace, final String name, final boolean prefixChannelNames) {
        this.namespace = namespace;
        this.name = name;
        this.lastModified = lastModified == null ? new Date() : lastModified;
        this.prefixChannelNames = prefixChannelNames;
    }

    /**
     * Returns the last modification time for this service. 
     * <p>
     * 
     * @return The last modification time of the service (or the time this class was created if unkown);
     */
    public Date getLastModified() {
        return lastModified;
    }

    /**
     * Adds an ADM model to this service. 
     * 
     * @param model The model to add. 
     */
    public void addMessageModel(AdmModel model) {
        messageModels.put(model.getFullName(), model);
    }

    /**
     * Returns the service's simple name.
     * 
     * @return The service's unqualified name.
     */
    public String getSimpleName() {
        return name;
    }

    /**
     * Returns the service's namespace.
     * 
     * @return The service's namespace.
     */
    public String getNameSpace() {
        return namespace;
    }

    /**
     * Returns the fully qualified name of this service. 
     * 
     * @return The service's fully qualified name. 
     */
    public String getName() {
        return namespace + "." + name;
    }

    /**
     * @return The default 'To' channel for this service. 
     */
    public ToaServiceChannel getDefaultChannel() {
        return defaultChannel;
    }

    /**
     * @return True if channels defined in this service should prefix their names with the lowercase, unqualified name of the service. 
     */
    public boolean isPrefixChannelNames() {
        return prefixChannelNames;
    }

    /**
     * Returns the collection of ADM Message models declared by the service.
     * <p>
     * The caller should not modify the returned collection. 
     * 
     * @return The service's ADM messaging models
     */
    public Collection<AdmModel> getMessageModels() {
        return messageModels.values();
    }

    /**
     * Returns the collection of factories used by the service.
     * <p>
     * The caller should not modify the returned collection. 
     * 
     * @return The ADM factories used by the service.
     */
    public Collection<AdmFactory> getAdmFactories() {
        return factoriesById.values();
    }

    /**
     * Returns the collection of channels defined for the service
     * <p>
     * The caller should not modify the returned collection. 
     * 
     * @return The collection of channels defined for the service
     */
    public Collection<ToaServiceChannel> getChannels() {
        return channelsBySimpleName.values();
    }

    /**
     * Returns the collection of 'To' roles defined for the service
     * <p>
     * The caller should not modify the returned collection. 
     * 
     * @return The collection of 'To' roles defined for the service
     */
    public Collection<ToaServiceToRole> getToRoles() {
        return roles.values();
    }

    /**
     * Looks up a 'To' role by name. 
     * 
     * @param roleName the role name.
     * @return The role for the given name or <code>null</code> if none exists.
     */
    public ToaServiceToRole getToRole(String roleName) {
        return roles.get(roleName);
    }

    /**
     * Returns true if the provided object is a {@link ToaService} with 
     * the same fully qualified name. 
     * 
     * @param other the object with which to compare. 
     */
    @Override
    public boolean equals(Object other) {
        if (other instanceof ToaService) {
            return equals((ToaService)other);
        }

        return false;
    }

    /**
     * The hashCode for a {@link ToaService} is the same as
     * the hashcode for its fully qualifed name. 
     * 
     */
    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    /**
     * Returns true if the provided service has the same 
     * fully qualified name. 
     * 
     * @param other the object with which to compare. 
     */
    public final boolean equals(ToaService other) {
        if (other == null) {
            return false;
        }

        return getName().equals(other.getName());
    }

    /**
     * Unmarshals a toa service model from the service xml at the given url. 
     * 
     * @param url The url pointing to an 
     * 
     * @return An unmarshalled {@link ToaService} 
     * @throws Exception If there is an error unmarshalling the xml. 
     */
    public static final ToaService unmarshal(URL url) throws Exception {
        if (url == null) {
            throw new IllegalArgumentException("Service url to unmarshal must not be null");
        }
        Date lastModified = null;
        URLConnection urlConnection = url.openConnection();
        long lastModifiedTime = urlConnection.getLastModified();
        if (lastModifiedTime > 0) {
            lastModified = new Date(lastModifiedTime);
        }
        Service service = (Service)JAXBContext.newInstance(Service.class).createUnmarshaller().unmarshal(new BufferedInputStream(urlConnection.getInputStream()));
        ToaService rc = new ToaService(lastModified, service.getNamespace(), service.getName(), Config.getValue(PROP_PREFIX_CHANNEL_NAMES, service.getChannels().isPrefixChannelNames()));

        // resolve message models 
        for (Service.Models.Model messageModel : service.getModels().getModel()) {
            try {
                final AdmModel admModel = AdmXMLParser.parse(resolveMessageModelFile(messageModel.getFile()));
                if (_tracer.debug) _tracer.log("<nv.toa> [" + rc.getName() + "] ......'" + messageModel.getFile() + "' (" + admModel.getName() + ").", Tracer.Level.DEBUG);
                rc.messageModels.put(admModel.getFullName(), admModel);
            }
            catch (Exception e) {
                throw new ToaServiceModelException(e);
            }
        }

        // parse channels:
        for (Service.Channels.Channel channel : service.getChannels().getChannel()) {
            ToaServiceChannel toaChannel = new ToaServiceChannel(rc, channel.getBus(), channel.getName(), channel.getKey());

            if (channel.isDefault() != null && channel.isDefault()) {
                if (_tracer.debug) _tracer.log("<nv.toa> [" + rc.getName() + "] ......'" + toaChannel.getName() + "'.", Tracer.Level.DEBUG);
                if (rc.defaultChannel != null) {
                    throw new ToaException("channel '" + rc.defaultChannel.getName() + "' and '" + toaChannel.getName() + "' are both configured as default channels in the '" + service.getName() + "' service");
                }
                rc.defaultChannel = toaChannel;
            }

            ToaServiceChannel prev = rc.channelsBySimpleName.put(toaChannel.getSimpleName(), toaChannel);
            if (prev != null) {
                if ((prev.getKey() == null && toaChannel.getKey() != null) || !prev.getKey().equals(toaChannel.getKey())) {
                    throw new ToaServiceModelException("Conflicting channel definitions for '" + toaChannel.getName() + "' two channels with same name but different keys: '" +
                            prev.getKey() + "' vs '" + toaChannel.getKey() + "'.");
                }
            }
        }

        //parse roles
        HashMap<Short, AdmFactory> factoriesById = new HashMap<Short, AdmFactory>();
        for (Service.Roles.To to : service.getRoles().getTo()) {
            ToaServiceToRole role = new ToaServiceToRole(to.getRole());
            rc.roles.put(role.getName(), role);
            for (Service.Roles.To.Message message : to.getMessage()) {
                // trace
                if (_tracer.debug) _tracer.log("<nv.toa> [" + rc.getName() + "] ......message '" + message.getName() + "...", Tracer.Level.DEBUG);

                // resolve the message model
                final AdmMessage admMessage = rc.resolveMessage(message.getName(), message.getModel());
                final AdmFactory admFactory = admMessage.getFactory();
                if (_tracer.debug) _tracer.log("<nv.toa> [" + rc.getName() + "] .........<message='" + admMessage.getFullName() + ">.", Tracer.Level.DEBUG);
                if (_tracer.debug) _tracer.log("<nv.toa> [" + rc.getName() + "] .........<factory='" + admFactory.getFullName() + "[vfid=" + admFactory.calcFactoryId() + "]>.", Tracer.Level.DEBUG);

                // resolve the channel model
                final ToaServiceChannel messageChannel = rc.resolveMessageChannel(admMessage, message.getChannel());
                if (_tracer.debug) _tracer.log("<nv.toa> [" + rc.getName() + "] .........<channel='" + messageChannel.getName() + ">.", Tracer.Level.DEBUG);

                // record the factory and check for collisions
                AdmFactory prevFactory = factoriesById.put(admFactory.calcFactoryId(), admFactory);
                if (prevFactory != null && !prevFactory.getFullName().equals(admFactory.getFullName())) {
                    throw new ToaServiceModelException("Factory id collision detected: '" + prevFactory + "' and '" + admFactory.getFullName() + "' both have factory id of " + prevFactory.calcFactoryId());
                }

                // add the message to the role
                role.addMessage(admMessage, messageChannel);
            }
        }

        return rc;
    }

    /**
     * Looks for a model file on the class path and extracts it to the file system for use
     * by the ADM XML Parser. 
     * 
     * TODO Should enhance teh ADM XML Parser to just accept that url. 
     * 
     * @param modelFilename The model file name.
     * @return
     * @throws IOException
     */
    final private static File resolveMessageModelFile(final String modelFilename) throws IOException {
        // if the file exists as a resource, then return it.
        final URL url = ToaService.class.getResource("/" + modelFilename);
        if (url != null) {
            InputStream is = url.openStream();
            try {
                return UtlFile.copyToTempFile(is);
            }
            finally {
                try {
                    is.close();
                }
                catch (IOException e) {}
            }
        }

        // otherwise...throw exception
        throw new FileNotFoundException("model file '" + modelFilename + "' could not be located");
    }

    /**
     * Resolves a message from this service's models. 
     * <p>
     * <ul>
     * <li>If the admModelName is specified as a qualified model name, then it is used to look for the message
     * <li>If the admModelName is specified as a unqualified model name, then all models with matching name will be searched
     * and if only one contains a message matching the name it will be returned.  
     * <li>Otherwise all modelswill be searched and if only one contains a message matching the name it will be returned.  
     * </ul>
     * <p>
     * If the messageName is qualified then its namespace will always be used to check that it matches the model
     * name. 
     * 
     * @param messageName The message model. 
     * @param admModelName The adm model name to look for (possibly qualified)
     * @return The resolved channel
     * @throws ToaServiceModelException if no channel is found. 
     */
    final private AdmMessage resolveMessage(String messageName, String admModelName) {
        String simpleMessageName = messageName;
        String messageNamespace = null;
        if (messageName.indexOf(".") > 0) {
            simpleMessageName = messageName.substring(messageName.lastIndexOf(".") + 1);
            messageNamespace = messageName.substring(0, messageName.lastIndexOf("."));
        }

        if (admModelName != null) {
            AdmModel resolvedModel = messageModels.get(admModelName);

            // check unqualified:
            if (resolvedModel == null) {
                for (AdmModel model : messageModels.values()) {
                    if (admModelName.equals(model.getName())) {
                        if (resolvedModel == null) {
                            if (model.getMessage(simpleMessageName) != null) {
                                if (messageNamespace != null && !messageNamespace.equals(model.getNamespace())) {
                                    if (_tracer.debug) _tracer.log("Found model with matching name for message '" + messageName + "', but namespace doesn't match: " + model.getFullName(), Level.DEBUG);
                                    continue;
                                }
                                resolvedModel = model;
                            }
                        }
                        else {
                            if (model.getMessage(simpleMessageName) != null) {
                                throw new ToaServiceModelException("Ambiguous model for message '" + messageName + "'defined in service '" + getName() + "'. Both '" + resolvedModel.getFullName() + "' and '" + model.getFullName() + "' contain the message type. A qualified model or message name must be specified.");
                            }
                        }
                    }
                }

                if (resolvedModel != null) {
                    return resolvedModel.getMessage(simpleMessageName);
                }
            }
            else if (resolvedModel.getMessage(simpleMessageName) != null) {
                return resolvedModel.getMessage(simpleMessageName);
            }
            else {
                throw new ToaServiceModelException("'" + admModelName + "' does not contain message '" + messageName + "' defined in service '" + getName() + "'.");
            }

            throw new ToaServiceModelException("Could not resolve message model '" + admModelName + "' with message '" + messageName + "' defined in service '" + getName() + "'.");
        }

        // model name not explicitly defined ... search all models for a message with the name.
        AdmModel resolvedModel = null;
        for (AdmModel model : messageModels.values()) {
            if (resolvedModel == null) {
                // lookup the type (including imports
                AdmType type = model.getType(messageName);
                if (type instanceof AdmMessage) {
                    // if namespace qualified, return immediately, no need to check for collisions.
                    if (messageNamespace != null) {
                        return (AdmMessage)type;
                    }

                    resolvedModel = model;
                }
                else {
                    if (_tracer.debug) _tracer.log("Found type in model '" + model.getFullName() + "' that was not a message: '" + type + "', ignoring...", Level.DEBUG);

                }
            }
            else {
                if (model.getMessage(simpleMessageName) != null) {
                    throw new ToaServiceModelException("Ambiguous model for message '" + messageName + "'defined in service '" + getName() + "'. Both '" + resolvedModel.getFullName() + "' and '" + model.getFullName() + "' contain the message type. A qualified qualified model or message name must be specified.");
                }
            }
        }

        if (resolvedModel != null) {
            return resolvedModel.getMessage(simpleMessageName);
        }
        throw new ToaServiceModelException("Could not resolve message '" + messageName + "' from any of the models defined in service '" + getName() + "'.");

    }

    /**
     * Resolves the channel to use for the given message. 
     * <p>
     * <ul>
     * <li>If the channelName is specified, it is used to lookup the channel.
     * <li>Otherwise, if there is a channel that matches the message's simple name, it is used.
     * <li>Otherwise, if there the default channel is used. 
     * <li>If no default channel this will result in a {@link ToaServiceModelException}.
     * </ul>
     * 
     * @param message The message model. 
     * @param channelName The channel name. 
     * @return The resolved channel
     * @throws ToaServiceModelException if no channel is found. 
     */
    final private ToaServiceChannel resolveMessageChannel(AdmMessage message, String channelName) {
        ToaServiceChannel messageChannel = null;
        if (channelName != null) {
            // channel explictly set for the message. verify channel set for message is indeed present in the services's channel set
            messageChannel = channelsBySimpleName.get(channelName);
            if (messageChannel == null) {
                throw new ToaServiceModelException("could not find channel '" + channelName + "' defined as the channel for message '" + message.getFullName() + "' in service '" + getName() + "'");
            }
        }
        else {
            // channel not set for message. check if there is a channel with the same name as the message
            messageChannel = channelsBySimpleName.get(message.getName());

            // found channel with same name as message?
            if (messageChannel == null) {
                // nope. use default channel
                if ((messageChannel = defaultChannel) == null) {
                    // no default channel resolved for service.
                    throw new ToaServiceModelException("Could not resolve the channel for message '" + message.getName() + "' in service '" + getName() + "'. No channel specified for the message, no default channel for the service, and no channel matching the message name.");
                }
            }
        }
        return messageChannel;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    public String toString() {
        return "ToaService [name=" + getName() + " prefixChannelNames=" + isPrefixChannelNames() + "]";
    }
}
