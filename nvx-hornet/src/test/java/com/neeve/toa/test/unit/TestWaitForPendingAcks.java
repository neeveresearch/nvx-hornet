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

import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.neeve.aep.annotations.EventHandler;
import com.neeve.sma.MessageChannel.Qos;
import com.neeve.sma.impl.loopback.LoopbackBus;
import com.neeve.toa.service.ToaService;
import com.neeve.toa.service.ToaServiceChannel;

/**
 * Tests that LoobackBus.waitForPening acks, waits for pending acks in TOA.
 */
public class TestWaitForPendingAcks extends AbstractToaTest {

    public static class RequestorApp extends AbstractToaTestApp {
        volatile int messageCount = 0;

        @EventHandler
        public void onReply(ReceiverMessage1 message) {
            System.out.println("Received reply");
            messageCount++;
        }

        @Override
        protected Qos getChannelQos(ToaService service, ToaServiceChannel channel) {
            return Qos.Guaranteed;
        }

    }

    public static class ReplierApp extends AbstractToaTestApp {

        @EventHandler
        public void onRequest(ForwarderMessage1 message) {
            System.out.println("Received request");
            ReceiverMessage1 outbound = ReceiverMessage1.create();
            outbound.setIntField(1);
            sendMessage(outbound);
        }

        @Override
        protected Qos getChannelQos(ToaService service, ToaServiceChannel channel) {
            return Qos.Guaranteed;
        }
    }

    @Test
    public void testRequestReplyWaitForPendingAcks() throws Throwable
    {
        RequestorApp requestor = createApp("Requestor", "standalone", RequestorApp.class);
        createApp("Replier", "standalone", ReplierApp.class);

        final int iterations = 10;

        for (int i = 0; i < iterations; i++) {
            //Create message and sent it. 
            ForwarderMessage1 message = ForwarderMessage1.create();
            message.setIntField(1);
            requestor.sendMessage(message);
            //Wait for pending acks and assert that the reply has been received: 
            LoopbackBus.getInstance(".").waitForPendingAcks(10, TimeUnit.SECONDS);
            assertEquals("Wrong number of replies received", i + 1, requestor.messageCount);
        }

    }
}
