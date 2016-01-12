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
package com.neeve.toa.tools;

/**
 * Thrown by {@link ToaCodeGenerator} if there is an error in code generation. 
 */
public class ToaCodeGenException extends Exception {
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new exception with the specified detail message. 
     * <p>
     * The cause is not initialized, and may subsequently be initialized by a call to initCause.
     * @param message The detail message.
     */
    public ToaCodeGenException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified detail message and cause. 
     * @param cause The cause.
     */
    public ToaCodeGenException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new exception with the specified cause.
     * @param cause The cause.
     */
    public ToaCodeGenException(Throwable cause) {
        super(cause);
    }

}
