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
package com.neeve.toa;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.neeve.adm.AdmFactory;
import com.neeve.adm.AdmMessage;
import com.neeve.adm.runtime.AdmCompatibility;
import com.neeve.adm.runtime.annotations.AdmFactoryInfo;
import com.neeve.adm.runtime.annotations.AdmGenerated;
import com.neeve.aep.AepEngine;
import com.neeve.aep.AepEngine.MessagingStartFailPolicy;
import com.neeve.aep.AepEngine.State;
import com.neeve.aep.AepEngineDescriptor;
import com.neeve.aep.AepEventDispatcher;
import com.neeve.aep.IAepApplicationStateFactory;
import com.neeve.aep.IAepPostdispatchMessageHandler;
import com.neeve.aep.IAepPredispatchMessageHandler;
import com.neeve.aep.IAepWatcher;
import com.neeve.aep.annotations.EventHandler;
import com.neeve.aep.event.AepApplicationExceptionEvent;
import com.neeve.aep.event.AepBusBindingOpenFailedEvent;
import com.neeve.aep.event.AepChannelUpEvent;
import com.neeve.aep.event.AepEngineActiveEvent;
import com.neeve.aep.event.AepEngineStartedEvent;
import com.neeve.aep.event.AepMessagingPrestartEvent;
import com.neeve.ci.ManifestProductInfo;
import com.neeve.ci.ProductInfo;
import com.neeve.ci.XRuntime;
import com.neeve.cli.annotations.Configured;
import com.neeve.event.alert.IAlertEvent;
import com.neeve.event.lifecycle.LifecycleEvent;
import com.neeve.lang.XLongLinkedHashMap;
import com.neeve.lang.XString;
import com.neeve.managed.ManagedObjectLocator;
import com.neeve.ods.IStoreBinding;
import com.neeve.ods.IStoreBindingRoleChangedEvent;
import com.neeve.ods.StoreDescriptor;
import com.neeve.ods.StoreObjectFactoryRegistry;
import com.neeve.rog.IRogMessage;
import com.neeve.root.RootConfig;
import com.neeve.server.Configurer;
import com.neeve.server.Main;
import com.neeve.server.app.SrvAppLoader;
import com.neeve.server.app.annotations.AppCommandHandler;
import com.neeve.server.app.annotations.AppCommandHandlerContainersAccessor;
import com.neeve.server.app.annotations.AppConfiguredAccessor;
import com.neeve.server.app.annotations.AppEventHandlerAccessor;
import com.neeve.server.app.annotations.AppEventHandlerContainersAccessor;
import com.neeve.server.app.annotations.AppFinalizer;
import com.neeve.server.app.annotations.AppHAPolicy;
import com.neeve.server.app.annotations.AppInitializer;
import com.neeve.server.app.annotations.AppInjectionPoint;
import com.neeve.server.app.annotations.AppMain;
import com.neeve.server.app.annotations.AppStat;
import com.neeve.server.app.annotations.AppStatContainersAccessor;
import com.neeve.server.app.annotations.AppStateFactoryAccessor;
import com.neeve.server.config.SrvConfigAppDescriptor;
import com.neeve.server.controller.SrvController;
import com.neeve.sma.MessageBusDescriptor;
import com.neeve.sma.MessageChannel;
import com.neeve.sma.MessageChannel.Qos;
import com.neeve.sma.MessageChannel.RawKeyResolutionTable;
import com.neeve.sma.MessageChannelDescriptor;
import com.neeve.sma.MessageView;
import com.neeve.sma.MessageViewFactoryRegistry;
import com.neeve.sma.MessageViewTags;
import com.neeve.sma.SmaException;
import com.neeve.sma.event.MessageEvent;
import com.neeve.toa.service.ToaService;
import com.neeve.toa.service.ToaServiceChannel;
import com.neeve.toa.service.ToaServiceToRole;
import com.neeve.toa.spi.ChannelFilterProvider;
import com.neeve.toa.spi.ChannelInitialKeyResolutionTableProvider;
import com.neeve.toa.spi.ChannelQosProvider;
import com.neeve.toa.spi.ServiceDefinitionLocator;
import com.neeve.toa.spi.TopicResolver;
import com.neeve.toa.spi.TopicResolverProvider;
import com.neeve.toa.tools.ToaCodeGenerator;
import com.neeve.trace.Tracer;
import com.neeve.trace.Tracer.Level;
import com.neeve.util.UtlTailoring;
import com.neeve.util.UtlThrowable;

/**
 * Base class for topic oriented applications.
 * <p>
 * <h2>Restrictions on AEP Usage</h2>
 * The {@link TopicOrientedApplication} class reserves usage of several AepEngine features for its
 * own use:
 * <ol>
 * <li><b>Predispatch Message Handler:</b><br>
 * {@link TopicOrientedApplication} reserves the sole right to set {@link AepEngine#setPredispatchMessageHandler(IAepPredispatchMessageHandler)}.
 * Applications may register delegate handlers via {@link #addPredispatchMessageHandler(IAepPredispatchMessageHandler)}.
 * </li>
 * <li><b>Postdispatch Message Handler:</b><br>
 * {@link TopicOrientedApplication} reserves the sole right to set {@link AepEngine#setPostdispatchMessageHandler(IAepPostdispatchMessageHandler)}.
 * Applications may register delegate handlers via {@link #addPostdispatchMessageHandler(IAepPostdispatchMessageHandler)}.
 * </li>
 * <li><b>Server Annotations:</b><br>
 * The following annotations are implemented by TopicOrientedApplication and should not be implemented by subclasses:
 * <ul>
 * <li><b>@{@link AppInjectionPoint}</b><br>
 * {@link TopicOrientedApplication} currently implements these injection points, Subclasses may
 * override the following methods instead:
 * <ul>
 * <li>{@link #onAppLoaderInjected(SrvAppLoader)}
 * <li>{@link #onEngineDescriptorInjected(AepEngineDescriptor)}
 * <li>{@link #onEngineInjected(AepEngine)}
 * </ul>
 * <li>{@link AppInitializer}, use {@link #onAppFinalized()}
 * <li>{@link AppFinalizer}, use {@link #onAppFinalized()}
 * </ul>
 * </li>
 * <li><b>@{@link AppEventHandlerContainersAccessor}, @{@link AppStatContainersAccessor} and  @{@link AppCommandHandlerContainersAccessor}</b><br>
 * Talon Server applications return objects containing event handlers and command handlers via methods on the application class
 * annotated with these annotations respectively. {@link TopicOrientedApplication} implements the accessors for application event 
 * and command handlers, and instead exposes a single collection point for all objects of interest to the server for introspect
 * via a {@link ManagedObjectLocator}. The default {@link ManagedObjectLocator} 
 * </li> 
 * </ul>
 * </ol>
 * <h2>Lifecycle</h2>
 * Apps loaded by a Talon server are initialized using annotations on the application's main class. 
 * The sequence for loading a Talon server application is as follows:
 * <ol>
 * <li> Instantiate the main class which must have a public 0 argument constructor
 * <li> Inspect the main class for Talon app annotations.
 * <li> Inject {@link SrvAppLoader} into a {@link AppInjectionPoint} annotated method to let the application first see the server and its {@link SrvConfigAppDescriptor}.
 * The SrvAppLoader provides the application with the ability to inspect the identity of the server in which it is being launched.
 * <br><i>Note: because TopicOrientedApplication provides this injection point it is illegal for subclasses to implement an {@link AppInjectionPoint} for  {@link SrvAppLoader},
 * subclasses can instead override {@link #onAppLoaderInjected(SrvAppLoader)}.</i> 
 * <li> Inject the {@link AepEngineDescriptor} into an {@link AppInjectionPoint} annotated method to let the app customize its {@link AepEngine}. The application may
 * augment the {@link AepEngineDescriptor}, and to inspect the configuration of the application being launched. 
 * <br><i>Note: because TopicOrientedApplication provides this injection point it is illegal for subclasses to implement an {@link AppInjectionPoint} for  {@link AepEngineDescriptor},
 * subclasses can instead override {@link #onEngineDescriptorInjected(AepEngineDescriptor)}.</i> 
 * <li>Call {@link #getManagedObjectLocator()} and call its {@link ManagedObjectLocator#locateManagedObjects(Set)} method to find objects that expose
 * {@link AppCommandHandler} or {@link EventHandler} annotations.
 * <br><i>Note: {@link TopicOrientedApplication} implements {@link AppCommandHandlerContainersAccessor} and {@link AppEventHandlerContainersAccessor} methods that
 * pass the objects returned from the application's {@link ManagedObjectLocator}, so it is illegal for subclasses to use these annotations</i>
 * <li>Call {@link #getServiceDefinitionLocator()} and invoke its {@link ServiceDefinitionLocator#locateServices(Set)}. {@link TopicOrientedApplication} parses the 
 * service models returned by the {@link ServiceDefinitionLocator} and based on interest defined by the application's {@link EventHandler}s determines which
 * channels to join. 
 * <li>Call {@link #onConfigured()}. At this point the application can call {@link #getServiceModels()} or {@link #getServiceModel(String)} to examine the parsed
 * service model.
 * <li> Call to {@link AppStateFactoryAccessor} annotated method to retrieve the application's state factory (for use with state replication engines). 
 * <li> Construct the AepEngine using the configured {@link AepEngineDescriptor}, {@link IAepApplicationStateFactory}, and {@link EventHandler}s, registering the 
 * {@link SrvAppLoader} as a {@link IAepWatcher} for the engine. 
 * <li> Inject {@link AepEngine} into a {@link AppInjectionPoint} annotated method to provide the application access to its {@link AepEngine}. 
 * <br><i>Note: because TopicOrientedApplication provides this injection point it is illegal for subclasses to implement an {@link AppInjectionPoint} for  {@link AepEngine},
 * subclasses can instead override {@link #onEngineInjected(AepEngine)}. In most cases, the application should not use the {@link AepEngine} directly and instead use the 
 * corresponding facilities provided by {@link TopicOrientedApplication} ({@link MessageSender}, {@link MessageInjector}, {@link EngineClock} etc).</i>  
 * <li> Insepects Managed Object for User defined stats (annotated with @{@link AppStat}. <i>This implies that by the time the call to {@link #onEngineInjected(AepEngine)} returns,
 * all application defined stats should have been constucted by the application.</i>
 * <li> Call {@link #onAppInitialized()} to indicate that the applications has been successfully initialized.
 * <br><i>Note because {@link TopicOrientedApplication} utilizes the {@link AppInitializer} annotation to implement this, applications must not use the {@link AppInitializer}
 * annotation.</i>
 * <li> If the application is annotated with an {@link AppMain} annotation, spin up Main Thread for the application when the AepEngine becomes Primary and invoke it. 
 * For an application that is responsive (i.e. reacts to messages), an application may register additional Aep {@link LifecycleEvent} {@link EventHandler}s to 
 * respond to various application lifecycle events (see link below)
 * <li> When the app is issued a close or unload command, invoke the {@link #onAppFinalized()} method on the app and join its {@link AppMain}n thread if started.
 * <br><i>Note because {@link TopicOrientedApplication} utilizes the {@link AppFinalizer} annotation to implement this, applications must not use the {@link AppInitializer}
 * annotation.</i>
 * </ol>
 * (For an indepth discussion of the underlying {@link AepEngine} lifecycle, see 
 * <a href="http://docs.neeveresearch.com/display/KB/X+Application+Lifecycle">X Application Lifecycle</a>)
 * <p>
 * <h2>Clustering</h2>
 * For a {@link TopicOrientedApplication} to support clustering, subclasses
 * must be annotated with an {@link AppHAPolicy} annotation. 
 */
abstract public class TopicOrientedApplication implements MessageSender, MessageInjector {
    final private static String MINIMUM_TALON_VERSION = "3.1.21";
    final protected static Tracer _tracer = RootConfig.ObjectConfig.createTracer(RootConfig.ObjectConfig.get("nv.toa"));
    static {
        ProductInfo productInfo = ManifestProductInfo.loadProductInfo("nvx-hornet");
        _tracer.log("Loaded X Topic Oriented Application Runtime (" + productInfo.getComponentVersionString() + ")", Tracer.Level.INFO);
        runtimeCompatibilityCheck();
    }

    final private class MessageSendContext {
        final XString busName;
        final XString channelName;
        final String messageType;
        final ToaServiceChannel serviceChannel;
        @SuppressWarnings("rawtypes")
        final TopicResolver topicResolver;
        MessageChannel channel;

        MessageSendContext(final XString busName, final XString channelName, final String messageType, final ToaServiceChannel serviceChannel, final TopicResolver<?> topicResolver) {
            this.busName = busName;
            this.channelName = channelName;
            this.messageType = messageType;
            this.serviceChannel = serviceChannel;
            this.topicResolver = topicResolver;
        }
    }

    /**
     * Tracks configuration state for messages that are declared in event 
     * handlers
     */
    final private class EventHandlerContext {
        final AepEventDispatcher dispatcherPrototype;
        final Class<?> eventClass;
        final boolean isFactoryMessage;
        final boolean localOnly;

        private Class<?> factoryClass;
        private boolean factoryRegistered = false;
        private long uniqueId;

        EventHandlerContext(Class<?> eventClass, AepEventDispatcher dispatcherPrototype) {
            this.dispatcherPrototype = dispatcherPrototype;
            this.eventClass = eventClass;
            if (MessageView.class.isAssignableFrom(eventClass) && eventClass != MessageView.class) {
                this.isFactoryMessage = true;
            }
            else {
                this.isFactoryMessage = false;
            }
            ArrayList<Method> handlerMethods = new ArrayList<Method>();
            dispatcherPrototype.getHandlerMethodsFor(eventClass, handlerMethods);
            boolean localOnly = true;
            for (Method handler : handlerMethods) {
                if (handler.isAnnotationPresent(EventHandler.class)) {
                    if (!handler.getAnnotation(EventHandler.class).localOnly()) {
                        localOnly = false;
                        break;
                    }
                }
                else {
                    localOnly = false;
                    break;
                }
            }

            this.localOnly = localOnly;

        }

        final void registerTypeWithRuntime() throws ToaException {
            if (!isFactoryMessage) {
                return;
            }

            // resolve factory class
            if (factoryClass == null) {
                if (eventClass.isAnnotationPresent(AdmFactoryInfo.class)) {
                    AdmFactoryInfo factoryInfo = eventClass.getAnnotation(AdmFactoryInfo.class);
                    this.factoryClass = factoryInfo.factoryClass();
                    uniqueId = uniqueMessageId(factoryInfo.vfid(), factoryInfo.typeId());
                }
                else {
                    if (eventClass.isAnnotationPresent(AdmGenerated.class)) {
                        StringBuilder error = new StringBuilder();
                        error.append("'" + eventClass + "' is declared in an event handler, but it has no AdmFactoryInfo annotation to provide");
                        error.append(" its factory information. This means its factory can't be registered with the platform which will cause problems deserializing it");
                        error.append(" during recovery and replication. Is its generated source code stale [");
                        AdmCompatibility.getAdmGenerationDetails(eventClass, error);
                        error.append("]?\n");
                        dispatcherPrototype.appendEventHandlerDeclarations(eventClass, " ", error);
                        _tracer.log(error.toString(), Tracer.Level.DEBUG);
                        throw new ToaException(error.toString());
                    }
                    else {
                        StringBuilder error = new StringBuilder();
                        error.append("'" + eventClass + "' is declared in an event handler, but was not generated by ADM and has no AdmFactoryInfo annotation to provide");
                        error.append(" its factory information. This means its factory cannot be registered with platform which will cause problems deserializing it");
                        error.append("  during recovery and replication. Annotate the class with AdmFactoryInfo to allow it to be registered with the platform.");
                        dispatcherPrototype.appendEventHandlerDeclarations(eventClass, " ", error);
                        _tracer.log(error.toString(), Tracer.Level.DEBUG);
                        throw new ToaException(error.toString());
                    }
                }
            }

            if (!factoryRegistered) {
                try {
                    MessageViewFactoryRegistry.getInstance().registerIfNoConflict(factoryClass.getName());
                    StoreObjectFactoryRegistry.getInstance().registerObjectFactory(factoryClass.getName());
                    if (_tracer.debug) {
                        _tracer.log(tracePrefix() + "......'" + factoryClass.getName() + "' for '" + eventClass.getName() + "'.", Tracer.Level.DEBUG);
                    }
                }
                catch (Exception e) {
                    StringBuilder error = new StringBuilder();
                    error.append("Failed to register event hander message factory '").append(factoryClass.getName());
                    error.append("' with the X runtime: [").append(e.getMessage()).append("]");
                    dispatcherPrototype.appendEventHandlerDeclarations(eventClass, " ", error);
                    throw new ToaException(error.toString(), e);
                }

                _factoryRegisteredTypesById.put(uniqueId, eventClass);
                factoryRegistered = true;
            }
        }

        public void setFactoryFromModel(AdmMessage message) {
            if (factoryClass == null) {
                try {
                    factoryClass = Class.forName(message.getFactory().getFullName());
                }
                catch (ClassNotFoundException e) {
                    throw new ToaException("Failed to load factory class for message '" + message.getFullName() + "'", e);
                }
                uniqueId = uniqueMessageId(message.getFactory().calcFactoryId(), message.getId());
            }
        }

    }

    /**
     * Tracks configuration state for messages declared in service model.
     */
    final private class ServiceMessageContext {
        final AdmMessage messageModel;
        final HashMap<String, ToaService> services = new HashMap<String, ToaService>();

        ServiceMessageContext(AdmMessage messageModel) {
            this.messageModel = messageModel;
        }

        /**
         * @param service The
         */
        public void addDeclaringService(ToaService service) {
            services.put(service.getName(), service);
        }

        public void appendDeclaringServices(final String prefix, final StringBuilder builder) {
            builder.append(prefix).append("Services declarating '" + messageModel.getFullName() + "':\n{\n");
            int i = 0;
            for (ToaService service : services.values()) {
                builder.append(prefix).append(" ").append(Integer.toString(i)).append(".) ").append(service.getName());
                i++;
            }
            builder.append("\n}");
        }

        /**
         * Registers the message's view factory with the runtime.
         */
        public void registerTypeWithRuntime() {
            AdmFactory factory = messageModel.getFactory();
            try {
                MessageViewFactoryRegistry.getInstance().registerIfNoConflict(factory.getFullName());
                StoreObjectFactoryRegistry.getInstance().registerObjectFactory(factory.getFullName());
                if (_tracer.debug) {
                    _tracer.log(tracePrefix() + "......'" + factory.getFullName() + "' for '" + messageModel.getFullName() + "'.", Tracer.Level.DEBUG);
                }

                _factoryRegisteredTypesById.put(uniqueMessageId(factory.calcFactoryId(), messageModel.getId()), Class.forName(messageModel.getFullName()));
            }
            catch (Exception e) {
                StringBuilder error = new StringBuilder();
                error.append("Failed to register service declared message factory '").append(factory.getFullName());
                error.append("' with the X runtime: [").append(e.getMessage()).append("]");
                appendDeclaringServices(" ", error);
                throw new ToaException(error.toString(), e);
            }
        }
    }

    private final class EngineTimeImpl implements EngineClock {

        /* (non-Javadoc)
         * @see com.neeve.toa.HAClock#getTime()
         */
        @Override
        public final long getTime() {
            if (_engine != null) {
                return _engine.getEngineTime();
            }
            else {
                return System.currentTimeMillis();
            }
        }

    }

    /**
     * Implements dispatch to multiple {@link IAepPostdispatchMessageHandler}s
     */
    private final class PostdispatchMessageHandlerDispatcher implements IAepPostdispatchMessageHandler {
        private final LinkedHashSet<IAepPostdispatchMessageHandler> registeredHandlers;
        private IAepPostdispatchMessageHandler[] handlerList;
        private volatile boolean handlerAdditionClosed;

        PostdispatchMessageHandlerDispatcher() {
            registeredHandlers = new LinkedHashSet<IAepPostdispatchMessageHandler>();
        }

        /* (non-Javadoc)
         * @see com.neeve.aep.IAepCentralMessageHandler#onMessage(com.neeve.rog.IRogMessage)
         */
        @Override
        public final void postMessage(final IRogMessage message) {
            for (int i = 0; i < handlerList.length; i++) {
                handlerList[i].postMessage(message);
            }
        }

        public final void addHandler(final IAepPostdispatchMessageHandler handler) {
            if (handler == null) {
                throw new IllegalArgumentException("Handler cannot be null");
            }
            if (!handlerAdditionClosed) {
                synchronized (registeredHandlers) {
                    registeredHandlers.add(handler);
                }
            }
            else {
                throw new IllegalStateException("Pre dispatch message handler addition is closed.");
            }
        }

        public final void closeHandlerAddition() {
            handlerAdditionClosed = true;
            synchronized (registeredHandlers) {
                handlerList = new IAepPostdispatchMessageHandler[registeredHandlers.size()];
                int i = 0;
                for (IAepPostdispatchMessageHandler handler : registeredHandlers) {
                    handlerList[i++] = handler;
                }
            }
        }
    }

    /**
     * Implements dispatch to multiple {@link IAepPredispatchMessageHandler}s
     */
    private final class PredispatchMessageHandlerDispatcher implements IAepPredispatchMessageHandler {
        private final LinkedHashSet<IAepPredispatchMessageHandler> registeredHandlers;
        private IAepPredispatchMessageHandler[] handlerList;
        private volatile boolean handlerAdditionClosed;

        PredispatchMessageHandlerDispatcher() {
            registeredHandlers = new LinkedHashSet<IAepPredispatchMessageHandler>();
        }

        /* (non-Javadoc)
         * @see com.neeve.aep.IAepCentralMessageHandler#onMessage(com.neeve.rog.IRogMessage)
         */
        @Override
        public final void onMessage(final IRogMessage message) {
            for (int i = 0; i < handlerList.length; i++) {
                handlerList[i].onMessage(message);
            }
        }

        public final void addHandler(final IAepPredispatchMessageHandler handler) {
            if (handler == null) {
                throw new IllegalArgumentException("Handler cannot be null");
            }
            if (!handlerAdditionClosed) {
                synchronized (registeredHandlers) {
                    registeredHandlers.add(handler);
                }
            }
            else {
                throw new IllegalStateException("Pre dispatch message handler addition is closed.");
            }
        }

        public final void closeHandlerAddition() {
            handlerAdditionClosed = true;
            synchronized (registeredHandlers) {
                handlerList = new IAepPredispatchMessageHandler[registeredHandlers.size()];
                int i = 0;
                for (IAepPredispatchMessageHandler handler : registeredHandlers) {
                    handlerList[i++] = handler;
                }
            }
        }
    }

    private final class FirstMessageValidator {

        @EventHandler
        public void onMessagingPrestart(AepMessagingPrestartEvent event) {

            final MessageView firstMessage = event.getFirstMessage();
            if (firstMessage != null && !_factoryRegisteredTypesById.containsKey(uniqueMessageId(firstMessage.getVfid(), firstMessage.getType()))) {
                //Schedule engine stop before the first message can be processed.
                _engine.stop(new ToaException("Can't use '" + firstMessage.getClass().getName() + "' as a first message it was not registered with the application during initialization. This probably means that you don't have an @EventHandler for it in your application."));
                return;
            }

            List<MessageView> initialMessages = event.getInitialMessages();

            for (int i = 0; i < initialMessages.size(); i++) {
                MessageView initialMessage = initialMessages.get(i);
                if (!_factoryRegisteredTypesById.containsKey(uniqueMessageId(initialMessage.getVfid(), initialMessage.getType()))) {
                    //Schedule engine stop before the initial message can be processed.
                    _engine.stop(new ToaException("Can't use '" + initialMessage.getClass().getName() + "' as an initial message it was not registered with the application during initialization. This probably means that you don't have an @EventHandler for it in your application."));
                    return;
                }
            }
        }
    }

    /**
     * Subclasses may use this tracer for trace logging.
     */
    private final Map<String, Map<String, List<Long>>> _channelMessageMapByBus;
    private final XLongLinkedHashMap<MessageSendContext> _messageChannelMap;
    private final XLongLinkedHashMap<Class<?>> _factoryRegisteredTypesById;

    private final Set<ToaService> services = new HashSet<ToaService>();
    private final EngineTimeImpl _engineClock = new EngineTimeImpl();
    private final PredispatchMessageHandlerDispatcher predispatchMessageHandlerDispatcher = new PredispatchMessageHandlerDispatcher();
    private final PostdispatchMessageHandlerDispatcher postdispatchMessageHandlerDispatcher = new PostdispatchMessageHandlerDispatcher();

    private AepEngine.HAPolicy _haPolicy;
    private IStoreBinding.Role _role;
    private AepEngineDescriptor _engineDescriptor;
    private AepEngine _engine;
    private String _engineName;
    private Configurer configurer;
    private volatile boolean messagingConfigured = false;
    private LinkedHashSet<Object> managedObjects = new LinkedHashSet<Object>(); //The managed objects (important to maintain addition order)
    private ManagedObjectLocator managedObjectLocator;
    private Tracer.Level alertTraceLevel = Tracer.getLevel(XRuntime.getValue("nv.toa.alerttracelevel", Tracer.Level.WARNING.name()));

    /**
     * Default constructor.
     */
    protected TopicOrientedApplication() {
        _channelMessageMapByBus = new HashMap<String, Map<String, List<Long>>>();
        _messageChannelMap = XLongLinkedHashMap.newInstance();
        _factoryRegisteredTypesById = XLongLinkedHashMap.newInstance();

        // validate that the subclass isn't using unsupported annotations:
        for (Method method : getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(AppInjectionPoint.class) && method.getDeclaringClass() != TopicOrientedApplication.class) {
                throw new UnsupportedOperationException("Usage of " + AppInjectionPoint.class.getSimpleName() + " annotation is unsupported for " + TopicOrientedApplication.class.getSimpleName() + " subclasses. '" + method + "' is therefore not valid!");
            }

            if (method.isAnnotationPresent(AppCommandHandlerContainersAccessor.class) && method.getDeclaringClass() != TopicOrientedApplication.class) {
                throw new UnsupportedOperationException("Usage of " + AppCommandHandlerContainersAccessor.class.getSimpleName() + " annotation is unsupported for " + TopicOrientedApplication.class.getSimpleName() + " subclasses. '" + method + "' is therefore not valid!");
            }

            if (method.isAnnotationPresent(AppEventHandlerAccessor.class) && method.getDeclaringClass() != TopicOrientedApplication.class) {
                throw new UnsupportedOperationException("Usage of " + AppEventHandlerAccessor.class.getSimpleName() + " annotation is unsupported for " + TopicOrientedApplication.class.getSimpleName() + " subclasses. '" + method + "' is therefore not valid!");
            }

            if (method.isAnnotationPresent(AppInitializer.class) && method.getDeclaringClass() != TopicOrientedApplication.class) {
                throw new UnsupportedOperationException("Usage of " + AppInitializer.class.getSimpleName() + " annotation is unsupported for " + TopicOrientedApplication.class.getSimpleName() + " subclasses. '" + method + "' is therefore not valid!");
            }

            if (method.isAnnotationPresent(AppFinalizer.class) && method.getDeclaringClass() != TopicOrientedApplication.class) {
                throw new UnsupportedOperationException("Usage of " + AppFinalizer.class.getSimpleName() + " annotation is unsupported for " + TopicOrientedApplication.class.getSimpleName() + " subclasses. '" + method + "' is therefore not valid!");
            }
        }
    }

    /**
     * Checks compatibility with Core X
     */
    private static void runtimeCompatibilityCheck() {
        if (!XRuntime.getValue("nv.toa.disablecompatcheck", false)) {
            final com.neeve.nvx.talon.Version talonVersion = new com.neeve.nvx.talon.Version();
            final String[] requiredMinVersionComponents = MINIMUM_TALON_VERSION.split("\\.");
            final String[] componentVersions = talonVersion.getFullVersion().split("\\.");
            try {
                for (int i = 0; i < requiredMinVersionComponents.length; i++) {
                    try {
                        int required = Integer.parseInt(requiredMinVersionComponents[i]);
                        int available = componentVersions.length > i ? Integer.parseInt(componentVersions[i]) : 0;
                        //Break on newer version:
                        if (available > required) {
                            break;
                        }
                        //Fail on older
                        if (available < required) {
                            throw new ToaException("This version of TopicOrientedApplication requires at least nvx-talon version '" + MINIMUM_TALON_VERSION + "', but '" + talonVersion.getFullVersion() + "' was found.");
                        }
                    }
                    catch (NumberFormatException nfe) {
                        String required = requiredMinVersionComponents[i];
                        String available = componentVersions.length > i ? componentVersions[i] : "0";
                        int comparison = available.compareTo(required);
                        if (comparison < 0) {
                            throw new ToaException("This version of TopicOrientedApplication requires at least nvx-talon version '" + MINIMUM_TALON_VERSION + "', but '" + talonVersion.getFullVersion() + "' was found.");
                        }
                        if (comparison > 0) {
                            break;
                        }
                    }
                }
            }
            catch (Throwable thrown) {
                throw new ToaException("Core X runtime compatibility check failed.", thrown);
            }
        }
    }

    final private long uniqueMessageId(final int factoryId, final int messageId) {
        return (((long)factoryId) << 32) | messageId;
    }

    @SuppressWarnings("unchecked")
    final private void sendMessage(final IRogMessage message,
                                   final String topic,
                                   final Properties keyResolutionTable,
                                   final XString rawTopic,
                                   final RawKeyResolutionTable rawKeyResolutionTable) {
        final long uniqueMessageId = uniqueMessageId(message.getVfid(), message.getType());
        if (_tracer.debug) _tracer.log(tracePrefix() + "Sending message '" + message.getClass().getSimpleName() + "' <id=" + uniqueMessageId + "'(vfid=" + message.getVfid() + ", id=" + message.getType() + ")>...", Tracer.Level.DEBUG);
        final MessageSendContext sendContext = _messageChannelMap.get(uniqueMessageId);
        if (sendContext != null) {
            if (sendContext.channel != null || (_haPolicy == AepEngine.HAPolicy.EventSourcing && _role != IStoreBinding.Role.Primary)) { // role == null i.e. initializing is also covered by role != Primary
                message.setMessageBusAsRaw(sendContext.busName);
                message.setMessageChannelAsRaw(sendContext.channelName);

                if (topic == null && rawTopic == null && sendContext.topicResolver != null) {
                    if (rawKeyResolutionTable != null) {
                        XString resolvedTopic;
                        try {
                            resolvedTopic = sendContext.topicResolver.resolveTopic(message, rawKeyResolutionTable);
                        }
                        catch (Exception e) {
                            throw new ToaException("Error resolving topic for '" + sendContext.messageType + "' being sent on channel '" + sendContext.channel.getName() + "' using topic resolver: " + e.getMessage(), e);
                        }
                        message.setMessageKeyAsRaw(resolvedTopic);
                        _engine.sendMessage(sendContext.channel, message);
                    }
                    else {
                        final XString resolvedTopic;
                        try {
                            resolvedTopic = sendContext.topicResolver.resolveTopic(message, keyResolutionTable);
                        }
                        catch (Exception e) {
                            throw new ToaException("Error resolving topic for '" + sendContext.messageType + "' being sent on channel '" + sendContext.channel.getName() + "' using topic resolver: " + e.getMessage(), e);
                        }
                        message.setMessageKeyAsRaw(resolvedTopic);
                        _engine.sendMessage(sendContext.channel, message);
                    }
                }
                else if (topic != null) {
                    _engine.sendMessage(sendContext.channel,
                                        message,
                                        topic,
                                        keyResolutionTable);
                }
                else {
                    if (rawKeyResolutionTable == null) {
                        _engine.sendMessage(sendContext.channel,
                                            message,
                                            topic,
                                            keyResolutionTable);
                    }
                    else {
                        _engine.sendMessage(sendContext.channel,
                                            message,
                                            rawTopic,
                                            rawKeyResolutionTable);
                    }
                }
                if (_tracer.debug) _tracer.log(tracePrefix() + "...message sent on '" + sendContext.channelName + "' channel with topic '" + message.getMessageKey() + "'.", Tracer.Level.DEBUG);
            }
            else {
                if (_tracer.debug) _tracer.log(tracePrefix() + "...channel '" + sendContext.channelName + "' is not ready for messaging.", Tracer.Level.DEBUG);
                throw new ToaException("channel '" + sendContext.channelName + "' is not ready for messaging, can't send '" + message.getClass().getName() + "'");
            }
        }
        else {
            if (_tracer.debug) _tracer.log(tracePrefix() + "...no channel associated for message.", Tracer.Level.DEBUG);
            throw new ToaException("no channel associated with message '" + message.getClass().getName() + "'");
        }
    }

    final private void configureMessaging(final Set<URL> serviceUrls, final Set<Object> handlerContainers) {
        // trace
        _tracer.log(tracePrefix() + "Configuring messaging...", Tracer.Level.CONFIG);

        // get services
        _tracer.log(tracePrefix() + "...parsing services (count=" + serviceUrls.size() + ").", Tracer.Level.CONFIG);
        final Map<ToaService, ToaServiceChannel> defaultChannels = new HashMap<ToaService, ToaServiceChannel>();
        for (URL url : serviceUrls) {
            if (url == null) {
                throw new ToaException("null service url return by the service locator for app '" + _engineDescriptor.getName() + "'!");
            }

            try {
                _tracer.log(tracePrefix() + "......'" + url + "'.", Tracer.Level.CONFIG);
                final ToaService service = ToaService.unmarshal(url);
                if (!services.add(service)) {
                    _tracer.log(tracePrefix() + "...ignore duplicate service '" + service.getName() + "' from " + url, Tracer.Level.CONFIG);
                }

                defaultChannels.put(service, service.getDefaultChannel());
            }
            catch (Exception e) {
                throw new ToaException(e);
            }
        }

        // get the set of handled event classes
        _tracer.log(tracePrefix() + "...parsing handled messages and events...", Tracer.Level.CONFIG);
        final AepEventDispatcher eventDispatcherPrototype = AepEventDispatcher.create(handlerContainers, null);
        final HashMap<String, EventHandlerContext> eventHandlersByClass = new HashMap<String, EventHandlerContext>();
        for (Class<?> clazz : eventDispatcherPrototype.getHandledEventClasses()) {
            EventHandlerContext handlerContext = new EventHandlerContext(clazz, eventDispatcherPrototype);
            eventHandlersByClass.put(clazz.getName(), handlerContext);
            _tracer.log(tracePrefix() + "......'" + clazz.getName() + "'.", Tracer.Level.CONFIG);
        }

        // prepare default channel map
        _tracer.log(tracePrefix() + "...preparing default channel list...", Tracer.Level.CONFIG);

        //Prepare the TopicResolverProviders set:
        final HashSet<TopicResolverProvider> topicResolverProviders = new HashSet<TopicResolverProvider>();
        for (Object o : managedObjects) {
            if (o instanceof TopicResolverProvider) {
                topicResolverProviders.add((TopicResolverProvider)o);
            }
        }

        // prepare map that contains the channels to join and the map containing the messages to send for each channel
        _tracer.log(tracePrefix() + "...preparing join channel list and message channel map...", Tracer.Level.CONFIG);
        final boolean genericHandlerJoinsAll = XRuntime.getValue("nv.toa.generichandlerjoinsall", true);
        final EventHandlerContext genericMessageViewHandler = eventHandlersByClass.get(MessageView.class);
        final EventHandlerContext genericMessageEventHandler = eventHandlersByClass.get(MessageEvent.class);
        final Map<ToaService, Set<ToaServiceChannel>> joinChannels = new HashMap<ToaService, Set<ToaServiceChannel>>();
        final HashMap<String, ServiceMessageContext> serviceDeclaredMessages = new HashMap<String, ServiceMessageContext>();
        for (ToaService service : services) {
            for (ToaServiceToRole to : service.getToRoles()) {
                for (AdmMessage admMessage : to.getMessages()) {
                    // trace
                    _tracer.log(tracePrefix() + "......message '" + admMessage.getName() + "'...", Tracer.Level.CONFIG);
                    ServiceMessageContext messageContext = serviceDeclaredMessages.get(admMessage.getFullName());
                    if (messageContext == null) {
                        messageContext = new ServiceMessageContext(admMessage);
                        serviceDeclaredMessages.put(admMessage.getFullName(), messageContext);
                    }
                    final long uniqueMessageId = uniqueMessageId(admMessage.getFactory().calcFactoryId(), admMessage.getId());
                    messageContext.addDeclaringService(service);

                    // find message's channel
                    final ToaServiceChannel toaChannel = to.getChannel(admMessage.getFullName());
                    _tracer.log(tracePrefix() + ".........<channel='" + toaChannel.getName() + "', id=" + uniqueMessageId + "(vfid=" + admMessage.getFactory().calcFactoryId() + ", id=" + admMessage.getId() + ")>.", Tracer.Level.CONFIG);

                    // fail if interface based handlers are detected:
                    String interfaceName = admMessage.getNamespace() + ".I" + admMessage.getJavaTypeName();
                    boolean interfaceHandler = eventHandlersByClass.containsKey(interfaceName);

                    if (toaChannel.getBusName() == null) {
                        toaChannel.setBusName(_engineName);
                    }

                    if (interfaceHandler) {
                        List<Method> invalidHandlers = new ArrayList<Method>();
                        try {
                            eventDispatcherPrototype.getHandlerMethodsFor(Class.forName(interfaceName), invalidHandlers);
                        }
                        catch (Exception e) {
                            _tracer.log("Failed to look up invalid event handler methods: " + UtlThrowable.prepareStackTrace(e), Tracer.Level.DEBUG);
                        }
                        StringBuilder error = new StringBuilder();
                        error.append("Interface based message EventHandler(s) detected for '").append(interfaceName);
                        error.append("', but interface based EventHandlers are not supported. Channel '").append(toaChannel.getName()).append("' can't be joined!");
                        if (!invalidHandlers.isEmpty()) {
                            error.append(" EventHandler Declarations:\n{\n");
                            for (int i = 0; i < invalidHandlers.size(); i++) {
                                error.append(" ").append(i + 1).append(".) ").append(invalidHandlers.get(i).toString());
                            }
                            error.append("\n}");
                        }

                        _tracer.log(tracePrefix() + "ERROR: " + error.toString(), Tracer.Level.SEVERE);
                        throw new ToaException(error.toString());
                    }

                    // check for an event handler to see if channel should be joined
                    EventHandlerContext eventHandler = eventHandlersByClass.get(admMessage.getFullName());
                    if (eventHandler != null) {
                        // set the model to help with factory lookup:
                        eventHandler.setFactoryFromModel(admMessage);
                    }

                    // join the channel if event handlers are not marked for only local message dispatch
                    if ((eventHandler != null && !eventHandler.localOnly) ||
                            (genericHandlerJoinsAll &&
                            ((genericMessageEventHandler != null && !genericMessageEventHandler.localOnly) ||
                            (genericMessageViewHandler != null && !genericMessageViewHandler.localOnly)))) {
                        Set<ToaServiceChannel> serviceJoinChannels = joinChannels.get(service);
                        if (serviceJoinChannels == null) {
                            joinChannels.put(service, serviceJoinChannels = new HashSet<ToaServiceChannel>());
                        }
                        serviceJoinChannels.add(toaChannel);
                    }

                    // add the message to the list of messages to be sent on the channel
                    Map<String, List<Long>> channelMap = _channelMessageMapByBus.get(toaChannel.getBusName());
                    if (channelMap == null) {
                        channelMap = new HashMap<String, List<Long>>();
                        _channelMessageMapByBus.put(toaChannel.getBusName(), channelMap);
                    }
                    List<Long> ids = channelMap.get(toaChannel.getName());
                    if (ids == null) {
                        channelMap.put(toaChannel.getName(), ids = new ArrayList<Long>());
                    }
                    ids.add(uniqueMessageId);

                    // create a send context for this message but with channel as null. the 
                    // channel will be filled in later when the channel up notification is 
                    // received

                    String messageType = admMessage.getFullName();
                    TopicResolver<?> topicResolver = null;
                    Class<?> messageClass = null;
                    try {
                        messageClass = Class.forName(admMessage.getFullName());
                        if (!MessageView.class.isAssignableFrom(messageClass)) {
                            messageClass = null;
                            _tracer.log("Message class found for '" + messageType + "', but does not implement MessageView. TopicResolver lookup will be unavailable!", Tracer.Level.SEVERE);
                        }
                    }
                    catch (ClassNotFoundException e) {
                        _tracer.log("Message class not found for '" + messageType + "', TopicResolver lookup will be unavailable", Tracer.Level.SEVERE);
                    }

                    if (messageClass != null) {
                        TopicResolverProvider topicResolverProvider = null;
                        for (TopicResolverProvider provider : topicResolverProviders) {
                            TopicResolver<?> resolver = provider.getTopicResolver(service, toaChannel, messageClass);
                            if (resolver != null) {
                                // assumes that a user override will be added after this class' default resolver and we 
                                // want to favor user override. 
                                if (topicResolver != null) {
                                    _tracer.log("Found multiple topic resolvers for message '" + messageType + "': '" + resolver.getClass() + "' provided by '" + provider.getClass() + "' will be used instead of '" + topicResolver + "' provided by '" + topicResolverProvider.getClass() + "'", Tracer.Level.WARNING);
                                }
                                topicResolver = resolver;
                                topicResolverProvider = provider;
                            }
                        }
                    }

                    _messageChannelMap.put(uniqueMessageId, new MessageSendContext(XString.create(toaChannel.getBusName(), true, true), XString.create(toaChannel.getName(), true, true), admMessage.getFullName(), toaChannel, topicResolver));
                }
            }
        }

        _tracer.log(tracePrefix() + "......channels to join...", Tracer.Level.CONFIG);
        for (ToaService service : joinChannels.keySet()) {
            _tracer.log(tracePrefix() + ".........service '" + service.getName() + "'...", Tracer.Level.CONFIG);
            Set<ToaServiceChannel> serviceJoinChannels = joinChannels.get(service);
            if (serviceJoinChannels.size() > 0) {
                for (ToaServiceChannel channel : serviceJoinChannels) {
                    _tracer.log(tracePrefix() + "............'" + channel.getName() + "'.", Tracer.Level.CONFIG);
                }
            }
            else {
                _tracer.log(tracePrefix() + "............<no channels to join>.", Tracer.Level.CONFIG);
            }
        }

        // prepare the ChannelFilterProviders set:
        final HashSet<ChannelFilterProvider> channelFilterProviders = new HashSet<ChannelFilterProvider>();
        for (Object o : managedObjects) {
            if (o instanceof ChannelFilterProvider) {
                channelFilterProviders.add((ChannelFilterProvider)o);
            }
        }

        // prepare the ChannelQosProviders set:
        final HashSet<ChannelQosProvider> channelQosProviders = new HashSet<ChannelQosProvider>();
        for (Object o : managedObjects) {
            if (o instanceof ChannelQosProvider) {
                channelQosProviders.add((ChannelQosProvider)o);
            }
        }

        // prepare the ChannelInitialKeyResolutionTableProvider set:
        final HashSet<ChannelInitialKeyResolutionTableProvider> channelKRTProviders = new HashSet<ChannelInitialKeyResolutionTableProvider>();
        for (Object o : managedObjects) {
            if (o instanceof ChannelInitialKeyResolutionTableProvider) {
                channelKRTProviders.add((ChannelInitialKeyResolutionTableProvider)o);
            }
        }

        // prepare the bus descriptor and, while doing so, add channels to the engine descriptor to register interest
        try {
            for (String busName : _channelMessageMapByBus.keySet()) {
                _tracer.log(tracePrefix() + "...adding channels to bus descriptor '" + busName + "'...", Tracer.Level.CONFIG);
                final MessageBusDescriptor busDescriptor = MessageBusDescriptor.load(busName);
                for (ToaService service : services) {
                    for (ToaServiceChannel channel : service.getChannels()) {
                        if (!busName.equals(channel.getBusName())) {
                            continue;
                        }

                        MessageChannelDescriptor channelDescriptor = busDescriptor.getChannel(channel.getName());
                        if (channelDescriptor == null) {
                            channelDescriptor = MessageChannelDescriptor.create(channel.getName(), busDescriptor);
                            busDescriptor.addChannel(channelDescriptor);
                        }

                        // resolve qos:
                        Qos channelQos = channelDescriptor.getChannelQos();
                        if (channelQos == null) {
                            channelQos = Qos.Guaranteed;
                        }
                        ChannelQosProvider qosProvider = null;
                        for (ChannelQosProvider provider : channelQosProviders) {
                            Qos qos = provider.getChannelQos(service, channel);
                            if (qos != null) {
                                _tracer.log(tracePrefix() + "......channel Qos for '" + channelDescriptor.getName() + "' '" + qos + "' (provided by: '" + provider.getClass().getName() + "').", Tracer.Level.CONFIG);

                                if (qosProvider == null) {
                                    channelQos = qos;
                                    qosProvider = provider;
                                }
                                else if (channelQos == Qos.BestEffort && qos == Qos.Guaranteed) {
                                    channelQos = qos;
                                    qosProvider = provider;
                                }
                            }
                        }
                        channelDescriptor.setChannelQos(channelQos);

                        //Check if the key is already defined in the channel.
                        String key = null;
                        if (channel.getKey() != null) {
                            key = channel.getKey();
                            if (channelDescriptor.getChannelKey() != null && key == null) {
                                key = channelDescriptor.getChannelKey();
                                _tracer.log(tracePrefix() + "......Using pre defined channel key '" + channel.getName() + "', key=" + key, Tracer.Level.CONFIG);
                            }
                        }

                        // resolve krt:
                        Properties initialKRT = null;
                        ChannelInitialKeyResolutionTableProvider krtProvider = null;
                        for (ChannelInitialKeyResolutionTableProvider provider : channelKRTProviders) {
                            Properties krt = provider.getInitialChannelKeyResolutionTable(service, channel);
                            if (krt != null) {
                                if (initialKRT != null) {
                                    throw new ToaException("Duplicate Initial KRT providers for channel '" + channel.getSimpleName() + "' in service'" + service.getName() + "'!"
                                            + " '" + krtProvider.getClass().getName() + "' provided '" + initialKRT + "'"
                                            + ", and '" + provider.getClass().getName() + "' provided '" + krt + "'");
                                }
                                krtProvider = provider;
                                initialKRT = krt;
                                _tracer.log(tracePrefix() + "......channel initial KRT for '" + channelDescriptor.getName() + "' is '" + krt + "' (provided by: '" + krtProvider.getClass().getName() + "').", Tracer.Level.CONFIG);
                            }
                        }

                        channel.setKey(key);

                        if (initialKRT != null && key != null) {
                            channel.setInitialKRT(initialKRT);
                            _tracer.log(tracePrefix() + "......performing initial channel key resolution for channel '" + channel.getName() + "', key=" + key, Tracer.Level.CONFIG);
                            key = UtlTailoring.springScanAndReplace(key, initialKRT, true);
                        }
                        //Update the model with the overridden key:
                        channel.setInitiallyResolvedKey(key);
                        channelDescriptor.setChannelKey(key);

                        // resolve channel filter
                        String channelFilter = null;
                        ChannelFilterProvider filterProvider = null;
                        for (ChannelFilterProvider provider : channelFilterProviders) {
                            String filter = provider.getChannelFilter(service, channel);
                            if (filter != null) {
                                if (channelFilter != null && !channelFilter.equals(filter)) {
                                    throw new ToaException("Conflicting channel filters provided for channel '" + channel.getSimpleName() + "' in service '" + service.getName() + "'!"
                                            + " '" + filterProvider.getClass().getName() + "' provided '" + channelFilter + "'"
                                            + ", but '" + provider.getClass().getName() + "' provided '" + filter + "'");
                                }
                                filterProvider = provider;
                                channelFilter = filter;
                                _tracer.log(tracePrefix() + "......channel filter for '" + channelDescriptor.getName() + "' '" + channelFilter + "' (provided by: '" + filterProvider.getClass().getName() + "').", Tracer.Level.CONFIG);
                            }
                        }

                        if (channelFilter != null) {
                            channelDescriptor.setChannelFilter(channelFilter);
                        }
                        else {
                            channelFilter = channelDescriptor.getChannelFilter();
                            _tracer.log(tracePrefix() + "......channel filter for '" + channelDescriptor.getName() + "' '" + channelFilter + "' already defined in channel descriptor.", Tracer.Level.CONFIG);
                        }

                        // add to engine
                        final boolean join = (joinChannels.get(service) != null && joinChannels.get(service).contains(channel));
                        _engineDescriptor.addChannel(busDescriptor.getName(),
                                                     channel.getName(),
                                                     AepEngineDescriptor.ChannelConfig.from("join=" + join));
                        // trace
                        _tracer.log(tracePrefix() + "......channel '" + channelDescriptor.getName() + "' configured (qos=" + channelDescriptor.getChannelQos() + ", key=" + channelDescriptor.getChannelKey() + ", filter=" + channelDescriptor.getChannelFilter() + ", join=" + join + ")...", Tracer.Level.CONFIG);
                    }
                }
                busDescriptor.save(busName);
            }
        }
        catch (SmaException e) {
            throw new ToaException(e);
        }

        // initialize TopicResolvers. This is done after we parse initial KRTs above
        // so they can be used by the resolvers. 
        for (MessageSendContext context : _messageChannelMap.values()) {
            if (context.topicResolver != null) {
                context.topicResolver.initialize(context.serviceChannel);
            }
        }

        // register service defined message factories 
        if (_tracer.debug) _tracer.log(tracePrefix() + "...registering service declared message view factories...", Tracer.Level.DEBUG);
        for (ServiceMessageContext context : serviceDeclaredMessages.values()) {
            context.registerTypeWithRuntime();
        }

        if (_tracer.debug) _tracer.log(tracePrefix() + "...registering event handler declared message view factories...", Tracer.Level.DEBUG);
        // register event handler factories. This covers registration of factories for messages that
        // don't come in over SMA such as injected messages and first messages which need to be
        // registered for event sourcing. 
        for (EventHandlerContext context : eventHandlersByClass.values()) {
            context.registerTypeWithRuntime();

            //            if (context.factoryRegistered && !context.foundServiceDefinition) {
            //                StringBuilder warning = new StringBuilder();
            //                warning.append("'" + eventClass + "' is declared in an event handler, but not found in a corresponding service definition"); 
            //                warning.append("]?");
            //
            //                _tracer.log(tracePrefix() + "......'" + context.eventClass.getName() + "' was declared in an event han.", Tracer.Level.WARN);
            //            }

        }

        StringBuilder factoryDump = new StringBuilder();
        MessageViewFactoryRegistry.getInstance().dumpFactoryVersionInfo(factoryDump);
        _tracer.log(tracePrefix() + "...registered message view factories:\n" + factoryDump, Tracer.Level.CONFIG);
        messagingConfigured = true;
    }

    /**
     * This method may be overridden by subclasses to add additional objects that contain
     * methods with {@link EventHandler} annotations. 
     * <p>
     * This method is called by the {@link DefaultManagedObjectLocator}, if an application
     * provides its own {@link ManagedObjectLocator} then it is up to that locator as
     * to whether or not this method will be invoked.
     * <p>
     * This method is called by the application during the configuration phase
     * allowing the application subclass to register event handler containers
     * by adding objects having methods annotated with {@link EventHandler} that 
     * will serve as the application's message and event handlers. 
     * <p>
     * This class is automatically added as an event handler container, subclasses
     * should not add itself to the set.
     * 
     * @param containers Objects with {@link EventHandler} methods should be added to this set.
     */
    protected void addHandlerContainers(Set<Object> containers) {}

    /**
     * This method may be overridden by subclasses to add additional objects that contain
     * methods with {@link AppCommandHandler} annotations.
     * <p>
     * This method is called by the {@link DefaultManagedObjectLocator}, if an application
     * provides its own {@link ManagedObjectLocator} then it is up to that locator as
     * to whether or not this method will be invoked.
     * <p>
     * This method is called by the application during the configuration phase
     * allowing the application subclass to register command handler containers
     * by adding objects having methods annotated with {@link AppCommandHandler} that 
     * will serve as the application's command handlers. 
     * <p>
     * This class is automatically added as an command handler container, subclasses
     * should not add itself to the set.
     * 
     * @param containers Objects with {@link AppCommandHandler} methods should be added to this set.
     */
    protected void addAppCommandHandlerContainers(Set<Object> containers) {}

    /**
     * This method may be overridden by subclasses to add additional objects that contain
     * methods with {@link AppStat} annotations.
     * <p>
     * This method is called by the {@link DefaultManagedObjectLocator}, if an application
     * provides its own {@link ManagedObjectLocator} then it is up to that locator as
     * to whether or not this method will be invoked.
     * <p>
     * This method is called by the application during the configuration phase
     * allowing the application subclass to register command handler containers
     * by adding objects having methods annotated with {@link AppStat} that 
     * will serve as the application's command handlers. 
     * <p>
     * This class is automatically added as an command handler container, subclasses
     * should not add itself to the set.
     * 
     * @param containers Objects with {@link AppStat} should be added to this set.
     */
    protected void addAppStatContainers(Set<Object> containers) {}

    /**
     * This method may be overridden by subclasses to add additional objects that contain
     * methods with {@link Configured} annotations.
     * <p>
     * This method is called by the {@link DefaultManagedObjectLocator}, if an application
     * provides its own {@link ManagedObjectLocator} then it is up to that locator as
     * to whether or not this method will be invoked.
     * <p>
     * This method is called by the application during the configuration phase
     * allowing the application subclass to register command handler containers
     * by adding objects having methods annotated with {@link Configured} that 
     * will serve as the application's command handlers. 
     * <p>
     * This class is automatically added as an command handler container, subclasses
     * should not add itself to the set.
     * 
     * @param containers Objects with {@link Configured} should be added to this set.
     */
    protected void addConfiguredContainers(Set<Object> containers) {}

    /**
     * This method may be overridden by subclasses to add additional objects that implement
     * {@link ChannelFilterProvider}. 
     * <p>
     * This method is called by the {@link DefaultManagedObjectLocator}, if an application
     * provides its own {@link ManagedObjectLocator} then it is up to that locator as
     * to whether or not this method will be invoked.
     * <p>
     * This method is called by the application during the configuration phase
     * allowing the application subclass to register {@link ChannelFilterProvider}s
     * <p>
     * The default implementation of this method adds a filter provider that calls
     * {@link #getChannelFilter(ToaService, ToaServiceChannel)}
     * 
     * @param containers Objects implementing {@link ChannelFilterProvider} should be added to this set.
     */
    protected void addChannelFilterProviders(final Set<Object> containers) {
        containers.add(new ChannelFilterProvider() {

            @Override
            public String getChannelFilter(ToaService service, ToaServiceChannel channel) {
                return TopicOrientedApplication.this.getChannelFilter(service, channel);
            }
        });
    }

    /**
     * This method may be overridden by subclasses to add additional objects that implement
     * {@link TopicResolverProvider}. 
     * <p>
     * This method is called by the {@link DefaultManagedObjectLocator}, if an application
     * provides its own {@link ManagedObjectLocator} then it is up to that locator as
     * to whether or not this method will be invoked.
     * <p>
     * This method is called by the application during the configuration phase
     * allowing the application subclass to register {@link TopicResolverProvider}s
     * <p>
     * The default implementation of this method adds 2 {@link TopicResolverProvider}s:
     * <ul>
     * <li>A {@link GeneratedTopicResolverProvider} which will search the classpath for channel resolvers
     * generated by the {@link ToaCodeGenerator}.
     * <li>A TopicResolverProvider that calls {@link #getChannelFilter(ToaService, ToaServiceChannel)}, allowing
     * subclasses to return a {@link TopicResolver}.
     * </ul>
     * @param containers Objects implementing {@link ChannelFilterProvider} should be added to this set.
     */
    protected void addTopicResolverProviders(final Set<Object> containers) {
        containers.add(new GeneratedTopicResolverProvider());
        containers.add(new TopicResolverProvider() {

            @Override
            public TopicResolver<?> getTopicResolver(ToaService service, ToaServiceChannel channel, Class<?> messageType) {
                return TopicOrientedApplication.this.getTopicResolver(service, channel, messageType);
            }
        });
    }

    /**
     * This method may be overridden by subclasses to return a {@link TopicResolver} for
     * messages of the given type sent on the given channel. 
     * <p>
     * This method will not be called unless {@link #addTopicResolverProviders(Set)} is
     * called on this class. This is done by the {@link DefaultManagedObjectLocator}, 
     * but may not be called if the application defines its own {@link ManagedObjectLocator}.
     * 
     * @param serviceName The service name. 
     * @param channelName The name of the channel to filter. 
     * @param messageType The concrete class of the message. 
     * 
     * @return A {@link TopicResolver} for the message when it is sent of the given channel.
     */
    protected TopicResolver<?> getTopicResolver(ToaService serviceName, ToaServiceChannel channelName, Class<?> messageType) {
        return null;
    }

    /**
     * This method may be overridden by subclasses to associate a channel filter
     * with the application. 
     * <p>
     * This method will not be called unless {@link #addChannelFilterProviders(Set)} is
     * called on this class. This is done by the {@link DefaultManagedObjectLocator}, 
     * but may not be called if the application defines its own {@link ManagedObjectLocator}.
     * 
     * @param serviceName The service name. 
     * @param channelName The name of the channel to filter. 
     * @return A channel filter or null if the channel should not be filtered. 
     */
    protected String getChannelFilter(ToaService serviceName, ToaServiceChannel channelName) {
        return null;
    }

    /**
     * This method may be overridden by subclasses to add additional objects that implement
     * {@link ChannelQosProvider}. 
     * <p>
     * This method is called by the {@link DefaultManagedObjectLocator}, if an application
     * provides its own {@link ManagedObjectLocator} then it is up to that locator as
     * to whether or not this method will be invoked.
     * <p>
     * This method is called by the application during the configuration phase
     * allowing the application subclass to register {@link ChannelQosProvider}s
     * <p>
     * The default implementation of this method adds a {@link ChannelQosProvider} that calls
     * {@link #getChannelFilter(ToaService, ToaServiceChannel)} on this class.
     * 
     * @param containers Objects implementing {@link ChannelQosProvider} should be added to this set.
     */
    protected void addChannelQosProviders(Set<Object> containers) {
        containers.add(new ChannelQosProvider() {

            @Override
            public Qos getChannelQos(ToaService service, ToaServiceChannel channel) {
                return TopicOrientedApplication.this.getChannelQos(service, channel);
            }
        });
    }

    /**
     * This method may be overridden by subclasses to specify the {@link Qos} for 
     * the provided service channel. 
     * <p>
     * The default implementation returns null which in the absense of .
     * <p>
     * This method will not be called unless {@link #addChannelQosProviders(Set)} is
     * called on this class. This is done by the {@link DefaultManagedObjectLocator}, 
     * but may not be called if the application defines its own {@link ManagedObjectLocator}.
     * 
     * @param service The service
     * @param channel The channel.
     * @return The quality of service. 
     */
    protected Qos getChannelQos(ToaService service, ToaServiceChannel channel) {
        return null;
    }

    /**
     * This method may be overridden by subclasses to add additional objects that implement
     * {@link ChannelQosProvider}. 
     * <p>
     * This method is called by the {@link DefaultManagedObjectLocator}, if an application
     * provides its own {@link ManagedObjectLocator} then it is up to that locator as
     * to whether or not this method will be invoked.
     * <p>
     * This method is called by the application during the configuration phase
     * allowing the application subclasses to register {@link ChannelInitialKeyResolutionTableProvider}s
     * <p>
     * The default implementation of this method adds a {@link ChannelQosProvider} that calls
     * {@link #getInitialChannelKeyResolutionTable(ToaService, ToaServiceChannel)} on this class.
     * 
     * @param containers Objects implementing {@link ChannelInitialKeyResolutionTableProvider} should be added to this set.
     */
    protected void addChannelInitialKeyResolutionTableProviders(Set<Object> containers) {
        containers.add(new ChannelInitialKeyResolutionTableProvider() {

            @Override
            public Properties getInitialChannelKeyResolutionTable(ToaService service, ToaServiceChannel channel) {
                return TopicOrientedApplication.this.getInitialChannelKeyResolutionTable(service, channel);
            }
        });
    }

    /**
     * This method may be overridden by subclasses to perform initial key resolution on a channel 
     * at the time it is configured which allows all or a portion of a channel's dynamic key parts
     * to be determined statically at configuration time by returning a initial Key Resolution Table
     * (KRT).
     * 
     * <p>
     * This method will not be called unless {@link #addChannelQosProviders(Set)} is
     * called on this class. This is done by the {@link DefaultManagedObjectLocator}, 
     * but may not be called if the application defines its own {@link ManagedObjectLocator}.
     * 
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
     * With the above KRT, the channel would be intialized with a key of
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
     * 
     * @param service The service name. 
     * @param channel The channel for which to perform key resolution
     * @return A key resolution table to substitute some or all of the configured channel key.
     */
    protected Properties getInitialChannelKeyResolutionTable(ToaService service, ToaServiceChannel channel) {
        return null;
    }

    /**
     * Subclasses may override this method to change the way service xmls are located.
     * <p>
     * The default implementation returns a new instance of {@link DefaultServiceDefinitionLocator}.
     * <p>
     * This is called prior to constructing the application's underlying {@link AepEngine} at this
     * point the {@link SrvAppLoader} will have been injected via {@link #onAppLoaderInjected(SrvAppLoader)}
     * and the {@link AepEngineDescriptor} will have been injected via {@link #onEngineDescriptorInjected(AepEngineDescriptor)}
     * so they may be used to figure out information about the application's identity. 
     *  
     * @return The {@link ServiceDefinitionLocator} to use to load the application's services. 
     * @see DefaultServiceDefinitionLocator
     */
    protected ServiceDefinitionLocator getServiceDefinitionLocator() {
        return new DefaultServiceDefinitionLocator();
    }

    /**
     * Subclasses may override this method to change the strategy for locating an application's
     * managed objects. 
     * <p>
     * The default implementation returns a new instance of {@link DefaultManagedObjectLocator}.
     * <p>
     * This is called prior to locating and parsing an application's service model and also prior to construction of the 
     * application's underlying {@link AepEngine} At this point the {@link SrvAppLoader} will have been injected via 
     * {@link #onAppLoaderInjected(SrvAppLoader)} and the {@link AepEngineDescriptor} will have been injected via 
     * {@link #onEngineDescriptorInjected(AepEngineDescriptor)} so they may be used to figure out information about 
     * the application's identity. 
     *  
     * @return The {@link ManagedObjectLocator} to use to load the application's services. 
     * @see DefaultManagedObjectLocator
     */
    protected ManagedObjectLocator getManagedObjectLocator() {
        return new DefaultManagedObjectLocator(this);
    }

    /**
     * Returns this application's {@link AepEngine}. The engine is injected 
     * just before the application is initialized, and this method will return
     * null before that occurs.
     * <p>
     * {@link TopicOrientedApplication} subclasses should use the processing facilities 
     * provided by this class for most operations, and only utilize the underlying
     * {@link AepEngine} for functionality not provided by TOA. In particular, 
     * all message sends should go through this application's {@link MessageSender} api
     * and message injection should be done through the {@link MessageInjector} unless
     * there is a compelling reason to bypass TOA.
     * 
     * @return This application's {@link AepEngine}
     */
    final public AepEngine getEngine() {
        return _engine;
    }

    /**
     * Return an interface which may be used to get the {@link AepEngine}
     * time. 
     * <p>
     * The default implementation of {@link EngineClock} returned simply
     * make a pass through call to the {@link AepEngine#getEngineTime()}
     * unless the engine for this application has not yet been set in 
     * which case the returned clock will just return {@link System#currentTimeMillis()}.
     * 
     * @return The {@link EngineClock}.
     */
    final public EngineClock getEngineClock() {
        return _engineClock;
    }

    /**
     * Returns this application's bootstrap configurer. 
     * <p>
     * For applications launched from the talon server {@link Main} class this will 
     * return the external {@link Configurer} for the application. For an embedded
     * Talon server constructed by user code this will return whatever object the application 
     * passed to the {@link SrvController#setBootstrapConfigurer(Object)}. 
     * <p>
     * The talon server injects the Configurer immediately after instantiating the 
     * TopicOrientedApplication. 
     * 
     * @return This application's bootstrap configurer. 
     */
    final public Configurer getConfigurer() {
        return configurer;
    }

    /**
     * Returns the application's parsed service definitions. 
     * <p>
     * An application's service definitions are parsed just after the application
     * calls {@link #addHandlerContainers(Set)}.
     * 
     * The returned collection must not be modified. 
     * 
     * @return The collection of parsed service models.  
     * @throws IllegalStateException if services have not yet been parsed.
     */
    final protected Collection<ToaService> getServiceModels() {
        if (!messagingConfigured) {
            throw new IllegalStateException("Services have not yet been parsed");
        }
        return services;
    }

    /**
     * Looks up a parsed service model by fully qualified name. 
     * <p>
     * An application's service definitions are parsed just after the application
     * calls {@link #addHandlerContainers(Set)}.
     * 
     * The returned ToaService must not be modified. 
     * 
     * @return The service model or <code>null</code> if no model was parsed.
     * @throws IllegalStateException if services have not yet been parsed.
     */
    final protected ToaService getServiceModel(String fullServiceName) {
        if (!messagingConfigured) {
            throw new IllegalStateException("Services have not yet been parsed");
        }
        for (ToaService service : services) {
            if (service.getName().equals(fullServiceName)) {
                return service;
            }
        }
        return null;
    }

    /**
     * Returns the {@link MessageSender} implementation which is this class.
     * <p>
     * This method is useful for dependency injection frameworks. 
     * <p>
     * Note that the 
     * thread safety of the returned implementation is the same as that of using
     * this class directly ... send calls are not thread safe and may only be
     * called from an {@link EventHandler}, or in a non concurrent fashion by
     * an unsolicited 'sender' thread if this is a purely producer application. 
     * 
     * @return This class. 
     */
    final public MessageSender getMessageSender() {
        return this;
    }

    /* (non-Javadoc)
     * @see com.neeve.toa.MessageSender#sendMessage(com.neeve.rog.IRogMessage)
     */
    @Override
    final public void sendMessage(final IRogMessage message) {
        sendMessage(message, null, null, null, null);
    }

    /* (non-Javadoc)
     * @see com.neeve.toa.MessageSender#sendMessage(com.neeve.rog.IRogMessage, java.lang.String)
     */
    @Override
    final public void sendMessage(final IRogMessage message, final String topic) {
        sendMessage(message, topic, null, null, null);
    }

    /* (non-Javadoc)
     * @see com.neeve.toa.MessageSender#sendMessage(com.neeve.rog.IRogMessage, java.util.Properties)
     */
    @Override
    final public void sendMessage(final IRogMessage message, final Properties keyResolutionTable) {
        sendMessage(message, null, keyResolutionTable, null, null);
    }

    /* (non-Javadoc)
     * @see com.neeve.toa.MessageSender#sendMessage(com.neeve.rog.IRogMessage, com.neeve.lang.XString)
     */
    @Override
    final public void sendMessage(final IRogMessage message, final XString topic) {
        sendMessage(message, null, null, topic, null);
    }

    /* (non-Javadoc)
     * @see com.neeve.toa.MessageSender#sendMessage(com.neeve.rog.IRogMessage, com.neeve.sma.MessageChannel.RawKeyResolutionTable)
     */
    @Override
    final public void sendMessage(final IRogMessage message, final RawKeyResolutionTable rawKeyResolutionTable) {
        sendMessage(message, null, null, null, rawKeyResolutionTable);
    }

    /**
     * Returns the {@link MessageSender} implementation which is this class.
     * <p>
     * This method is useful for dependency injection frameworks. 
     * <p>
     * Note that the 
     * thread safety of the returned implementation is the same as that of using
     * this class directly ... send calls are not thread safe and may only be
     * called from an {@link EventHandler}, or in a non concurrent fashion by
     * an unsolicited 'sender' thread if this is a purely producer application. 
     * 
     * @return This class. 
     */
    final public MessageInjector getMessageInjector() {
        return this;
    }

    /* (non-Javadoc)
     * @see com.neeve.toa.MessageInjector#injectMessage(com.neeve.rog.IRogMessage)
     */
    @Override
    final public void injectMessage(final IRogMessage message) {
        injectMessage(message, false, 0);
    }

    /* (non-Javadoc)
     * @see com.neeve.toa.MessageInjector#injectMessage(com.neeve.rog.IRogMessage, boolean)
     */
    @Override
    final public void injectMessage(final IRogMessage message, boolean nonBlocking) {
        injectMessage(message, nonBlocking, 0);
    }

    /* (non-Javadoc)
     * @see com.neeve.toa.MessageInjector#injectMessage(com.neeve.rog.IRogMessage, boolean)
     */
    @Override
    final public void injectMessage(IRogMessage message, boolean nonBlocking, final int delay) {
        if (_engine.getState() == State.Started && _engine.isPrimary()) {
            if (!_engine.isDispatchThread()) {

                if (message == null) {
                    throw new IllegalArgumentException("cannot inject a null message");
                }

                if (!_factoryRegisteredTypesById.containsKey(uniqueMessageId(message.getVfid(), message.getType()))) {
                    throw new ToaException("Can't inject '" + message.getClass().getName() + "' it was not registered with the application during initialization. This probably means that you don't have an @EventHandler for it in your application.");
                }

                if (delay == 0) {

                    try {
                        _engine.multiplexMessage(message, nonBlocking);
                    }
                    catch (IllegalStateException ise) {
                        //engine may have been stopped during multiplex...
                        _tracer.log("Injection of message canceled: " + ise.getMessage(), Tracer.Level.WARNING);
                    }
                }
                else {
                    final MessageEvent messageEvent = MessageEvent.create(null, null, message, null);
                    messageEvent.setDelay(delay);
                    try {
                        message.setTag(MessageViewTags.TAG_SMA_MESSAGE_EVENT, messageEvent);
                        _engine.getEventMultiplexer().scheduleEvent(messageEvent);
                    }
                    finally {
                        messageEvent.dispose();
                    }
                }
            }
            else {
                throw new UnsupportedOperationException("Injection of messages from an event handler thread is not currently supported.");
            }
        }
    }

    /**
     * This method is called by a Talon server to inject the application's
     * {@link AepEngineDescriptor}. 
     * <p>
     * <b>This method should not be called by subclasses or application code.</b>   
     * 
     * @param engineDescriptor The application's {@link AepEngineDescriptor}.
     */
    @AppInjectionPoint
    final private void setEngineConfiguration(final AepEngineDescriptor engineDescriptor) throws Exception {
        _engineDescriptor = engineDescriptor;
        _engineName = engineDescriptor.getName();
        onEngineDescriptorInjected(engineDescriptor);
        if (!_engineName.equals(engineDescriptor.getName())) {
            throw new IllegalStateException("Subclass changed engine name from '" + _engineName + "' to '" + engineDescriptor.getName() + "'!");
        }

        // check that AppHAPolicy annotation is present if configured for clustering.
        if (_engineDescriptor.getStore() != null) {
            if (getClass().getAnnotation(AppHAPolicy.class) == null) {
                _tracer.log(tracePrefix() + "Application must be annotated with an '" + AppHAPolicy.class.getCanonicalName() + "' to be configured for clustering.", Tracer.Level.SEVERE);
                throw new IllegalStateException("Application must be annotated with an '" + AppHAPolicy.class.getCanonicalName() + "' to be configured for clustering.");
            }
        }
        else {
            if (getClass().getAnnotation(AppHAPolicy.class) == null) {
                _tracer.log(tracePrefix() + "No '" + AppHAPolicy.class.getCanonicalName() + "' annotation detected on main application class '" + getClass().getCanonicalName() + "'. Application will not be clusterable.", Tracer.Level.WARNING);
            }
        }

        this.managedObjectLocator = getManagedObjectLocator();
        if (managedObjectLocator == null) {
            throw new IllegalStateException(getClass().getCanonicalName() + ".getManagedObjectLocator() returned null.");
        }
        managedObjectLocator.locateManagedObjects(managedObjects);

        if (managedObjects.contains(null)) {
            throw new IllegalStateException("Addition of null objects to the set of managed objects is not supported.");
        }
    }

    /**
     * Called when the {@link AepEngineDescriptor} is injected into the application. 
     * <p>
     * This is called prior to creating the engine allowing applications to further customize
     * the {@link AepEngine} prior to its construction. 
     * <p>
     * Because {@link TopicOrientedApplication} already provides an {@link AppInjectionPoint} for {@link AepEngineDescriptor},
     * this method provides subclasses with a means to listen for {@link AepEngineDescriptor} injection. 
     * <p>
     * 
     * @param engineDescriptor the AepEngineDescriptor
     * 
     * @throws Exception An application throwing an exception from this method will cause loading of the application to fail. 
     */
    protected void onEngineDescriptorInjected(final AepEngineDescriptor engineDescriptor) throws Exception {}

    /**
     * This method is called by a Talon server during application initialization 
     * to solicit the application's event handlers. 
     * <p>
     * <b>This method should not be called by subclasses or application code.</b>  
     * 
     * @param containers The set to which event handler containers should be added. 
     */
    @AppEventHandlerContainersAccessor
    final private void configure(Set<Object> containers) throws Exception {
        containers.add(this);
        containers.addAll(managedObjects);
        final LinkedHashSet<URL> services = new LinkedHashSet<URL>();
        getServiceDefinitionLocator().locateServices(services);
        configureMessaging(services, containers);
        onConfigured();
        containers.add(new FirstMessageValidator());
        traceConfig(Tracer.Level.CONFIG);
    }

    /**
     * Dumps config trace. 
     * 
     * @param level The level to dump at. 
     */
    private final void traceConfig(final Tracer.Level level) {
        try {
            if (_tracer.getLevel().val <= level.val) {
                StringBuffer buf = new StringBuffer();
                buf.append("\n=====================================\n");
                buf.append("Topic Oriented Application Configured\n");
                buf.append("Engine Descriptor:\n");
                buf.append(_engineDescriptor.toString());
                buf.append("\n");
                if (_engineDescriptor.getStore() != null) {
                    buf.append("Store Descriptor:\n");
                    final StoreDescriptor storeDescriptor = StoreDescriptor.load(_engineDescriptor.getStore());
                    buf.append(storeDescriptor.toString());
                    buf.append("\n");
                }
                buf.append("=====================================");
                _tracer.log(buf.toString(), Tracer.Level.CONFIG);
            }
        }
        catch (Exception e) {
            _tracer.log("Error tracing application configuration: " + UtlThrowable.prepareStackTrace(e), level);
        }
    }

    /**
     * This method is called by a Talon server during application initialization 
     * to solicit the application's command handlers. 
     * <p>
     * <b>This method should not be called by subclasses or application code.</b>  
     * 
     * @param containers The set to which command handler containers should be added. 
     */
    @AppCommandHandlerContainersAccessor
    final private void findCommandHandlers(final Set<Object> containers) throws Exception {
        containers.add(this);
        containers.addAll(managedObjects);
    }

    /**
     * This method is called by a Talon server during application initialization 
     * to solicit the application's user defined stat providers
     * <p>
     * <b>This method should not be called by subclasses or application code.</b>  
     * 
     * @param containers The set to which stat accessor containers should be added. 
     */
    @AppStatContainersAccessor
    final private void findStatAccessors(final Set<Object> containers) throws Exception {
        containers.add(this);
        containers.addAll(managedObjects);
    }

    /**
     * This method is called by a Talon server during application initialization 
     * to solicit the application's user defined beans that require Configuration.
     * <p>
     * <b>This method should not be called by subclasses or application code.</b>  
     * 
     * @param containers The set to which Configured containers should be added. 
     */
    @AppConfiguredAccessor
    final private void findConfiguredAccessor(final Set<Object> containers) throws Exception {
        containers.add(this);
        containers.addAll(managedObjects);
    }

    /**
     * Called after configuration of messaging and services has been successfully completed.
     * <p>
     * At this point the {@link AepEngine} is still not available, but {@link ToaService}s and the 
     * the {@link Configurer} are. 
     * <p>
     * Applications may override this method to examine either of these objects. 
     * 
     * @throws Exception An application throwing an exception from this method will cause loading of the application to fail.  
     */
    protected void onConfigured() throws Exception {}

    /**
     * This method is called by a Talon server during application initialization 
     * to inject the application's {@link AepEngine}.
     * <p>
     * <b>This method should not be called by subclasses or application code.</b>  
     * 
     * @param engine The application's {@link AepEngine}.
     * @throws Exception 
     */
    @AppInjectionPoint
    synchronized final private void setEngine(final AepEngine engine) throws Exception {
        _engine = engine;
        _haPolicy = engine.getHAPolicy();

        onEngineInjected(engine);

        predispatchMessageHandlerDispatcher.closeHandlerAddition();
        if (predispatchMessageHandlerDispatcher.handlerList.length > 0) {
            _engine.setPredispatchMessageHandler(predispatchMessageHandlerDispatcher);
        }

        postdispatchMessageHandlerDispatcher.closeHandlerAddition();
        if (postdispatchMessageHandlerDispatcher.handlerList.length > 0) {
            _engine.setPostdispatchMessageHandler(postdispatchMessageHandlerDispatcher);
        }

        if (_tracer.getLevel().val >= Level.CONFIG.val) {
            _tracer.log(tracePrefix() + " Engine Injected, descriptor" + engine.getDescriptor().toString(), Level.CONFIG);
        }
    }

    /**
     * Called when the {@link AepEngine} is injected into the application. 
     * <p>
     * This is called after the {@link AepEngine} has been created, but before it is started.
     * <p>
     * Because {@link TopicOrientedApplication} already provides an {@link AppInjectionPoint} for {@link AepEngine},
     * this method provides subclasses with a means to listen for {@link AepEngine} injection. 
     * <p>
     * 
     * @param engine the AepEngine
     *
     * @throws Exception An application throwing an exception from this method will cause loading of the application to fail. 
     */
    protected void onEngineInjected(final AepEngine engine) throws Exception {}

    /**
     * Adds a message handler to be invoked before 'normal' message handler dispatch. 
     * <p>
     * {@link TopicOrientedApplication} messages are dispatched by the underlying
     * {@link AepEngine} to @{@link EventHandler} annotated methods exposed by the
     * application. This method, allows an applications to add generic pre dispatch handlers
     * to do pre processing before message dispatch to the 'normal' handlers.
     * <p>
     * If multiple pre dispatch message handlers are registered and one of them throws an 
     * exception, remaining handlers will not be called and the engine will dispatch
     * an {@link AepApplicationExceptionEvent}.
     * <p>
     * Handler addition is not permitted after the call to {@link #onEngineInjected(AepEngine)}
     * and will thrown an {@link IllegalStateException}.
     * 
     * @throws IllegalStateException if this method is called after {@link #onEngineInjected(AepEngine)}.
     */
    public final void addPredispatchMessageHandler(final IAepPredispatchMessageHandler handler) {
        predispatchMessageHandlerDispatcher.addHandler(handler);
    }

    /**
     * Adds a message handler to be invoked after 'normal' message handler dispatch. 
     * <p>
     * {@link TopicOrientedApplication} messages are dispatched by the underlying
     * {@link AepEngine} to @{@link EventHandler} annotated methods exposed by the
     * application. This method, allows an applications to add generic post dispatch handlers
     * to do post processing after message dispatch to the 'normal' handlers.
     * <p>
     * If a message handler throws an exception prior to the {@link IAepPostdispatchMessageHandler} 
     * being invoked, then the {@link IAepPostdispatchMessageHandler} will not be invoked, and the
     * engine will instead dispatch an {@link AepApplicationExceptionEvent}, so 
     * an IAepPostdispatchMessageHandler may want to handle this event as well if it needs to 
     * be notified in the event of an unsuccessful dispatch.   
     * <p>
     * Handler addition is not permitted after the call to {@link #onEngineInjected(AepEngine)}
     * and will thrown an {@link IllegalStateException}.
     * 
     * @throws IllegalStateException if this method is called after {@link #onEngineInjected(AepEngine)}.
     */
    public final void addPostdispatchMessageHandler(final IAepPostdispatchMessageHandler handler) {
        postdispatchMessageHandlerDispatcher.addHandler(handler);
    }

    /*
     * Loader injection point (used to retrieve the application's Configurer)
     */
    @AppInjectionPoint
    synchronized final private void setAppLoader(final SrvAppLoader loader) throws Exception {
        this.configurer = (Configurer)SrvController.getInstance(loader.getServerDescriptor()).getBootstrapConfigurer();
        onAppLoaderInjected(loader);
    }

    /**
     * Called when the SrvAppLoader is injected into the application.
     * <p> 
     * This is called just after application class is constructed providing the means for an application
     * to examine the configuration of the server that is loading it. 
     * <p>
     * <p>
     * Because {@link TopicOrientedApplication} already provides an {@link AppInjectionPoint} for {@link SrvAppLoader},
     * this method provides subclasses with a means to listen for {@link SrvAppLoader} injection. 
     * <p>
     * 
     * @param loader the SrvAppLoader
     * 
     * @throws Exception An application throwing an exception from this method will cause loading of the application to fail. 
     */
    protected void onAppLoaderInjected(final SrvAppLoader loader) throws Exception {}

    @AppInitializer
    private final void appInitialized() throws Exception {
        onAppInitialized();
    }

    /**
     * This is called after the application's {@link AepEngine} has been successfully created
     * and injected into the application. Subclasses may override this method 
     * to do any final initialization prior to the application being started. 
     * 
     * @throws Exception An application throwing an exception from this method will cause loading of the application to fail. 
     */
    protected void onAppInitialized() throws Exception {}

    @EventHandler
    synchronized final private void onEngineStarted(final AepEngineStartedEvent event) {
        _role = _engine.getStore() != null ? _engine.getStore().getRole() : IStoreBinding.Role.Primary;
    }

    @EventHandler
    synchronized final private void onRoleChanged(final IStoreBindingRoleChangedEvent event) {
        _role = event.getRole();
    }

    /**
     * This method is called by the AepEngine when a channel is started. 
     * <p>
     * <b>This method should not be called by subclasses or application code.</b>  
     * 
     * @param event The {@link AepEngineActiveEvent}
     */
    @EventHandler
    final private void onChannelUp(final AepChannelUpEvent event) {
        final MessageChannel channel = event.getMessageChannel();
        final String busName = channel.getMessageBusBinding().getName();
        final String channelName = channel.getName();
        if (_tracer.debug) _tracer.log(tracePrefix() + "Channel '" + channelName + "' is up.", Tracer.Level.DEBUG);
        if (_channelMessageMapByBus.get(busName) != null) {
            Map<String, List<Long>> channelMap = _channelMessageMapByBus.get(busName);
            if (channelMap.get(channelName) != null) {
                if (_tracer.debug) _tracer.log(tracePrefix() + "...channel is in channel message map. adding channel to message send map for following ids...", Tracer.Level.DEBUG);
                for (Long id : channelMap.get(channelName)) {
                    MessageSendContext context = _messageChannelMap.get(id);
                    context.channel = channel;
                    if (_tracer.debug) _tracer.log(tracePrefix() + "......'" + context.messageType + "' [" + id + "].", Tracer.Level.DEBUG);
                }
            }
            else {
                if (_tracer.debug) _tracer.log(tracePrefix() + "...channel '" + channelName + "@" + channel.getMessageBusBinding().getName() + "' not in channel message map.", Tracer.Level.DEBUG);
            }
        }
        else {
            if (_tracer.debug) _tracer.log(tracePrefix() + "...channel bus '" + channelName + "@" + channel.getMessageBusBinding().getName() + "' not in channel message map.", Tracer.Level.DEBUG);
        }
    }

    @AppFinalizer
    private final void appFinalized() throws Exception {
        onAppFinalized();
    }

    /**
     * This is called after the application's {@link AepEngine} has been successfully created
     * and injected into the application. Subclasses may override this method 
     * to do any final initialization prior to the application being started. 
     * 
     * @throws Exception An application throwing an exception from this method will cause the app finalization to terminate with an error.
     */
    protected void onAppFinalized() throws Exception {}

    /**
     * Trace all alerts. 
     * @param alert The alert.
     */
    @EventHandler
    private final void onApplicationAlert(IAlertEvent alert) {
        if (_tracer.getLevel().val > alertTraceLevel.val) {
            return;
        }

        if (alert instanceof AepBusBindingOpenFailedEvent) {
            if (_engineDescriptor.getMessagingStartFailPolicy() == MessagingStartFailPolicy.NeverFail) {
                return;
            }
        }
        MessageView backing = alert.getBackingMessage();
        _tracer.log(tracePrefix() + "ALERT: " + alert.toString() + (backing != null ? ": " + backing.toString() : ""), alertTraceLevel);
    }

    private final String tracePrefix() {
        if (_engineName != null) {
            return "<nv.toa> [" + _engineName + "] ";
        }
        else {
            return "<nv.toa> ";
        }
    }

}
