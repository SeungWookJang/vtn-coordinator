/*
 * Copyright (c) 2013-2015 NEC Corporation
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vtn.manager.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.opendaylight.controller.forwardingrulesmanager.FlowEntry;

/**
 * This class implements flow programming task which installs a flow entry
 * to SDN switch connected to this controller.
 */
public class LocalFlowAddTask extends FlowEntryTask {
    /**
     * Logger instance.
     */
    private static final Logger  LOG =
        LoggerFactory.getLogger(LocalFlowAddTask.class);

    /**
     * Construct a new flow add task.
     *
     * @param mgr   VTN Manager service.
     * @param ctx   MD-SAL datastore transaction context.
     * @param fent  A flow entry to be installed to local node.
     */
    LocalFlowAddTask(VTNManagerImpl mgr, TxContext ctx, FlowEntry fent) {
        super(mgr, ctx, fent);
    }

    /**
     * Execute flow installation task.
     *
     * @return  {@code true} is returned if this task completed successfully.
     *          Otherwise {@code false} is returned.
     */
    @Override
    protected boolean execute() {
        return installLocal(getFlowEntry());
    }

    /**
     * Return a logger object for this class.
     *
     * @return  A logger object.
     */
    @Override
    protected Logger getLogger() {
        return LOG;
    }
}
