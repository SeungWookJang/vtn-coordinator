/*
 * Copyright (c) 2015 NEC Corporation
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vtn.manager.it.ofmock.impl;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;

import org.opendaylight.vtn.manager.it.ofmock.OfMockFlow;
import org.opendaylight.vtn.manager.it.ofmock.OfMockLink;
import org.opendaylight.vtn.manager.it.ofmock.OfMockService;
import org.opendaylight.vtn.manager.it.ofmock.OfMockUtils;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;

import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.Notification;

import org.opendaylight.yang.gen.v1.urn.opendaylight.vtn.config.rev150209.VtnConfig;

import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceivedBuilder;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev100924.MacAddress;

/**
 * MD-SAL service provider of the mock-up of openflowplugin.
 */
public class OfMockProvider implements AutoCloseable, Executor, OfMockService {
    /**
     * Logger instance.
     */
    private static final Logger  LOG =
        LoggerFactory.getLogger(OfMockProvider.class);

    /**
     * The base datapath ID for initial switches.
     */
    private static final long  DPID_BASE = 1000L;

    /**
     * The minimum ID for edge ports in initial switches.
     */
    private static final long  MIN_EDGE_PORT_ID = 2L;

    /**
     * The maximum ID for edge ports in initial switches.
     */
    private static final long  MAX_EDGE_PORT_ID = 3L;

    /**
     * The number of milliseconds to wait for asynchronous task.
     */
    static final long  TASK_TIMEOUT = 10000L;

    /**
     * The number of milliseconds to wait for inventories to be initialized.
     */
    private static final long  INV_INIT_TIMEOUT = 1000L;

    /**
     * The number of attempts to initialize inventory information.
     */
    private static final int  INV_INIT_MAXTRY = 10;

    /**
     * Data broker service.
     */
    private final DataBroker  dataBroker;

    /**
     * RPC provider registry service.
     */
    private final RpcProviderRegistry  rpcRegistry;

    /**
     * Notification provider service.
     */
    private final NotificationProviderService  notificationService;

    /**
     * The giant lock.
     */
    private final ReentrantReadWriteLock  rwLock =
        new ReentrantReadWriteLock();

    /**
     * The global executor service.
     */
    private final ExecutorService  globalExecutor =
        Executors.newSingleThreadExecutor();

    /**
     * A map that keeps OpenFlow nodes.
     */
    private final Map<String, OfNode>  switches = new TreeMap<>();

    /**
     * A set of initial node identifiers.
     */
    private final Set<String>  initialSwitches = new HashSet<>();

    /**
     * VTN node listener.
     */
    private final VtnNodeListener  nodeListener;

    /**
     * VTN port listener.
     */
    private final VtnPortListener  portListener;

    /**
     * The clone of the packet routing table maintained by the VTN Manager.
     */
    private final RoutingTable  routingTable;

    /**
     * Determine whether this provider service is available or not.
     */
    private boolean  serviceAvailable = true;

    /**
     * Global configuration for VTN Manager.
     */
    private VtnConfig  vtnConfig;

    /**
     * Keep {@code true} if inventory information is not yet initialized.
     */
    private boolean  firstRun = true;

    /**
     * AD-SAL inventory manager.
     */
    private AdSalInventory  adSalInventory;

    /**
     * Construct a new instance.
     *
     * @param broker  A {@link DataBroker} service instance.
     * @param rpcReg  A {@link RpcProviderRegistry} service instance.
     * @param nsv     A {@link NotificationProviderService} service instance.
     */
    public OfMockProvider(DataBroker broker, RpcProviderRegistry rpcReg,
                          NotificationProviderService nsv) {
        dataBroker = broker;
        rpcRegistry = rpcReg;
        notificationService = nsv;
        nodeListener = new VtnNodeListener(broker);
        portListener = new VtnPortListener(broker);
        routingTable = new RoutingTable(nsv);
        LOG.debug("openflowplugin mock-up has been created: {}", this);
    }

    /**
     * Return the data broker service.
     *
     * @return  Data broker service.
     */
    public DataBroker getDataBroker() {
        return dataBroker;
    }

    /**
     * Publish the given notification.
     *
     * @param n  A {@link Notification} instance.
     */
    public void publish(Notification n) {
        notificationService.publish(n, globalExecutor);
    }

    /**
     * Determine whether this instance can be reused or not.
     *
     * @return  {@code true} only if this instance can be reused.
     */
    public boolean canReuse() {
        Lock rdlock = rwLock.readLock();
        rdlock.lock();
        try {
            return serviceAvailable;
        } finally {
            rdlock.unlock();
        }
    }

    /**
     * Acquire the global lock.
     *
     * @param writer  If {@code true}, writer lock is acquired.
     *                Otherwise reader lock is acquired.
     * @return  A {@link Lock} instance.
     */
    public Lock acquire(boolean writer) {
        Lock lk = (writer) ? rwLock.writeLock() : rwLock.readLock();
        lk.lock();
        return lk;
    }

    /**
     * Create a new OpenFlow node.
     *
     * <p>
     *   This method must be called with holding writer lock of
     *   {@link #rwLock}.
     * </p>
     *
     * @param dpid  The datapath ID of the node.
     * @return  An {@link OfNode} instance.
     */
    private OfNode createNode(BigInteger dpid) {
        return createNode(ID_OPENFLOW, dpid);
    }

    /**
     * Create a new node.
     *
     * <p>
     *   This method must be called with holding writer lock of
     *   {@link #rwLock}.
     * </p>
     *
     * @param prefix  Protocol prefix of the node.
     * @param dpid    The datapath ID of the node.
     * @return  An {@link OfNode} instance.
     */
    private OfNode createNode(String prefix, BigInteger dpid) {
        OfNode node = new OfNode(this, notificationService, prefix, dpid);
        String nid = node.getNodeIdentifier();
        node.register(rpcRegistry);
        OfNode old = switches.put(nid, node);
        if (old != null) {
            switches.put(nid, old);
            throw new IllegalArgumentException("Node ID confilict: " + nid);
        }

        return node;
    }

    /**
     * Return an {@link OfNode} instance associated with the given MD-SAL
     * node identifier.
     *
     * @param nid    The identifier of the MD-SAL node.
     * @return  An {@link OfNode} instance.
     * @throws IllegalArgumentException
     *    The node specified by {@code nid} was not found.
     */
    private OfNode checkNode(String nid) {
        Lock rdlock = rwLock.readLock();
        rdlock.lock();
        try {
            OfNode node = switches.get(nid);
            if (node == null) {
                throw new IllegalArgumentException("Unknown node: " + nid);
            }

            return node;
        } finally {
            rdlock.unlock();
        }
    }

    /**
     * Return an {@link OfPort} instance associated with the given port
     * identifier string.
     *
     * <p>
     *   This method must be called with holding {@link #rwLock}.
     * </p>
     *
     * @param pid  The port identifier string.
     * @return  An {@link OfNode} instance if found.
     *          {@code null} if not found.
     */
    private OfPort getPort(String pid) {
        String nid = OfMockUtils.getNodeIdentifier(pid);
        OfNode node = switches.get(nid);
        return (node == null) ? null : node.getPort(pid);
    }

    /**
     * Return an {@link OfPort} instance associated with the given MD-SAL
     * node connector identifier.
     *
     * @param pid  The port identifier string.
     * @return  An {@link OfPort} instance.
     * @throws IllegalArgumentException
     *    The node connector specified by {@code pid} was not found.
     */
    private OfPort checkPort(String pid) {
        Lock rdlock = rwLock.readLock();
        rdlock.lock();
        try {
            OfPort port = getPort(pid);
            if (port == null) {
                throw new IllegalArgumentException("Unknown port: " + pid);
            }

            return port;
        } finally {
            rdlock.unlock();
        }
    }

    /**
     * Return the global configuration for VTN Manager.
     *
     * @return  A {@link VtnConfig} instance.
     * @throws Exception  An error occurred.
     */
    private VtnConfig getVtnConfig() throws Exception {
        VtnConfig vcfg = vtnConfig;
        if (vcfg == null) {
            // Load configuration from MD-SAL datastore.
            InstanceIdentifier<VtnConfig> path =
                InstanceIdentifier.create(VtnConfig.class);
            ReadOnlyTransaction rtx = dataBroker.newReadOnlyTransaction();
            CheckedFuture<Optional<VtnConfig>, ReadFailedException> f =
                rtx.read(LogicalDatastoreType.OPERATIONAL, path);
            Optional<VtnConfig> opt = f.checkedGet();
            vcfg = opt.get();
            vtnConfig = vcfg;
        }

        return vcfg;
    }

    /**
     * Invoked when inventory information has been initialized on the first
     * run.
     *
     * @param allPorts  A list of {@link OfPort} instances which represents
     *                  all the physical ports present in the test environment.
     * @throws InterruptedException
     *    The calling thread was interrupted.
     */
    private void initInventory(List<OfPort> allPorts)
        throws InterruptedException {
        if (!firstRun) {
            for (OfPort port: allPorts) {
                String src = port.getPortIdentifier();
                String peer = port.getPeerIdentifier();
                if (peer == null) {
                    portListener.awaitCreated(src);
                } else {
                    routingTable.awaitLink(src, peer, true);
                }
            }

            return;
        }

        firstRun = false;

        // OpenFlow application, such as topology manager, may not be started.
        // So we need to resend inventory and topology notifications for
        // missing inventories.
        for (OfNode node: switches.values()) {
            verify(node);
            adSalInventory.awaitNode(node.getNodeIdentifier());
        }

        List<OfPort> isl = new ArrayList<>();
        for (OfPort port: allPorts) {
            verify(port);
            adSalInventory.awaitPort(port.getPortIdentifier());

            String peer = port.getPeerIdentifier();
            if (peer != null && port.isUp()) {
                isl.add(port);
            }
        }

        for (OfPort port: isl) {
            verifyLink(port);
        }
    }

    /**
     * Ensure that the given node is present.
     *
     * @param node  An {@link OfNode} instance.
     * @throws InterruptedException
     *    The calling thread was interrupted.
     */
    private void verify(OfNode node) throws InterruptedException {
        String nid = node.getNodeIdentifier();
        LOG.trace("Verifying node: {}", nid);

        for (int i = 0; i < INV_INIT_MAXTRY; i++) {
            if (nodeListener.awaitCreated(nid, INV_INIT_TIMEOUT)) {
                LOG.trace("Node has been created: {}", nid);
                return;
            }

            LOG.trace("Resending notification for node {}", nid);
            node.publish();
        }

        String msg = "Node was not created: " + nid;
        LOG.error(msg);
        throw new IllegalStateException(msg);
    }

    /**
     * Ensure that the given switch port is present.
     *
     * @param port  An {@link OfPort} instance.
     * @throws InterruptedException
     *    The calling thread was interrupted.
     */
    private void verify(OfPort port) throws InterruptedException  {
        String pid = port.getPortIdentifier();
        LOG.trace("Verifying port: {}", pid);

        for (int i = 0; i < INV_INIT_MAXTRY; i++) {
            if (portListener.awaitCreated(pid, INV_INIT_TIMEOUT)) {
                LOG.trace("Port has been created: {}", pid);
                return;
            }

            LOG.trace("Resending notification for port {}", pid);
            port.publish(this);
        }

        String msg = "Port was not created: " + pid;
        LOG.error(msg);
        throw new IllegalStateException(msg);
    }

    /**
     * Ensure that the given inter-switch link is present.
     *
     * @param port  An {@link OfPort} instance.
     * @throws InterruptedException
     *    The calling thread was interrupted.
     */
    private void verifyLink(OfPort port) throws InterruptedException {
        String pid = port.getPortIdentifier();
        String peer = port.getPeerIdentifier();
        LOG.trace("Verifying inter-switch link: {} -> {}", pid, peer);

        for (int i = 0; i < INV_INIT_MAXTRY; i++) {
            if (routingTable.awaitLinkUp(pid, peer, INV_INIT_TIMEOUT)) {
                LOG.trace("Inter-swtich link has been established: {} -> {}",
                          pid, peer);
                return;
            }

            LOG.trace("Resending notification for inter-switch link: {} -> {}",
                      pid, peer);
            port.publishLink(this);
        }

        StringBuilder builder = new StringBuilder(
            "Inter-swtich link was not established: ").
            append(pid).append(" -> ").append(peer);
        String msg = builder.toString();
        LOG.error(msg);
        throw new IllegalStateException(msg);
    }

    // AutoCloseable

    /**
     * Close the openflowplugin mock-up.
     */
    @Override
    public void close() {
        if (adSalInventory != null) {
            adSalInventory.close();
        }

        Lock wrlock = rwLock.writeLock();
        wrlock.lock();
        try {
            if (!serviceAvailable) {
                return;
            }

            serviceAvailable = false;
            nodeListener.close();
            portListener.close();
            routingTable.close();

            for (OfNode node: switches.values()) {
                node.close();
            }
            switches.clear();

            globalExecutor.shutdown();
            if (!globalExecutor.awaitTermination(TASK_TIMEOUT,
                                                 TimeUnit.MILLISECONDS)) {
                globalExecutor.shutdownNow();
                if (!globalExecutor.awaitTermination(TASK_TIMEOUT,
                                                     TimeUnit.MILLISECONDS)) {
                    LOG.warn("Global executor did not terminate.");
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to close the openflowplugin mock-up.", e);
        } finally {
            wrlock.unlock();
        }

        LOG.debug("openflowplugin mock-up has been closed: {}", this);
    }

    // Executor

    /**
     * Execute the given task on the global executor service.
     *
     * @param task  The runnable task.
     */
    @Override
    public void execute(Runnable task) {
        globalExecutor.execute(task);
    }

    // OfMockService

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize() throws InterruptedException {
        if (adSalInventory == null) {
            try {
                adSalInventory = new AdSalInventory(dataBroker);
            } catch (RuntimeException e) {
                String msg = "Failed to initialize AD-SAL inventory: " +
                    e.getMessage();
                LOG.error(msg, e);
                throw e;
            }
        }

        boolean done = false;
        List<OfPort> allPorts = new ArrayList<>();
        Lock wrlock = rwLock.writeLock();
        wrlock.lock();
        try {
            if (switches.isEmpty()) {
                // Create a node.
                long dpid = DPID_BASE;
                OfNode node1 = createNode(BigInteger.valueOf(dpid));
                initialSwitches.add(node1.getNodeIdentifier());

                // Create 2 more nodes.
                for (int i = 1; i <= 2; i++) {
                    dpid++;
                    OfNode node = createNode(BigInteger.valueOf(dpid));
                    initialSwitches.add(node.getNodeIdentifier());

                    // Link port 1 with node1.
                    OfPort port = node.addPort(1L);
                    OfPort peer = node1.addPort((long)i);
                    allPorts.add(port);
                    allPorts.add(peer);
                    port.setPeerIdentifier(this, peer.getPortIdentifier());
                    peer.setPeerIdentifier(this, port.getPortIdentifier());

                    // Create 2 edge ports.
                    for (long p = MIN_EDGE_PORT_ID; p <= MAX_EDGE_PORT_ID;
                         p++) {
                        allPorts.add(node.addPort(p));
                    }
                }

                // Enable all ports.
                for (OfPort port: allPorts) {
                    port.setPortState(this, true);
                }
            }

            done = true;
        } catch (Exception e) {
            String msg = "Failed to initialize openflowplugin mock-up.";
            LOG.error(msg, e);
            throw new IllegalStateException(msg, e);
        } finally {
            if (!done) {
                close();
            }
            wrlock.unlock();
        }

        // Ensure that all inventory events have been notified.
        initInventory(allPorts);

        LOG.debug("Test environment has been initialized.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset() throws InterruptedException, TimeoutException {
        Set<String> removedNodes = new HashSet<>();
        Set<String> edgePorts = new HashSet<>();
        Map<String, String> links = new HashMap<>();

        Lock wrlock = rwLock.writeLock();
        wrlock.lock();
        try {
            for (Iterator<Map.Entry<String, OfNode>> it = switches.entrySet().
                     iterator(); it.hasNext();) {
                Map.Entry<String, OfNode> ent = it.next();
                OfNode node = ent.getValue();

                // Ensure that flow table is empty.
                node.awaitFlowCleared(TASK_TIMEOUT);

                String nid = ent.getKey();
                if (initialSwitches.contains(nid)) {
                    // Enable all ports.
                    node.setPortState(true);

                    // Clear packet transmission queue.
                    node.clearTransmittedPacket();

                    for (OfPort port: node.getOfPorts(false)) {
                        String src = port.getPortIdentifier();
                        String peer = port.getPeerIdentifier();
                        links.put(src, peer);
                    }
                    for (OfPort port: node.getOfPorts(true)) {
                        String pid = port.getPortIdentifier();
                        edgePorts.add(pid);
                    }
                } else {
                    // Remove all temporary nodes.
                    it.remove();
                    node.close();
                    removedNodes.add(nid);
                }
            }
        } finally {
            wrlock.unlock();
        }

        // Ensure that all temporary nodes have been removed.
        for (String nid: removedNodes) {
            nodeListener.awaitRemoved(nid);
        }

        // Ensure that all inter-switch links are available.
        for (Map.Entry<String, String> entry: links.entrySet()) {
            String src = entry.getKey();
            String peer = entry.getValue();
            routingTable.awaitLink(src, peer, true);
        }

        // Ensure that all edge ports are in up state.
        for (String pid: edgePorts) {
            portListener.awaitLinkState(pid, true);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String addNode(BigInteger dpid) throws InterruptedException {
        return addNode(ID_OPENFLOW, dpid);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String addNode(BigInteger dpid, boolean sync)
        throws InterruptedException {
        return addNode(ID_OPENFLOW, dpid, sync);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String addNode(String prefix, BigInteger dpid)
        throws InterruptedException {
        return addNode(prefix, dpid, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String addNode(String prefix, BigInteger dpid, boolean sync)
        throws InterruptedException {
        String nid;
        Lock wrlock = rwLock.writeLock();
        wrlock.lock();
        try {
            OfNode node = createNode(prefix, dpid);
            nid = node.getNodeIdentifier();
        } finally {
            wrlock.unlock();
        }

        if (sync) {
            nodeListener.awaitCreated(nid);
            adSalInventory.awaitNode(nid);
        }

        return nid;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeNode(String nid) throws InterruptedException {
        if (initialSwitches.contains(nid)) {
            throw new IllegalArgumentException(
                "Initial node cannot be removed: " + nid);
        }

        Lock wrlock = rwLock.writeLock();
        wrlock.lock();
        try {
            OfNode node = switches.remove(nid);
            if (node == null) {
                throw new IllegalArgumentException("Unknown node: " + nid);
            }
            node.close();
        } finally {
            wrlock.unlock();
        }

        nodeListener.awaitRemoved(nid);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String addPort(String nid, long idx) throws InterruptedException {
        return addPort(nid, idx, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String addPort(String nid, long idx, boolean sync)
        throws InterruptedException {
        if (initialSwitches.contains(nid)) {
            throw new IllegalArgumentException(
                "Initial node cannot be modified: " + nid);
        }

        String pid;
        Lock wrlock = rwLock.writeLock();
        wrlock.lock();
        try {
            OfNode node = checkNode(nid);
            OfPort port = node.addPort(idx);
            pid = port.getPortIdentifier();
        } finally {
            wrlock.unlock();
        }

        if (sync) {
            portListener.awaitCreated(pid);
            adSalInventory.awaitPort(pid);
        }

        return pid;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void awaitPortCreated(String pid) throws InterruptedException {
        portListener.awaitCreated(pid);
        adSalInventory.awaitPort(pid);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removePort(String pid) throws InterruptedException {
        removePort(pid, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removePort(String pid, boolean sync)
        throws InterruptedException {
        String nid = OfMockUtils.getNodeIdentifier(pid);
        if (initialSwitches.contains(nid)) {
            throw new IllegalArgumentException(
                "Initial switch port cannot be removed: " + pid);
        }

        Lock wrlock = rwLock.writeLock();
        wrlock.lock();
        try {
            OfNode node = checkNode(nid);
            node.removePort(pid);
        } finally {
            wrlock.unlock();
        }

        if (sync) {
            portListener.awaitRemoved(pid);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void awaitPortRemoved(String pid) throws InterruptedException {
        portListener.awaitRemoved(pid);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPeerIdentifier(String pid) {
        Lock rdlock = rwLock.readLock();
        rdlock.lock();
        try {
            OfPort port = checkPort(pid);
            return port.getPeerIdentifier();
        } finally {
            rdlock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setPeerIdentifier(String pid, String peer)
        throws InterruptedException {
        setPeerIdentifier(pid, peer, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setPeerIdentifier(String pid, String peer, boolean sync)
        throws InterruptedException {
        String nid = OfMockUtils.getNodeIdentifier(pid);
        if (initialSwitches.contains(nid)) {
            throw new IllegalArgumentException(
                "Initial switch port cannot be modified: " + pid);
        }

        boolean link = (peer != null);
        if (link) {
            String peerNid = OfMockUtils.getNodeIdentifier(peer);
            if (initialSwitches.contains(peerNid)) {
                String msg =
                    "Initial switch port cannot be specified to peer: " + peer;
                throw new IllegalArgumentException(msg);
            }

            // Ensure that both ports are already notified.
            portListener.awaitCreated(pid);
            portListener.awaitCreated(peer);
        }

        String dst;
        boolean changed;
        Lock rdlock = rwLock.readLock();
        rdlock.lock();
        try {
            OfPort port = checkPort(pid);
            dst = (link) ? peer : port.getPeerIdentifier();
            changed = port.setPeerIdentifier(this, peer);
        } finally {
            rdlock.unlock();
        }

        if (sync && changed) {
            routingTable.awaitLink(pid, dst, link);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getNodes() {
        Lock rdlock = rwLock.readLock();
        rdlock.lock();
        try {
            return new ArrayList<String>(switches.keySet());
        } finally {
            rdlock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getPorts(String nid, boolean edge) {
        Lock rdlock = rwLock.readLock();
        rdlock.lock();
        try {
            OfNode node = checkNode(nid);
            return node.getPorts(edge);
        } finally {
            rdlock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPortName(String pid) {
        Lock rdlock = rwLock.readLock();
        rdlock.lock();
        try {
            OfPort port = checkPort(pid);
            return port.getPortName();
        } finally {
            rdlock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] getTransmittedPacket(String pid) {
        Lock rdlock = rwLock.readLock();
        rdlock.lock();
        try {
            OfPort port = checkPort(pid);
            return port.getTransmittedPacket();
        } finally {
            rdlock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] awaitTransmittedPacket(String pid)
        throws InterruptedException {
        long deadline = System.currentTimeMillis() + TASK_TIMEOUT;
        long timeout = TASK_TIMEOUT;
        try {
            for (;;) {
                OfPort port = checkPort(pid);
                byte[] payload = port.awaitTransmittedPacket(timeout);
                if (payload != null) {
                    return payload;
                }
                timeout = deadline - System.currentTimeMillis();
            }
        } catch (TimeoutException e) {
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clearTransmittedPacket() {
        Lock rdlock = rwLock.readLock();
        rdlock.lock();
        try {
            for (OfNode node: switches.values()) {
                node.clearTransmittedPacket();
            }
        } finally {
            rdlock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendPacketIn(String ingress, byte[] payload)
        throws InterruptedException {
        if (ingress == null) {
            throw new IllegalArgumentException("Ingress port cannot be null.");
        }
        if (payload == null || payload.length == 0) {
            throw new IllegalArgumentException("Payload cannot be empty.");
        }

        Lock rdlock = rwLock.readLock();
        rdlock.lock();
        try {
            checkPort(ingress);

            // Synchronize AD-SAL inventory information only for AD-SAL FRM.
            for (OfNode node: switches.values()) {
                String nid = node.getNodeIdentifier();
                adSalInventory.awaitNode(nid);

                for (OfPort port: node.getOfPorts()) {
                    String pid = port.getPortIdentifier();
                    adSalInventory.awaitPort(pid);
                }
            }
        } finally {
            rdlock.unlock();
        }

        InstanceIdentifier<NodeConnector> path =
            OfMockUtils.getPortPath(ingress);
        NodeConnectorRef ref = new NodeConnectorRef(path);
        PacketReceived pin = new PacketReceivedBuilder().
            setIngress(ref).setPayload(payload).build();

        publish(pin);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setPortState(String pid, boolean state)
        throws InterruptedException {
        setPortState(pid, state, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setPortState(String pid, boolean state, boolean sync)
        throws InterruptedException {
        Lock rdlock = rwLock.readLock();
        rdlock.lock();
        try {
            OfPort port = checkPort(pid);
            port.setPortState(this, state);
        } finally {
            rdlock.unlock();
        }

        if (sync) {
            portListener.awaitLinkState(pid, state);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void awaitLinkState(String pid, boolean state)
        throws InterruptedException {
        portListener.awaitLinkState(pid, state);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void awaitLink(String src, String dst, boolean state)
        throws InterruptedException {
        String d = dst;
        if (d == null) {
            OfPort port = checkPort(src);
            d = port.getPeerIdentifier();
        }

        routingTable.awaitLink(src, d, state);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OfMockFlow getFlow(String nid, int table, Match match, int pri) {
        Lock rdlock = rwLock.readLock();
        rdlock.lock();
        try {
            OfNode node = checkNode(nid);
            return node.getFlow(table, match, pri);
        } finally {
            rdlock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OfMockFlow awaitFlow(String nid, int table, Match match, int pri,
                                boolean installed)
        throws InterruptedException {
        OfMockFlowEntry target = new OfMockFlowEntry(nid, table, match, pri);
        long deadline = System.currentTimeMillis() + TASK_TIMEOUT;
        long timeout = TASK_TIMEOUT;
        OfMockFlowEntry ofent;
        try {
            for (;;) {
                OfNode node = checkNode(nid);
                ofent = node.awaitFlow(target, installed, timeout);
                if ((ofent != null) == installed) {
                    return ofent;
                }
                timeout = deadline - System.currentTimeMillis();
            }
        } catch (TimeoutException e) {
        }

        return getFlow(nid, table, match, pri);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getFlowCount(String nid) {
        Lock rdlock = rwLock.readLock();
        rdlock.lock();
        try {
            OfNode node = switches.get(nid);
            if (node == null) {
                throw new IllegalArgumentException("Unknown node: " + nid);
            }

            return node.getFlowCount();
        } finally {
            rdlock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] getControllerMacAddress() throws Exception {
        VtnConfig vcfg = getVtnConfig();
        MacAddress maddr = vcfg.getControllerMacAddress();
        byte[] mac = OfMockUtils.getMacAddress(maddr);
        if (mac == null) {
            throw new IllegalStateException("Invalid MAC address: " + maddr);
        }

        return mac;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getL2FlowPriority() throws Exception {
        VtnConfig vcfg = getVtnConfig();
        Integer value = vcfg.getL2FlowPriority();
        return value.intValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<OfMockLink> getRoute(String src, String dst) {
        return routingTable.getRoute(src, dst);
    }
}
