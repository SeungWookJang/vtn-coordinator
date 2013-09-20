/*
 * Copyright (c) 2013 NEC Corporation
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vtn.manager.internal;

import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.opendaylight.vtn.manager.internal.cluster.FlowAddEvent;
import org.opendaylight.vtn.manager.internal.cluster.FlowGroupId;
import org.opendaylight.vtn.manager.internal.cluster.FlowModResult;
import org.opendaylight.vtn.manager.internal.cluster.VTNFlow;

import org.opendaylight.controller.connectionmanager.ConnectionLocality;
import org.opendaylight.controller.connectionmanager.IConnectionManager;
import org.opendaylight.controller.forwardingrulesmanager.FlowEntry;
import org.opendaylight.controller.forwardingrulesmanager.
    IForwardingRulesManager;

/**
 * This class implements flow programming task which installs a VTN flow.
 */
public class FlowAddTask extends RemoteFlowModTask {
    /**
     * Logger instance.
     */
    private final static Logger  LOG =
        LoggerFactory.getLogger(FlowAddTask.class);

    /**
     * A VTN flow to be installed.
     */
    private final VTNFlow  vtnFlow;

    /**
     * Construct a new flow install task.
     *
     * @param mgr    VTN Manager service.
     * @param vflow  A VTN flow to be installed.
     */
    FlowAddTask(VTNManagerImpl mgr, VTNFlow vflow) {
        super(mgr);
        vtnFlow = vflow;
    }

    /**
     * Execute flow installation task.
     *
     * @return  {@code true} is returned if this task completed successfully.
     *          Otherwise {@code false} is returned.
     */
    @Override
    protected boolean execute() {
        // This class expects that the given VTN flow has at least one flow
        // entry.
        FlowGroupId gid = vtnFlow.getGroupId();
        Iterator<FlowEntry> it = vtnFlow.getFlowEntries().iterator();
        if (!it.hasNext()) {
            LOG.error("{}: No flow entry in a VTN flow: {}",
                      vtnManager.getContainerName(), gid);
            return false;
        }

        FlowEntry ingress = it.next();
        List<FlowEntry> entries = new ArrayList<FlowEntry>();
        while (it.hasNext()) {
            entries.add(it.next());
        }

        // This class expects that the ingress flow is installed to local node.
        IConnectionManager cnm = vtnManager.getConnectionManager();
        ConnectionLocality cl = cnm.getLocalityStatus(ingress.getNode());
        if (cl != ConnectionLocality.LOCAL) {
            if (cl == ConnectionLocality.NOT_LOCAL) {
                LOG.error("{}: Ingress flow must be installed to " +
                          "local node: {}", vtnManager.getContainerName(),
                          ingress);
            } else {
                LOG.error("{}: Target node of ingress flow entry is " +
                          "disconnected: {}", vtnManager.getContainerName(),
                          ingress);
            }
            return false;
        }

        // Install flow entries except for ingress flow.
        // Ingress flow must be installed at last in order to avoid
        // PACKET_IN at intermediate switch on the flow path.
        ArrayList<LocalFlowAddTask> local = new ArrayList<LocalFlowAddTask>();
        ArrayList<FlowEntry> remote = null;
        if (!entries.isEmpty()) {
            remote = new ArrayList<FlowEntry>();
            if (!install(entries, local, remote)) {
                rollback(local, null);
                return false;
            }

            if (!remote.isEmpty()) {
                // Direct remote cluster nodes to install flow entries.
                VTNConfig vc = vtnManager.getVTNConfig();
                long limit = System.currentTimeMillis() +
                    (long)vc.getRemoteFlowModTimeout();
                if (!modifyRemoteFlow(remote, false, limit)) {
                    rollback(local, remote);
                    return false;
                }
            }

            // Ensure that all local flows were successfully installed.
            for (LocalFlowAddTask task: local) {
                if (task.waitFor() != FlowModResult.SUCCEEDED) {
                    rollback(local, remote);
                    return false;
                }
            }
        }

        // Put VTN flow into the cluster cache.
        ConcurrentMap<FlowGroupId, VTNFlow> db = vtnManager.getFlowDB();
        vtnFlow.pack();
        db.put(gid, vtnFlow);

        // Install ingress flow.
        if (!installLocal(ingress)) {
            rollback(local, remote);
            return false;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("{}: Installed VTN flow: group={}",
                      vtnManager.getContainerName(), vtnFlow.getGroupId());
        }

        return true;
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

    /**
     * Initiate request for flow modification to remote cluster nodes.
     *
     * @param entries  A list of flow entries to be modified.
     */
    @Override
    protected void sendRequest(List<FlowEntry> entries) {
        // Send flow add event.
        FlowAddEvent ev = new FlowAddEvent(entries);
        vtnManager.postEvent(ev);
    }

    /**
     * Install all flow entries in the specified list to local node.
     *
     * <p>
     *   This method tries to install local flows in background.
     * </p>
     *
     * @param entries  A list of flow entries to be installed.
     * @param local    {@link LocalFlowAddTask} list to set tasks to install
     *                 local flows.
     * @param remote   Flow entry list to set remote flows.
     *                 This method appends all flow entries to be installed
     *                 by remote cluster node to this list.
     * @return  Upon successful completion, {@code true} is returned.
     *          {@code false} is returned if at least one target node is
     *          not connected to any of controllers in the cluster.
     */
    private boolean install(List<FlowEntry> entries,
                            List<LocalFlowAddTask> local,
                            List<FlowEntry> remote) {
        IConnectionManager cnm = vtnManager.getConnectionManager();
        IVTNResourceManager resMgr = vtnManager.getResourceManager();
        for (FlowEntry fent: entries) {
            ConnectionLocality cl = cnm.getLocalityStatus(fent.getNode());
            if (cl == ConnectionLocality.LOCAL) {
                LocalFlowAddTask task = new LocalFlowAddTask(vtnManager, fent);
                local.add(task);
                vtnManager.postAsync(task);
            } else if (cl == ConnectionLocality.NOT_LOCAL) {
                remote.add(fent);
            } else {
                LOG.error("{}: Target node of flow entry is disconnected: {}",
                          vtnManager.getConnectionManager(), fent);
                return false;
            }
        }

        return true;
    }

    /**
     * Rollback flow installation.
     *
     * @param local   A list of local flow add tasks.
     * @param remote  A list of remote flow entries.
     */
    private void rollback(List<LocalFlowAddTask> local,
                          List<FlowEntry> remote) {
        // Uninstall flow entries.
        if (local != null) {
            IForwardingRulesManager frm = vtnManager.getForwardingRuleManager();
            for (LocalFlowAddTask task: local) {
                task.waitFor();
                FlowEntry fent = task.getFlowEntry();
                uninstallLocal(frm, fent);
            }
        }

        FlowGroupId gid = vtnFlow.getGroupId();
        if (remote != null && !remote.isEmpty()) {
            FlowRemoveTask task =
                new FlowRemoveTask(vtnManager, gid, null, remote.iterator());
            task.run();
            task.getResult();
        }

        // Remove the VTN flow from the flow database.
        VTNFlowDatabase fdb = vtnManager.getTenantFlowDB(gid.getTenantName());
        if (fdb != null) {
            fdb.removeIndex(vtnManager, vtnFlow);
        }

        ConcurrentMap<FlowGroupId, VTNFlow> db = vtnManager.getFlowDB();
        db.remove(gid);
    }
}