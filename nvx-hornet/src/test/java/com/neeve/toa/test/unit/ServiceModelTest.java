/**
 * Copyright 2022 N5 Technologies, Inc
 *
 * This product includes software developed at N5 Technologies, Inc
 * (http://www.n5corp.com/) as well as software licenced to N5 Technologies,
 * Inc under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding
 * copyright ownership.
 *
 * N5 Technologies licenses this file to you under the Apache License,
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import com.neeve.toa.DefaultServiceDefinitionLocator;
import com.neeve.toa.service.ToaService;
import com.neeve.toa.service.ToaServiceChannel;
import com.neeve.toa.service.ToaServiceToRole;
import com.neeve.toa.test.unit.modelA.AmbiguouslyNamedMessage;
import com.neeve.toa.test.unit.modelA.ModelAMessage1;
import com.neeve.toa.test.unit.modelB.ModelBMessage1;

public class ServiceModelTest extends AbstractToaTest {
    @Test
    public void testConflictingMessageIdFails() throws Exception {
        try {
            ToaService.unmarshal(getClass().getResource("/conflictingMessageFactoryIdTestService.xml"));
            fail("Should have failed with factory id collision");
        }
        catch (Exception e) {
            if (e.getMessage().indexOf("Factory id collision detected") == -1) {
                e.printStackTrace();
                fail("Expected to find 'Factory id collision detected' in exception text, but got: " + e.getMessage());
            }
        }
    }

    @Test
    public void testAmbiguousMessageNameFails() throws Exception {
        try {
            ToaService.unmarshal(getClass().getResource("/ambiguousMessageNameTestService.xml"));
            fail("Should have failed with Ambiguous model for message");
        }
        catch (Exception e) {
            if (e.getMessage().indexOf("Ambiguous model for message") == -1) {
                e.printStackTrace();
                fail("Expected to find ' Ambiguous model for message' in exception text, but got: " + e.getMessage());
            }
        }
    }

    @Test
    public void testQualifiedAmbiguousMessageNamePasses() throws Exception {
        ToaService service = ToaService.unmarshal(getClass().getResource("/unambiguousMessageNameTestService.xml"));

        List<Class<?>> expectedClasses = Arrays.asList(new Class<?>[] {
                                                                       com.neeve.toa.test.unit.modelA.AmbiguouslyNamedMessage.class,
                                                                       com.neeve.toa.test.unit.modelB.AmbiguouslyNamedMessage.class
        });

        ToaServiceToRole role = service.getToRole("TestRole");
        assertNotNull("Expected to find 'TestRole' role", role);
        assertTrue("Collection should contain " + AmbiguouslyNamedMessage.class.getCanonicalName(), role.getMessageClasses().containsAll(expectedClasses));
    }

    @Test
    public void testDefaultChannel() throws Exception {
        ToaService service = ToaService.unmarshal(getClass().getResource("/defaultChannelTestService.xml"));

        ToaServiceToRole role = service.getToRole("ServiceA");
        assertNotNull("Expected to find 'ServiceA' role", role);
        ToaServiceChannel channel = role.getChannel(ModelAMessage1.class.getName());
        assertEquals("Message should be on default channel", "DefaultChannel", channel.getSimpleName());
        assertEquals("Message should be on default channel", "defaultchanneltest-DefaultChannel", channel.getName());
        String expectedChannelName = service.getSimpleName().toLowerCase() + "-" + channel.getSimpleName();
        assertEquals("Channel name should be prefix with service name", expectedChannelName, channel.getName());
    }

    @Test
    public void testNoServicePrefix() throws Exception {
        ToaService service = ToaService.unmarshal(getClass().getResource("/noPrefixChannelTestService.xml"));

        ToaServiceToRole role = service.getToRole("ServiceB");
        assertNotNull("Expected to find 'ServiceB' role", role);
        ToaServiceChannel channel = role.getChannel(ModelBMessage1.class.getName());
        assertEquals("Channel Name and Simple Channel name should be identical", channel.getSimpleName(), channel.getName());
    }

    @Test
    public void testServiceWithSpaceInNameFailsToValidate() throws Exception {
        URL url = getClass().getResource("/serviceWithSpaceInNameTestService.xml");
        try {
            DefaultServiceDefinitionLocator.validateServiceDefinitionFile(url);
            fail("Shouldn't haven been able to validate service with spaces in name");
        }
        catch (Exception e) {
            assertTrue("Excepted error about NCName but was '" + e.getMessage() + "'", e.getMessage().indexOf("NCName") >= 0);
        }

    }

    @Test
    public void testReceiveOnlyChannelRejectsSendMapping() throws Exception {
        try {
            ToaService.unmarshal(getClass().getResource("/receiveOnlySendTestService.xml"));
            fail("Should have failed because message is mapped to receiveOnly channel");
        }
        catch (Exception e) {
            if (e.getMessage().indexOf("receive-only channel") == -1) {
                e.printStackTrace();
                fail("Expected to find 'receive-only channel' in exception text, but got: " + e.getMessage());
            }
        }
    }

    @Test
    public void testReceiveOnlyDefaultChannelFails() throws Exception {
        try {
            ToaService.unmarshal(getClass().getResource("/receiveOnlyDefaultChannelTestService.xml"));
            fail("Should have failed because channel is both receiveOnly and default");
        }
        catch (Exception e) {
            if (e.getMessage().indexOf("receiveOnly and the default channel") == -1) {
                e.printStackTrace();
                fail("Expected to find 'receiveOnly and the default channel' in exception text, but got: " + e.getMessage());
            }
        }
    }

    @Test
    public void testReceiveOnlyChannelValidConfig() throws Exception {
        ToaService service = ToaService.unmarshal(getClass().getResource("/receiveOnlyValidTestService.xml"));

        // Verify the receive-only channel exists and is marked correctly
        boolean foundReceiveOnly = false;
        for (ToaServiceChannel channel : service.getChannels()) {
            if ("InboundChannel".equals(channel.getSimpleName())) {
                assertTrue("InboundChannel should be receiveOnly", channel.isReceiveOnly());
                foundReceiveOnly = true;
            }
            if ("OutboundChannel".equals(channel.getSimpleName())) {
                assertFalse("OutboundChannel should not be receiveOnly", channel.isReceiveOnly());
            }
        }
        assertTrue("Expected to find InboundChannel", foundReceiveOnly);
    }

    @Test
    public void testNoServicePrefixByProperty() throws Exception {
        System.setProperty(ToaService.PROP_PREFIX_CHANNEL_NAMES, "false");
        ToaService service = ToaService.unmarshal(getClass().getResource("/defaultChannelTestService.xml"));
        ToaServiceToRole role = service.getToRole("ServiceA");
        assertNotNull("Expected to find 'ServiceA' role", role);
        ToaServiceChannel channel = role.getChannel(ModelAMessage1.class.getName());
        assertEquals("Channel Name and Simple Channel name should be identical", channel.getSimpleName(), channel.getName());
    }

    /**
     * Model XMLs are copied out of the classpath to a temp file so the ADM parser can read them
     * by path. Those copies used to be cleaned up only by {@link File#deleteOnExit}, which never
     * runs for a container killed with SIGKILL, so they accumulated in the temp directory across
     * restarts (TOA-134). Unmarshalling must leave none behind.
     */
    @Test
    public void testModelTempFilesAreDeletedAfterUnmarshal() throws Exception {
        final Set<String> before = listModelTempFiles();

        // this service declares two models, so it exercises the loop more than once
        ToaService service = ToaService.unmarshal(getClass().getResource("/unambiguousMessageNameTestService.xml"));

        // the models must still have parsed correctly -- the copy is only needed during the parse
        assertEquals("Expected both declared models to have been parsed", 2, service.getMessageModels().size());

        final Set<String> leaked = listModelTempFiles();
        leaked.removeAll(before);
        assertTrue("Unmarshal leaked model temp files in " + System.getProperty("java.io.tmpdir") + ": " + leaked, leaked.isEmpty());
    }

    /**
     * Names of the model temp files ({@code xmd<random>xml}, from {@code UtlFile.copyToTempFile})
     * currently present in the JVM's temp directory.
     */
    private static Set<String> listModelTempFiles() {
        final Set<String> names = new HashSet<String>();
        final File[] files = new File(System.getProperty("java.io.tmpdir")).listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().startsWith("xmd") && file.getName().endsWith("xml")) {
                    names.add(file.getName());
                }
            }
        }
        return names;
    }
}
