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
package com.neeve.toa.opt;

import com.neeve.toa.TopicOrientedApplication;

/**
 * Provides the ability to delay acknowledgments for inbound messages for applications
 * that do additional processing outside of a the messages handler.  
 * <p>
 * To facilitate this functionality a {@link DelayedAcknowledgmentController} provides a 
 * {@link #delayAcknowledgment()} method. When this method is called from within a message handler, it
 * returns a {@link DelayedAcknowledger DelayedAcknowledger}. Acknowledgment of the AEP transaction in which the 
 * message was dispatched to the application is suspended until the the application calls 
 * {@link DelayedAcknowledger#acknowledge()}.
 * <p>
 * A {@link TopicOrientedApplication} only creates a {@link DelayedAcknowledgmentController} when
 * the configuration property {@link TopicOrientedApplication#PROP_ENABLED_DELAYED_ACK_CONTROLLER nv.toa.enabledelayedackcontroller} is
 * set to <code>true</code>. When enabled, an additional executor bus is added to the application. Under
 * the covers delayed acknowledgment is done by sending a DelayedAckMessage over the executor bus 
 * and attaching the returned {@link DelayedAcknowledger DelayedAcknowledger} to the message. The inbound message is 
 * acknowledged when both the application calls {@link DelayedAcknowledger#acknowledge() DelayedAcknowledger.acknowledged()}
 * and the underlying executor bus processes the DelayedAckMessage. In this way acks are delayed in an ordered fashion
 * with respect to other messsages in the transacton flow -- namely on outbound send stability. 
 * <p>
 * Delayed acknowledgments are only supported on engines that are not configured with a 
 * store. This restriction exists because the processing done by a non message handler thread
 * can't be reliably performed from an HA standpoint on a backup instance or during recovery. 
 * Applications needing to do HA reliable work in a thread other the engine thread should use
 * an executor bus which provides asynchrononus acknowledgment capabilities <b>and</b> and the 
 * ability to resume such work across failover and recovery. 
 * <p>
 * <i>The {@link DelayedAcknowledgmentController} is classified as an experimental feature provided
 * mainly as a convenience to developers doing stateless work. Applications that need more flexibility 
 * or have HA needs are encouraged to use an executor bus directly.</i>
 * <p>
 * 
 * </p>
 * <h2>Example Code</h2>
 * <h3>Delaying Acknowledgment</h3>
 * <pre>
 * &#64;AppHAPolicy(HAPolicy.EventSourcing)
 * public class MyApp extends TopicOrientedApplication {
 *   ExecutorService executor = Executors.newSingleThreadExecutor();
 *  
 *   &#64;EventHandler
 *   public void onMessage(MyMessage message) {
 *     final DelayedAcknowledger delayedAck = getDelayedAcknowledgmentController().delayAcknowledgment();
 *     
 *     //Do some potentially blocking work in a background thread:
 *     executor.execute(new Runnable() {
 *       public void run() {
 *          EmailAlertUtil.sendEmail("I got a message!");
 *          delayedAck.acknowledge();
 *       }
 *     });
 *   }
 * }
 * </pre>
 * <h3>Using the inbound message in a separate thread</h3>
 * Per the AepEngine contract, you should copy or acquire the message if you
 * plan to use it outside of a handler. The example below shows such a scenario:
 * <pre>
 * &#64;AppHAPolicy(HAPolicy.EventSourcing)
 * public class MyApp extends TopicOrientedApplication {
 * 
 *   ExecutorService executor = Executors.newSingleThreadExecutor();
 *  
 *   &#64;EventHandler
 *   public void onMessage(MyMessage message) {
 *     final DelayedAcknowledger delayedAck = getDelayedAcknowledgmentController().delayAcknowledgment();
 *     
 *     final MyMessage messageCopy = message.copy();
 *     
 *     //Schedule some work to do on a copy of the message
 *     //in a background thread:
 *     executor.execute(new Runnable() {
 *       public void run() {
 *          byte [] messageCopy.serializeToByteArray()
 *          //TODO do something with the message
 *          messageCopy.dispose();
 *          
 *          //If the application fails before above processing
 *          //is complete the message will not have been
 *          //acknowledged and can be reprocessed on application
 *          //restart.
 *          delayedAck.acknowledge();
 *       }
 *     });
 *   }
 * }
 * </pre>
 */
public interface DelayedAcknowledgmentController {

    /**
     * A delayed acknowledger handle. 
     * <p>
     * An application may create a delayed acknowldeger via {@link DelayedAcknowledgmentController#delayAcknowledgment()}. Doing
     * so suspends acknowledgment of the inbound message being processed until {@link #acknowledge()} is called in which case 
     * the inbound messages acknowledgment is resumed. 
     */
    public static interface DelayedAcknowledger {

        /**
         * Called to successfully release a delayed acknowledgemnt.
         * <p>
         * When called this method allows the underlying transaction to be completed which ultimately
         * results in that transaction's inbound events / messages being acknowledged upstream. A failure
         * to acknowledge the message will ultimately result in the AepEngine's transaction pipeline stalling
         * so it is vital that this method is ultimately called.  
         * <p>
         * This method may be called by any thread but may only be called once. DelayedAcknowledger
         * are pooled, and the act of calling acknowledge returns the acknowledger to its pool. An
         * application must not call this method more than once and should release its reference
         * to this object after calling acknowledge.  
         * <p>
         * This method is equivalent to calling {@link DelayedAcknowledger#acknowledge(Exception) acknowledge(null)}.
         */
        public void acknowledge();

        /**
         * Called to acknowledge processing done by an ExecutorBusProcessor. 
         * 
         * Calling this method with a non null exception indicates that processing did not complete successfully. 
         * This may result in the execution bus shutting down if it is so configured. 
         * <p>
         * This method may be called by any thread but may only be called once. The executor bus will pool 
         * Acknowledger instances and the act of calling acknowledge returns the acknowledger to its pool. 
         */
        public void acknowledge(Exception status);
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
    public DelayedAcknowledger delayAcknowledgment();
}
