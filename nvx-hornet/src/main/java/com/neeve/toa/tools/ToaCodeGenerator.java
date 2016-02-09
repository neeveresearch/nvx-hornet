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
package com.neeve.toa.tools;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;

import com.neeve.cli.CliException;
import com.neeve.cli.CommandExecutor;
import com.neeve.cli.annotations.Command;
import com.neeve.cli.annotations.Option;
import com.neeve.root.RootConfig;
import com.neeve.toa.service.ToaService;
import com.neeve.toa.spi.AbstractServiceDefinitionLocator;
import com.neeve.trace.Tracer;
import com.neeve.trace.Tracer.Level;

/**
 * Code generator for TOA Services.
 * <p>
 * <b>NOTE: This functionality is experimental</b>
 */
public class ToaCodeGenerator {
    private final static Tracer tracer = RootConfig.ObjectConfig.createTracer(RootConfig.ObjectConfig.get("nv.toa"));
    private final static FileFilter SERVICE_FILE_FILTER = new FileFilter() {

        @Override
        public boolean accept(File pathname) {
            try {
                return AbstractServiceDefinitionLocator.isServiceDefinitionFile(pathname.toURI().toURL());
            }
            catch (MalformedURLException e) {
                return false;
            }
        }

    };

    @Command(name = "generate", description = "Generates code associated with TOA services")
    public static final void generate(@Option(shortForm = 's', longForm = "srcdir", required = true, description = "source directory containing model files") File srcdir,
                                      @Option(shortForm = 'o', longForm = "outdir", required = true, description = "base out directory for the generated source files") File outdir)
                                                                                                                                                                                    throws IllegalArgumentException, ToaCodeGenException
    {
        if (srcdir == null) {
            throw new IllegalArgumentException("srcdir cannot be null");
        }

        if (outdir == null) {
            throw new IllegalArgumentException("outdir cannot be null");
        }

        if (!srcdir.exists()) {
            tracer.log("Source directory not found: " + srcdir, Tracer.Level.WARNING);
            return;
        }

        // locate and parse service models:
        ArrayList<ToaService> services = new ArrayList<ToaService>();
        for (File f : srcdir.listFiles(SERVICE_FILE_FILTER)) {
            try {
                services.add(ToaService.unmarshal(f.toURI().toURL()));
            }
            catch (MalformedURLException e) {
                //Not a service file
                continue;
            }
            catch (Exception e) {
                tracer.log("Error parsing service model '" + f.getName() + "': " + e.getMessage(), Level.WARNING);
            }
        }

        if (services.isEmpty()) {
            tracer.log("No services found in " + srcdir, Tracer.Level.INFO);
            return;
        }

        // make sure output dir exists
        if (!outdir.exists()) {
            outdir.mkdirs();
        }

        // generate key resolvers
        for (ToaService service : services) {
            try {
                ChannelKeyResolverGenerator.generateKeyResolvers(service, outdir);
            }
            catch (IOException e) {
                throw new ToaCodeGenException("Failed to generate TopicResolvers for service " + service.getName() + ": " + e.getMessage(), e);
            }
        }

    }

    public static final void main(String[] args) throws CliException {
        CommandExecutor.invokeAndExit(new ToaCodeGenerator(), "generate", args);
    }

}
