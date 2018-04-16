/**
 * Copyright (c) 2018 Neeve Research & Consulting LLC. All Rights Reserved.
 * Confidential and proprietary information of Neeve Research & Consulting LLC.
 * CopyrightVersion 1.0
 */
package com.neeve.toa.opt.impl;

import com.neeve.sma.MessageBusBinding;
import com.neeve.sma.spi.executor.AbstractExecutorBusProcessorFactory;
import com.neeve.sma.spi.executor.ExecutorBusProcessor;

/**
 * Factory class for delayed acknowledgment controllers' bus processors. 
 */
public class DelayedAckControllerProcessorFactory extends AbstractExecutorBusProcessorFactory {

    /* (non-Javadoc)
     * @see com.neeve.sma.spi.executor.AbstractExecutorBusProcessorFactory#createExecutorBusProcessor(com.neeve.sma.MessageBusBinding)
     */
    @Override
    public ExecutorBusProcessor createExecutorBusProcessor(MessageBusBinding binding) {
        return new DelayedAckControllerImpl.DelayedAckProcessor();
    }

}
