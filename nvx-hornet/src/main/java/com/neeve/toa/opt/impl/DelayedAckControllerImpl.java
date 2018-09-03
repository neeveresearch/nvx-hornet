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
package com.neeve.toa.opt.impl;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.neeve.aep.AepEngine;
import com.neeve.aep.AepEngineDescriptor.ChannelConfig;
import com.neeve.aep.AepEngineDescriptor;
import com.neeve.aep.annotations.EventHandler;
import com.neeve.aep.event.AepChannelDownEvent;
import com.neeve.aep.event.AepChannelUpEvent;
import com.neeve.rog.IRogMessage;
import com.neeve.server.app.annotations.AppStat;
import com.neeve.sma.MessageBusDescriptor;
import com.neeve.sma.MessageChannel;
import com.neeve.sma.MessageChannelDescriptor;
import com.neeve.sma.MessageView;
import com.neeve.sma.SmaException;
import com.neeve.sma.MessageChannel.Qos;
import com.neeve.sma.spi.executor.ExecutorBusConstants;
import com.neeve.sma.spi.executor.ExecutorBusProcessor;
import com.neeve.sma.spi.executor.ExecutorBusProcessor.Acknowledger;
import com.neeve.toa.messages.HornetMessageFactory;
import com.neeve.toa.opt.DelayedAcknowledgmentController;
import com.neeve.toa.opt.DelayedAcknowledgmentController.DelayedAcknowledger;
import com.neeve.trace.Tracer;
import com.neeve.trace.Tracer.Level;
import com.neeve.util.UtlPool;
import com.neeve.util.UtlPool.Factory;
import com.neeve.util.UtlReferenceTracker;

/**
 * Delayed AcknowledgmentController implementation.
 */
public class DelayedAckControllerImpl implements DelayedAcknowledgmentController {
    private static final Tracer tracer = Tracer.create("nv.toa.delayedack", Level.INFO);

    private static enum State {
        /**
         * Created, but not started. 
         */
        Init,
        /**
         * Started and ready for delay acknowledgment creation. 
         */
        Started,
        /**
         * Closed. 
         */
        Closed;
    }

    /**
     * This DelayedAcknowledger implementation waits for both the executor bus acknowledgment
     * and the callers acknowledgment before dispatching the executor bus acknowledgment. 
     * <p>
     * The ordered processing of the delayed-acknowledger bus executions orders 
     * acknowledgments both within a given transaction in the case of multiple delayed 
     * acks and across transactions so that AEP transactions are committed in order. In this
     * way a delayed ack call from transaction T2 completing before a delayed ack in transaction
     * T1 will still be still result in T1 being acknowledged first. 
     */
    private class DelayedAcknowledgerImpl implements UtlPool.Item<DelayedAcknowledgerImpl>, DelayedAcknowledger {
        final private UtlReferenceTracker refTracker;

        private UtlPool<DelayedAcknowledgerImpl> pool;
        private volatile Exception status;
        private volatile Acknowledger busAcknowledger;
        private AtomicInteger ackCountDown = new AtomicInteger(0);

        DelayedAcknowledgerImpl() {
            refTracker = UtlReferenceTracker.enabled(this.getClass()) ? new UtlReferenceTracker(this) : null;
            init();
        }

        /* (non-Javadoc)
         * @see com.neeve.util.UtlPool.Item#init()
         */
        @Override
        public DelayedAcknowledgerImpl init() {
            int count = ackCountDown.getAndSet(0);
            if (count != 0) {
                if (UtlReferenceTracker.TYPE_TRACKING_ENABLED && refTracker != null) {
                    refTracker.onInit(count);
                }
                throw new IllegalStateException("DelayedAcknowledger initialized with non 0 ack count (" + count + ")");
            }
            status = null;
            busAcknowledger = null;
            return this;
        }

        public DelayedAcknowledgerImpl initAckCount() {
            if (!ackCountDown.compareAndSet(0, 2)) {
                if (UtlReferenceTracker.TYPE_TRACKING_ENABLED && refTracker != null) {
                    refTracker.dump();
                }
                throw new IllegalStateException("Attempt to initialized DelayedAcknowledger with non zero ack count " + ackCountDown.get());
            }
            return this;
        }

        /* (non-Javadoc)
         * @see com.neeve.util.UtlPool.Item#setPool(com.neeve.util.UtlPool)
         */
        @Override
        public final DelayedAcknowledgerImpl setPool(UtlPool<DelayedAcknowledgerImpl> pool) {
            this.pool = pool;
            return this;
        }

        /* (non-Javadoc)
         * @see com.neeve.util.UtlPool.Item#getPool()
         */
        @Override
        public final UtlPool<DelayedAcknowledgerImpl> getPool() {
            return pool;
        }

        /**
         * Sets the executor bus acknowledger. 
         * 
         * @param acknowledger The {@link Acknowledger ExecutorBusProcessor.Acknowledger}.
         */
        final void setBusAcknowledger(final ExecutorBusProcessor.Acknowledger acknowledger) {
            if (tracer.debug) tracer.log("Delayed acknowledgment bus processing completed for " + engineDescriptor.getName(), Tracer.Level.DEBUG);
            this.busAcknowledger = acknowledger;
            acknowledge(null);
        }

        /* (non-Javadoc)
         * @see com.neeve.toa.opt.DelayedAcknowledger.Acknowledger#acknowledge()
         */
        @Override
        public final void acknowledge() {
            acknowledge(null);
        }

        /* (non-Javadoc)
         * @see com.neeve.toa.opt.DelayedAcknowledger.Acknowledger#acknowledge()
         */
        @Override
        public final void acknowledge(final Exception status) {
            if (status != null && this.status == null) {
                this.status = status;
            }
            int count = ackCountDown.decrementAndGet();
            if (UtlReferenceTracker.TYPE_TRACKING_ENABLED && refTracker != null) {
                refTracker.onDispose(count);
            }
            if (count < 0) {
                throw new IllegalStateException("Attempt to acknowledge an already acknowledged delayed acknowledgment!");
            }
            else if (count == 0) {
                if (tracer.debug) tracer.log("Releasing delayed acknowledgment for " + engineDescriptor.getName(), Tracer.Level.DEBUG);

                // note that busAcknowledger has to have been set for count to have dropped to 0:
                busAcknowledger.acknowledge(status);
                delayedAcksPendingCount.decrementAndGet();
                if (state != State.Closed) {
                    pool.put(this);
                }
            }

        }
    }

    /**
     * The {@link ExecutorBusProcessor} implementation for the delayed acknowledger. 
     * <p>
     * As delayed acks are dispatched through the executor bus, the executor bus' acknowledger
     * is tagged onto the {@link DelayedAcknowledger}. When both the executor bus processing and
     * the application's delayed acknowledgment is called the executor bus' acknowledger is called
     * which results in the transactions being completed. 
     */
    static final class DelayedAckProcessor implements ExecutorBusProcessor {

        /* (non-Javadoc)
         * @see com.neeve.sma.spi.executor.ExecutorBusProcessor#process(com.neeve.sma.MessageView, com.neeve.sma.spi.executor.ExecutorBusProcessor.Acknowledger, int)
         */
        @Override
        public void process(MessageView view, ExecutorBusProcessor.Acknowledger acknowledger, int flags) throws Exception {
            if (view instanceof IRogMessage) {
                if (((IRogMessage)view).getAttachment() instanceof DelayedAcknowledgerImpl) {
                    DelayedAcknowledgerImpl delayedAck = (DelayedAcknowledgerImpl)((IRogMessage)view).getAttachment();
                    delayedAck.setBusAcknowledger(acknowledger);
                }
            }
        }
    }

    /**
     * Factory for delayed acknowledgers.  
     */
    private final Factory<DelayedAcknowledgerImpl> FACTORY = new Factory<DelayedAcknowledgerImpl>() {

        @Override
        public DelayedAcknowledgerImpl createItem(Object object) {
            return new DelayedAcknowledgerImpl();
        }

        @Override
        public DelayedAcknowledgerImpl[] createItemArray(int size) {
            return new DelayedAcknowledgerImpl[size];
        }
    };

    private static final String delayedAckExecutorChannelName = "delayed-ack";

    private volatile State state = State.Init;

    private volatile AepEngineDescriptor engineDescriptor;
    private volatile AepEngine engine;
    private String delayedAckExecutorBusName;
    private UtlPool<DelayedAcknowledgerImpl> delayedAcknowledgePool;

    private volatile MessageChannel delayedAckChannel;
    private AtomicLong delayedAcksPendingCount = new AtomicLong(0);

    public void initEngineDescriptor(AepEngineDescriptor engineDescriptor) throws SmaException {
        if (tracer.isEnabled(Level.CONFIG)) tracer.log("Initializing delayed acknowledgment controller for " + engineDescriptor.getName(), Tracer.Level.CONFIG);
        if (engineDescriptor.getStore() != null) {
            throw new UnsupportedOperationException("Delayed acknowledgment is not supported for engines configured with a store");
        }

        this.engineDescriptor = engineDescriptor;

        delayedAckExecutorBusName = "hornet-delayed-executor-" + engineDescriptor.getName();
        // create delayed acknowledger executor bus. 
        String delayedAckExecutorBusName = "hornet-delayed-executor-" + engineDescriptor.getName();
        MessageBusDescriptor delayedAckBusDescriptor = MessageBusDescriptor.create(delayedAckExecutorBusName);
        delayedAckBusDescriptor.setProviderConfig("executor://" + delayedAckExecutorBusName);
        delayedAckBusDescriptor.setProviderConfigProperty(ExecutorBusConstants.PROPNAME_PROCESSOR_PROVIDER_CLASSNAME, DelayedAckControllerProcessorFactory.class.getName());

        MessageChannelDescriptor delayedAckChannelDesecriptor = MessageChannelDescriptor.create(delayedAckExecutorChannelName, delayedAckBusDescriptor);
        delayedAckChannelDesecriptor.setChannelQos(Qos.Guaranteed);
        delayedAckBusDescriptor.addChannel(delayedAckChannelDesecriptor);
        delayedAckBusDescriptor.save();

        engineDescriptor.addBus(delayedAckExecutorBusName);
        engineDescriptor.setBusManagerProperty(delayedAckExecutorBusName, "detachedCommit", "false");
        engineDescriptor.addChannel(delayedAckExecutorBusName, delayedAckExecutorChannelName, ChannelConfig.from("join=false"));

        // create the delayed acknowler pool
        delayedAcknowledgePool = UtlPool.create("hornet-delayed-ack", engineDescriptor.getName(), FACTORY, UtlPool.Params.create().setThreaded(true));
    }

    public void initEngine(AepEngine engine) {
        this.engine = engine;
        this.engine.registerFactory(HornetMessageFactory.create(null));
        this.state = State.Started;
    }

    @EventHandler
    public void onChannelUp(AepChannelUpEvent event) {
        if (delayedAckExecutorChannelName.equals(event.getMessageChannel().getName()) &&
                event.getMessageBusBinding().getName().equals(delayedAckExecutorBusName)) {
            delayedAckChannel = event.getMessageChannel();
        }
    }

    @EventHandler
    public void onChannelDown(AepChannelDownEvent event) {
        if (delayedAckExecutorChannelName.equals(event.getMessageChannel().getName()) &&
                event.getMessageBusBinding().getName().equals(delayedAckExecutorBusName)) {
            delayedAckChannel = null;
        }
    }

    public void close() {
        if (engineDescriptor != null) {
            if (tracer.isEnabled(Level.CONFIG)) tracer.log("Closing delayed acknowledgment controller for " + engineDescriptor.getName(), Tracer.Level.INFO);
            this.engine = null;
            this.engineDescriptor = null;
            this.delayedAcknowledgePool.close();
            state = State.Closed;
        }
    }

    @AppStat(name = "hornet.delayedAcksPending")
    public long getDelayedAcksPending() {
        return delayedAcksPendingCount.get();
    }

    /**
     * Creates a delayed acknowledger. 
     * <p>
     * When called from message handler this method delays completion of the AEP transaction 
     * in which the message is being processed until the returned {@link DelayedAcknowledger}'s
     * {@link DelayedAcknowledger#acknowledge() acknowledge() method is called}.
     * 
     * @return A {@link DelayedAcknowledger}.
     * @throws IllegalStateException If called from outside of a message handler thread. 
     * @throws UnsupportedOperationException If called from an engine that is configured in a manner in which 
     *  delayed acknowledgments are not supported.
     */
    @Override
    public final DelayedAcknowledger delayAcknowledgment() {
        if (state != State.Started) {
            throw new IllegalStateException("Delayed acknowledgment controller is not Started (" + state + ")");
        }

        if (engine.getStore() != null) {
            throw new UnsupportedOperationException("Delayed acknowledgment is not supported for engines configured with a store");
        }

        if (!engine.isMessageDispatchThread()) {
            throw new IllegalStateException("createDelayedAck cannot be called from outside of a message handler!");
        }

        if (engine.isPrimary() && engine.getState() == AepEngine.State.Started) {
            MessageChannel delayedAckChannel = this.delayedAckChannel;
            if (delayedAckChannel == null) {
                throw new IllegalStateException("delayedAckChannel is not up!");
            }

            DelayedAcknowledger ack = delayedAcknowledgePool.get(null).initAckCount();
            IRogMessage message = HornetMessageFactory.createDelayedAckMessage();
            message.setAttachment(ack);
            message.setMessageChannelAsRaw(delayedAckChannel.getNameAsRaw());
            message.setMessageBusAsRaw(delayedAckChannel.getNameAsRaw());
            engine.sendMessage(delayedAckChannel, message);
            delayedAcksPendingCount.incrementAndGet();
            if (tracer.debug) tracer.log("Created delayed acknowledger for " + engine.getName(), Tracer.Level.DEBUG);
            return ack;
        }
        else {
            throw new IllegalStateException("createDelayedAck can only be called from a primary engine in which messaging is started!");
        }
    }
}
