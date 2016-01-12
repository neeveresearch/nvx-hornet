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
package com.neeve.toa.test.unit;

import static org.junit.Assert.assertEquals;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.neeve.aep.AepEngine;
import com.neeve.rog.IRogMessage;
import com.neeve.server.app.SrvAppLoader;
import com.neeve.toa.TopicOrientedApplication;
import com.neeve.toa.spi.AbstractServiceDefinitionLocator;
import com.neeve.toa.spi.ServiceDefinitionLocator;
import com.neeve.util.UtlObjectGraph;

/**
 * Base class for toa test apps.
 */
public class AbstractToaTestApp extends TopicOrientedApplication {

    volatile boolean holdMessages = true;
    volatile int receivedMessageCount = 0;
    volatile List<IRogMessage> received = new ArrayList<IRogMessage>();

    volatile int sentMessageCount = 0;
    volatile List<IRogMessage> sent = new ArrayList<IRogMessage>();

    private SrvAppLoader appLoader;

    private class ToaTestServiceDefinitionLocator extends AbstractServiceDefinitionLocator {

        /* (non-Javadoc)
         * @see com.neeve.toa.spi.ServiceDefinitionLocator#locateServices(java.util.Set)
         */
        @Override
        public void locateServices(Set<URL> urls) throws Exception {
            urls.add(getClass().getResource("/services/forwarderService.xml"));
            urls.add(getClass().getResource("/services/receiverService.xml"));
        }

    }

    @Override
    public ServiceDefinitionLocator getServiceDefinitionLocator() {
        return new ToaTestServiceDefinitionLocator();
    }

    @Override
    protected void onAppLoaderInjected(SrvAppLoader appLoader) {
        this.appLoader = appLoader;
    }

    public SrvAppLoader getAppLoader() {
        return appLoader;
    }

    /**
     * A public accessor to get the AepEngine.
     * 
     * @return This app's AepEngine.
     */
    public AepEngine getAepEngine() {
        return super.getEngine();
    }

    /**
     * Should be called by subclasses on each message receipt. 
     * 
     * @param message the received message.
     */
    protected IRogMessage recordReceipt(IRogMessage message) {
        if (holdMessages) {
            message.acquire();
            received.add(message);
        }
        receivedMessageCount++;
        return message;
    }

    protected IRogMessage recordSend(IRogMessage message) {
        if (holdMessages) {
            message.acquire();
            sent.add(message);
        }
        sentMessageCount++;
        return message;
    }

    public IRogMessage populateMessage(IRogMessage message) {
        try {
            UtlObjectGraph.populateObject(message, IForwarderMessage1.class, IForwarderMessage2.class, IReceiverMessage1.class, IReceiverMessage2.class);
        }
        catch (Exception e) {
            throw new RuntimeException("Error populating message", e);
        }
        return message;

    }

    /**
     * Instructs the application to hold onto received messages. This 
     * assumes that subclasses call {@link #recordReceipt(IRogMessage)} for each 
     * received message.
     * 
     * @param value True to hold on to messages. 
     */
    public void setHoldMessages(boolean value) {
        holdMessages = value;
    }

    /**
     * Waits the given number of seconds for transaction to stabilize. 
     * 
     * @param seconds The number of seconds to wait
     * @param minTransactions The number of transactions to wait for.
     * @return True if at least minTransactions have been received.
     * @throws InterruptedException If the thread is interrupted waiting. 
     */
    public boolean waitForTransactionStability(int seconds, long minTransactions) throws InterruptedException {
        boolean waiting = true;
        long timeout = System.currentTimeMillis() + seconds * 1000;
        while ((waiting = (getEngine().getStats().getNumCommitsCompleted() < minTransactions)) && System.currentTimeMillis() < timeout) {
            Thread.sleep(100);
        }
        return !waiting;
    }

    /**
     * Waits for started transactions to stabilize:
     * 
     * @param seconds The number of seconds to wait
     * @return True if at least minTransactions have been received.
     * @throws InterruptedException If the thread is interrupted waiting. 
     */
    public boolean waitForTransactionStability(int seconds) throws InterruptedException {
        return waitForTransactionStability(seconds, getEngine().getStats().getNumCommitsStarted());
    }

    /**
     * Waits the given number of seconds for messages to be sent. 
     * 
     * @param seconds The number of seconds to wait
     * @param minMessages The number of messages to wait for.
     * @return True if at least minMessages have been received.
     * @throws InterruptedException If the thread is interrupted waiting. 
     */
    public boolean waitForSends(int seconds, int minMessages) throws InterruptedException {
        boolean waiting = true;
        long timeout = System.currentTimeMillis() + seconds * 1000;
        while ((waiting = (sentMessageCount < minMessages)) && System.currentTimeMillis() < timeout) {
            Thread.sleep(100);
        }
        return !waiting;
    }

    public void assertExpectedSends(int seconds, int expectedSends) throws InterruptedException {
        waitForSends(seconds, expectedSends);
        assertEquals(getAepEngine().getName() + " has unexpected number of sent messages", expectedSends, sentMessageCount);
    }

    /**
     * Waits the given number of seconds for messages to be received. 
     * 
     * @param seconds The number of seconds to wait
     * @param minMessages The number of messages to wait for.
     * @return True if at least minMessages have been received.
     * @throws InterruptedException If the thread is interrupted waiting. 
     */
    public boolean waitForMessages(int seconds, int minMessages) throws InterruptedException {
        boolean waiting = true;
        long timeout = System.currentTimeMillis() + seconds * 1000;
        while ((waiting = (receivedMessageCount < minMessages)) && System.currentTimeMillis() < timeout) {
            Thread.sleep(100);
        }
        return !waiting;
    }

    public void assertExpectedReceipt(int seconds, int expectedReceipt) throws InterruptedException {
        waitForMessages(seconds, expectedReceipt);
        assertEquals(getAepEngine().getName() + " has unexpected number of received messages", expectedReceipt, receivedMessageCount);
    }

    /**
     * Clears received messages if they are being held and 
     * resets the receivedCount.
     */
    public void clearReceivedMessages() {
        if (holdMessages) {
            for (IRogMessage message : received) {
                message.dispose();
            }
            received.clear();
        }
        receivedMessageCount = 0;
    }

    /**
     * Called at the end of a testcase to perform cleanup
     */
    public void cleanup() {
        clearReceivedMessages();
    }
}
