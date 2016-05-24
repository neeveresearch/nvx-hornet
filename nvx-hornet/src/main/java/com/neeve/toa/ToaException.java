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
package com.neeve.toa;

/**
 * Base exception class for all exceptions stemming from the {@link TopicOrientedApplication} plugin.
 */
public class ToaException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ToaException(final Throwable cause) {
        super(cause);
    }

    public ToaException(final String str) {
        super(str);
    }

    public ToaException(final String str, final Throwable cause) {
        super(str, cause);
    }
}
