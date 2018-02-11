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

import java.io.File;
import java.io.FilenameFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;

import com.neeve.ci.XRuntime;
import com.neeve.toa.spi.AbstractServiceDefinitionLocator;
import com.neeve.toa.spi.ServiceDefinitionLocator;
import com.neeve.trace.Tracer;
import com.neeve.util.UtlResource;
import com.neeve.util.UtlResource.URLFilter;

/**
 * Implements the default lookup strategy for services definition xml. 
 * <p>
 * This {@link ServiceDefinitionLocator} looks for services in the following locations:
 * <ul>
 * <li>file://${NVROOT}/conf/services/*.xml
 * <li>file://${NVROOT}/resources/${application.name}/services/*.xml
 * <li>classpath://services/*.xml
 * </ul>
 * This locator will return any .xml file found in the above locations that xml that 
 * validates against the service definition schema x-tsml.xsd.
 */
public final class DefaultServiceDefinitionLocator extends AbstractServiceDefinitionLocator {

    /**
     * This property controls whether or not strict validation is done on service definition
     * files located by this locator. 
     * <p>
     * When validation is not strict, files found at the locations
     * for this locator are ignored with a warning, when set to <code>true</code>, an exception
     * will be thrown if a file is encountered that is not a valid service definition.
     * <p>
     * <b>Property name:</b> {@value #PROP_STRICT_SERVICE_VALIDATION}
     * <br>
     * <b>Default value:</b> {@value #PROP_STRICT_SERVICE_VALIDATION_DEFAULT}
     * <br>
     * @see #PROP_STRICT_SERVICE_VALIDATION_DEFAULT
     */
    public static final String PROP_STRICT_SERVICE_VALIDATION = "nv.toa.strictservicelocatorvalidation";

    /**
     * Deprecated property name
     * @deprecated use {@link #PROP_STRICT_SERVICE_VALIDATION} instead.
     */
    @Deprecated
    private static final String PROP_STRICT_SERVICE_VALIDATION_DEPRECATED = "nv.toa.strictServiceLocatorValidation";

    /**
     * The default value for strict service definition location validation ({@value #PROP_STRICT_SERVICE_VALIDATION_DEFAULT}).
     * By default files that are not services are ignored and result in a trace log warning.  
     */
    public static final boolean PROP_STRICT_SERVICE_VALIDATION_DEFAULT = false;

    /**
     * This property controls whether or not the default service definition locator will scan the classpath for services. 
     * <p>
     * This property can be used to disable classpath scanning for services. 
     * <p>
     * <b>Property name:</b> {@value #PROP_SCAN_FOR_CLASSPATH_SERVICES}
     * <br>
     * <b>Default value:</b> {@value #PROP_SCAN_FOR_CLASSPATH_SERVICES_DEFAULT}
     * <br>
     * @see #PROP_SCAN_FOR_CLASSPATH_SERVICES_DEFAULT
     */
    public static final String PROP_SCAN_FOR_CLASSPATH_SERVICES = "nv.toa.scanforclasspathservices";

    /**
     * The default value for strict service definition location validation ({@value #PROP_STRICT_SERVICE_VALIDATION_DEFAULT}).
     * By default files that are not services are ignored and result in a trace log warning.  
     */
    public static final boolean PROP_SCAN_FOR_CLASSPATH_SERVICES_DEFAULT = true;

    private final boolean strictValidation = XRuntime.getValue(PROP_STRICT_SERVICE_VALIDATION, XRuntime.getValue(PROP_STRICT_SERVICE_VALIDATION_DEPRECATED, PROP_STRICT_SERVICE_VALIDATION_DEFAULT));
    private final boolean scanForClassPathServices = XRuntime.getValue(PROP_SCAN_FOR_CLASSPATH_SERVICES, PROP_STRICT_SERVICE_VALIDATION_DEFAULT);

    private final URLFilter SERVICE_FILTER = new URLFilter() {

        @Override
        public boolean filter(URL url) {
            if (strictValidation) {
                if (url.getPath().endsWith(".xml")) {
                    AbstractServiceDefinitionLocator.validateServiceDefinitionFile(url);
                    return false;
                }
                else {
                    if (tracer.debug) tracer.log("Ignoring service definition candidate, no xml suffix: " + url, Tracer.Level.DEBUG);
                    return true;
                }
            }
            else {
                return !AbstractServiceDefinitionLocator.isServiceDefinitionFile(url);
            }
        }
    };

    /**
     * Locates valid Hornet services.
     * <p>
     * This {@link ServiceDefinitionLocator} looks for services in the following locations:
     * <ul>
     * <li>file://${NVROOT}/conf/services/*.xml
     * <li>file://${NVROOT}/resources/services/*.xml
     * <li>file://${NVROOT}/resources/${application.name}/services/*.xml
     * <li>classpath://services/*.xml
     * </ul>
     * This locator will return any .xml file found in the above locations that xml that 
     * validates against the service definition schema x-tsml.xsd.
     */
    @Override
    public final void locateServices(final Set<URL> urls) throws Exception {
        findFileSystemServices(new File(XRuntime.getRootDirectory().toString() + File.separator + "conf" + File.separator + "services"), urls);
        findFileSystemServices(new File(XRuntime.getRootDirectory().toString() + File.separator + "resources" + File.separator + "services"), urls);
        String appName = XRuntime.getValue("application.name", null);
        if (appName != null) {
            findFileSystemServices(new File(XRuntime.getRootDirectory().toString() + File.separator + "conf" + File.separator + appName + File.separator + "services"), urls);
            findFileSystemServices(new File(XRuntime.getRootDirectory().toString() + File.separator + "resources" + File.separator + appName + File.separator + "services"), urls);
        }

        // search classpath?
        if (scanForClassPathServices) {
            UtlResource.findClasspathResourcesIn("services", urls, SERVICE_FILTER);
        }
    }

    private final void findFileSystemServices(final File directory, final Set<URL> urls) throws MalformedURLException {
        if (tracer.debug) tracer.log("Looking for service definitions in " + directory, Tracer.Level.DEBUG);
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }

        final File[] files = directory.listFiles(new FilenameFilter() {
            final public boolean accept(final File dir, final String name) {
                return !new File(name).isDirectory() && name.endsWith(".xml");
            }
        });

        for (File file : files) {
            final URL url = file.toURI().toURL();
            if (strictValidation) {
                validateServiceDefinitionFile(url);
                urls.add(url);
            }
            else {
                if (isServiceDefinitionFile(url)) {
                    urls.add(url);
                }
            }
        }
    }
}
