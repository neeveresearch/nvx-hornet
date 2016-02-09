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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.xml.sax.SAXException;

import com.neeve.root.RootConfig;
import com.neeve.toa.ToaException;
import com.neeve.trace.Tracer;

/**
 * A base class for {@link ServiceDefinitionLocator}s. 
 */
public abstract class AbstractServiceDefinitionLocator implements ServiceDefinitionLocator {
    final protected static Tracer tracer = RootConfig.ObjectConfig.createTracer(RootConfig.ObjectConfig.get("nv.toa"));
    final private static Schema SERVICE_SCHEMA;

    static {
        final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        URL url = AbstractServiceDefinitionLocator.class.getResource("/x-tsml.xsd");
        try {
            SERVICE_SCHEMA = factory.newSchema(url);
        }
        catch (SAXException e) {
            throw new RuntimeException("Unable to set up X-TSML Schema validator. Check that x-tsml.xsd is on your classpath");
        }
    }

    /**
     * Tests if the given URL represents a valid x-tsml service defintion. 
     * 
     * @param url The url to test.
     * @throws ToaException If the provided url doesn't contain a parseable service definition.
     */
    public static final void validateServiceDefinitionFile(URL url) throws ToaException {
        if (tracer.debug) tracer.log("validateServiceDefinitionFile checking url: " + url.toString(), Tracer.Level.DEBUG);

        Validator validator = SERVICE_SCHEMA.newValidator();
        try {
            InputStream is = url.openStream();
            try {
                validator.validate(new StreamSource(url.openStream()));
                return;
            }
            catch (SAXException e) {
                throw new ToaException("Couldn't parse " + url + " as a service definition: " + e.getMessage(), e);
            }
            finally {
                is.close();
            }
        }
        catch (IOException ioe) {
            throw new ToaException("Unable to read service definition at " + url.toString(), ioe);
        }

    }

    /**
     * Tests if the given URL represents a valid x-tsml service defintion. 
     * 
     * @param url The url to test.
     * @return true if valid, false otherwise.
     */
    public static final boolean isServiceDefinitionFile(URL url) {
        if (tracer.debug) tracer.log("isServiceDefinitionFile checking url: " + url.toString(), Tracer.Level.DEBUG);

        if (!url.getPath().endsWith(".xml")) {
            if (tracer.debug) tracer.log("isServiceDefinitionFile returning false, no xml suffix", Tracer.Level.DEBUG);
            return false;
        }

        Validator validator = SERVICE_SCHEMA.newValidator();
        try {
            InputStream is = url.openStream();
            try {
                validator.validate(new StreamSource(url.openStream()));
                return true;
            }
            catch (SAXException e) {
                tracer.log("isServiceDefinitionFile couldn't parse " + url + " as a service definition: " + e.getMessage(), Tracer.Level.WARNING);
            }
            finally {
                is.close();
            }
        }
        catch (IOException ioe) {
            tracer.log("isValidServiceDefinitionFile encountered an error reading: " + url.toString(), Tracer.Level.WARNING);
        }

        return false;
    }
}
