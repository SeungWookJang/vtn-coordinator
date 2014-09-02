/*
 * Copyright (c) 2013-2014 NEC Corporation
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vtn.manager.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;

import org.opendaylight.vtn.manager.DataLinkHost;
import org.opendaylight.vtn.manager.EthernetHost;
import org.opendaylight.vtn.manager.SwitchPort;
import org.opendaylight.vtn.manager.VBridgeConfig;
import org.opendaylight.vtn.manager.VBridgeIfPath;
import org.opendaylight.vtn.manager.VBridgePath;
import org.opendaylight.vtn.manager.VNodePath;
import org.opendaylight.vtn.manager.VTenantConfig;
import org.opendaylight.vtn.manager.VTenantPath;
import org.opendaylight.vtn.manager.VTerminalIfPath;
import org.opendaylight.vtn.manager.VTerminalPath;
import org.opendaylight.vtn.manager.internal.cluster.MacMapPath;
import org.opendaylight.vtn.manager.internal.cluster.MapReference;
import org.opendaylight.vtn.manager.internal.cluster.MapType;
import org.opendaylight.vtn.manager.internal.cluster.VlanMapPath;

import org.opendaylight.controller.sal.core.Edge;
import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.sal.core.NodeConnector;
import org.opendaylight.controller.sal.packet.ARP;
import org.opendaylight.controller.sal.packet.Ethernet;
import org.opendaylight.controller.sal.packet.IEEE8021Q;
import org.opendaylight.controller.sal.packet.IPv4;
import org.opendaylight.controller.sal.packet.RawPacket;
import org.opendaylight.controller.sal.packet.address.EthernetAddress;
import org.opendaylight.controller.sal.utils.EtherTypes;
import org.opendaylight.controller.sal.utils.GlobalConstants;
import org.opendaylight.controller.sal.utils.NetUtils;

/**
 * Abstract base class for JUnit tests.
 */
public abstract class TestBase extends Assert {
    /**
     * String Declaration for Mac Unavilable.
     */
    protected static final String MAC_UNAVAILABLE =
        "MAC mapping is unavailable: ";

    /**
     * String Declaration for Mac Used .
     */
    protected static final String MAC_USED =
        "Same MAC address is already mapped: host={";

    /**
     * String Declaration for Mac Inactivated .
     */
    protected static final String MAC_INACTIVATED =
        "MAC mapping has been inactivated: host={";

    /**
     * String Declaration for Switch Reserved .
     */
    protected static final String SWITCH_RESERVED =
        "switch port is reserved: host={";

    /**
     * Throw an error which indicates an unexpected throwable is caught.
     *
     * @param t  A throwable.
     */
    protected static void unexpected(Throwable t) {
        throw new AssertionError("Unexpected throwable: " + t, t);
    }

    /**
     * Throw an error which indicates the test code should never reach here.
     */
    protected static void unexpected() {
        fail("Should never reach here.");
    }

    /**
     * Create a copy of the specified string.
     *
     * @param str  A string to be copied.
     * @return     A copied string.
     */
    protected static String copy(String str) {
        if (str != null) {
            str = new String(str);
        }
        return str;
    }

    /**
     * Create a copy of the specified integer.
     *
     * @param i   An integer to be copied.
     * @return  A copied integer.
     */
    protected static Integer copy(Integer i) {
        if (i != null) {
            i = new Integer(i.intValue());
        }
        return i;
    }

    /**
     * Create a copy of the specified {@link Node}.
     *
     * @param node  A {@link Node} object to be copied.
     * @return      A copied {@link Node} object.
     */
    protected static Node copy(Node node) {
        if (node != null) {
            try {
                node = new Node(node);
            } catch (Exception e) {
                unexpected(e);
            }
        }
        return node;
    }

    /**
     * Create a copy of the specified {@link NodeConnector}.
     *
     * @param nc  A {@link NodeConnector} object to be copied.
     * @return    A copied {@link NodeConnector} object.
     */
    protected static NodeConnector copy(NodeConnector nc) {
        if (nc != null) {
            try {
                nc = new NodeConnector(nc);
            } catch (Exception e) {
                unexpected(e);
            }
        }
        return nc;
    }

    /**
     * Create a copy of the specified {@link EthernetAddress}.
     *
     * @param ea    A {@link EthernetAddress} object to be copied.
     * @return      A copied {@link EthernetAddress} object.
     */
    protected static EthernetAddress copy(EthernetAddress ea) {
        if (ea != null) {
            try {
                ea = new EthernetAddress(ea.getValue());
            } catch (Exception e) {
                unexpected(e);
            }
        }
        return ea;
    }

    /**
     * Create a copy of the specified {@link InetAddress}.
     *
     * @param ia    A {@link InetAddress} object to be copied.
     * @return      A copied {@link InetAddress} object.
     */
    protected static InetAddress copy(InetAddress ia) {
        if (ia != null) {
            try {
                ia = InetAddress.getByAddress(ia.getAddress());
            } catch (Exception e) {
                unexpected(e);
            }
        }
        return ia;
    }

    /**
     * Create a deep copy of the specified {@link InetAddress} set.
     *
     * @param ia    A {@link InetAddress} set to be copied.
     * @return      A copied {@link InetAddress} set.
     */
    protected static Set<InetAddress> copy(Set<InetAddress> ia) {
        if (ia != null) {
            Set<InetAddress> newset = new HashSet<InetAddress>();
            try {
                for (InetAddress iaddr : ia) {
                    newset.add(InetAddress.getByAddress(iaddr.getAddress()));
                }
            } catch (Exception e) {
                unexpected(e);
            }
            ia = newset;
        }
        return ia;
    }

    /**
     * Create a deep copy of the specified {@link VTenantPath} object.
     *
     * @param path  A {@link VTenantPath} object to be copied.
     * @return  A copied {@link VTenantPath} object.
     */
    protected static VTenantPath copy(VTenantPath path) {
        if (path != null) {
            String tname = copy(path.getTenantName());
            if (path instanceof VBridgePath) {
                VBridgePath bpath = (VBridgePath)path;
                String bname = copy(bpath.getBridgeName());
                bpath = new VBridgePath(tname, bname);

                if (path instanceof VBridgeIfPath) {
                    VBridgeIfPath ipath = (VBridgeIfPath)path;
                    String iname = copy(ipath.getInterfaceName());
                    path = new VBridgeIfPath(tname, bname, iname);
                } else if (path instanceof VlanMapPath) {
                    VlanMapPath vpath = (VlanMapPath)path;
                    String mapId = copy(vpath.getMapId());
                    path = new VlanMapPath(bpath, mapId);
                } else if (path instanceof MacMapPath) {
                    path = new MacMapPath(bpath);
                } else {
                    path = bpath;
                }
            } else if (path instanceof VTerminalPath) {
                VTerminalPath vtpath = (VTerminalPath)path;
                String vtname = copy(vtpath.getTerminalName());
                vtpath = new VTerminalPath(tname, vtname);

                if (path instanceof VTerminalIfPath) {
                    VTerminalIfPath ipath = (VTerminalIfPath)path;
                    String iname = copy(ipath.getInterfaceName());
                    path = new VTerminalIfPath(vtpath, iname);
                } else {
                    path = vtpath;
                }
            }
        }
        return path;
    }

    /**
     * Create a deep copy of the specified {@link MapReference} object.
     *
     * @param ref  A {@link MapReference} object to be copied.
     * @return  A copied {@link MapReference} object.
     */
    protected static MapReference copy(MapReference ref) {
        if (ref != null) {
            MapType type = ref.getMapType();
            String cname = copy(ref.getContainerName());
            VNodePath path = (VNodePath)copy(ref.getPath());
            ref = new MapReference(type, cname, path);
        }

        return ref;
    }

    /**
     * Create a list of boolean values and a {@code null}.
     *
     * @return A list of boolean values.
     */
    protected static List<Boolean> createBooleans() {
        return createBooleans(true);
    }

    /**
     * Create a list of boolean values.
     *
     * @param setNull  Set {@code null} to returned list if {@code true}.
     * @return A list of boolean values.
     */
    protected static List<Boolean> createBooleans(boolean setNull) {
        ArrayList<Boolean> list = new ArrayList<Boolean>();
        if (setNull) {
            list.add(null);
        }

        list.add(Boolean.TRUE);
        list.add(Boolean.FALSE);
        return list;
    }

    /**
     * Create a list of strings and a {@code null}.
     *
     * @param base A base string.
     * @return A list of strings.
     */
    protected static List<String> createStrings(String base) {
        return createStrings(base, true);
    }

    /**
     * Create a list of strings.
     *
     * @param base     A base string.
     * @param setNull  Set {@code null} to returned list if {@code true}.
     * @return A list of strings.
     */
    protected static List<String> createStrings(String base, boolean setNull) {
        ArrayList<String> list = new ArrayList<String>();
        if (setNull) {
            list.add(null);
        }

        for (int i = 0; i <= base.length(); i++) {
            list.add(base.substring(0, i));
        }

        return list;
    }

    /**
     * Create a list of Short objects and a {@code null}.
     *
     * @param start    The first value.
     * @param count    The number of Shorts to be created.
     * @return A list of {@link Short}.
     */
    protected static List<Short> createShorts(short start, short count) {
        return createShorts(start, count, true);
    }

    /**
     * Create a list of Short objects.
     *
     * @param start    The first value.
     * @param count    The number of Shorts to be created.
     * @param setNull  Set {@code null} to returned list if {@code true}.
     * @return A list of {@link Short}.
     */
    protected static List<Short> createShorts(short start, short count,
                                              boolean setNull) {
        List<Short> list = new ArrayList<Short>();
        if (count > 0) {
            if (setNull) {
                list.add(null);
                count--;
            }

            for (short i = 0; i < count; i++, start++) {
                list.add(Short.valueOf(start));
            }
        }

        return list;
    }


    /**
     * Create a list of integer objects and a {@code null}.
     *
     * @param start    The first value.
     * @param count    The number of integers to be created.
     * @return A list of {@link Integer}.
     */
    protected static List<Integer> createIntegers(int start, int count) {
        return createIntegers(start, count, true);
    }

    /**
     * Create a list of integer objects.
     *
     * @param start    The first value.
     * @param count    The number of integers to be created.
     * @param setNull  Set {@code null} to returned list if {@code true}.
     * @return A list of {@link Integer}.
     */
    protected static List<Integer> createIntegers(int start, int count,
                                                  boolean setNull) {
        ArrayList<Integer> list = new ArrayList<Integer>();
        if (count > 0) {
            if (setNull) {
                list.add(null);
                count--;
            }

            for (int i = 0; i < count; i++, start++) {
                list.add(new Integer(start));
            }
        }

        return list;
    }

    /**
     * Create a list of {@link Node} and a {@code null}.
     *
     * @param num  The number of objects to be created.
     * @return A list of {@link Node}.
     */
    protected static List<Node> createNodes(int num) {
        return createNodes(num, true);
    }

    /**
     * Create a list of {@link Node}.
     *
     * @param num      The number of objects to be created.
     * @param setNull  Set {@code null} to returned list if {@code true}.
     * @return A list of {@link Node}.
     */
    protected static List<Node> createNodes(int num, boolean setNull) {
        ArrayList<Node> list = new ArrayList<Node>();
        if (setNull) {
            list.add(null);
            num--;
        }

        String[] types = {
            Node.NodeIDType.OPENFLOW,
            Node.NodeIDType.ONEPK,
            Node.NodeIDType.PRODUCTION,
        };

        for (int i = 0; i < num; i++) {
            int tidx = i % types.length;
            String type = types[tidx];
            Object id;
            if (type.equals(Node.NodeIDType.OPENFLOW)) {
                id = new Long((long)i);
            } else {
                id = "Node ID: " + i;
            }

            try {
                Node node = new Node(type, id);
                assertNotNull(node.getType());
                assertNotNull(node.getNodeIDString());
                list.add(node);
            } catch (Exception e) {
                unexpected(e);
            }
        }

        return list;
    }

    /**
     * Create a list of {@link NodeConnector} and a {@code null}.
     *
     * @param num  The number of objects to be created.
     * @return A list of {@link NodeConnector}.
     */
    protected static List<NodeConnector> createNodeConnectors(int num) {
        return createNodeConnectors(num, true);
    }

    /**
     * Create a list of {@link NodeConnector}.
     *
     * @param num      The number of objects to be created.
     * @param setNull  Set {@code null} to returned list if {@code true}.
     * @return A list of {@link NodeConnector}.
     */
    protected static List<NodeConnector> createNodeConnectors(
        int num, boolean setNull) {
        ArrayList<NodeConnector> list = new ArrayList<NodeConnector>();
        if (setNull) {
            list.add(null);
            num--;
        }

        String[] nodeTypes = {
            Node.NodeIDType.OPENFLOW,
            Node.NodeIDType.ONEPK,
            Node.NodeIDType.PRODUCTION,
        };
        String[] connTypes = {
            NodeConnector.NodeConnectorIDType.OPENFLOW,
            NodeConnector.NodeConnectorIDType.ONEPK,
            NodeConnector.NodeConnectorIDType.PRODUCTION,
        };

        for (int i = 0; i < num; i++) {
            int tidx = i % nodeTypes.length;
            String nodeType = nodeTypes[tidx];
            String connType = connTypes[tidx];
            Object nodeId, connId;
            if (nodeType.equals(Node.NodeIDType.OPENFLOW)) {
                nodeId = new Long((long)i);
                connId = new Short((short)(i + 10));
            } else {
                nodeId = "Node ID: " + i;
                connId = "Node Connector ID: " + i;
            }

            try {
                Node node = new Node(nodeType, nodeId);
                assertNotNull(node.getType());
                assertNotNull(node.getNodeIDString());
                NodeConnector nc = new NodeConnector(connType, connId, node);
                assertNotNull(nc.getType());
                assertNotNull(nc.getNodeConnectorIDString());
                list.add(nc);
            } catch (Exception e) {
                unexpected(e);
            }
        }

        return list;
    }

    /**
     * Create a list of unique edges.
     *
     * @param num  The number of edges to be created.
     * @return  A list of unique edges.
     */
    protected static List<Edge> createEdges(int num) {
        List<Edge> list = new ArrayList<Edge>();
        NodeConnector src = null;
        for (NodeConnector nc: createNodeConnectors(num << 1, false)) {
            if (src == null) {
                src = nc;
            } else {
                try {
                    Edge edge = new Edge(src, nc);
                    list.add(edge);
                    src = null;
                } catch (Exception e) {
                    unexpected(e);
                }
            }
        }

        return list;
    }

    /**
     * Create a list of {@link SwitchPort} and a {@code null}.
     *
     * @param num  The number of objects to be created.
     * @return A list of {@link SwitchPort}.
     */
    protected static List<SwitchPort> createSwitchPorts(int num) {
        return createSwitchPorts(num, true);
    }

    /**
     * Create a list of {@link SwitchPort}.
     *
     * @param num      The number of objects to be created.
     * @param setNull  Set {@code null} to returned list if {@code true}.
     * @return A list of {@link SwitchPort}.
     */
    protected static List<SwitchPort> createSwitchPorts(int num,
                                                        boolean setNull) {
        ArrayList<SwitchPort> list = new ArrayList<SwitchPort>();
        if (setNull) {
            list.add(null);
            num--;
            if (num == 0) {
                return list;
            }
        }

        list.add(new SwitchPort(null, null, null));
        num--;
        if (num == 0) {
            return list;
        }

        list.add(new SwitchPort("name", "type", "id"));
        num--;

        for (; num > 0; num--) {
            String name = ((num % 2) == 0) ? null : "name:" + num;
            String type = ((num % 7) == 0) ? null : "type:" + num;
            String id = ((num % 9) == 0) ? null : "id:" + num;
            if (name == null && type == null && id == null) {
                name = "name:" + num;
            }
            list.add(new SwitchPort(name, type, id));
        }

        return list;
    }

    /**
     * Create a {@link VTenantConfig} object.
     *
     * @param desc  Description of the virtual tenant.
     * @param idle  {@code idle_timeout} value for flow entries.
     * @param hard  {@code hard_timeout} value for flow entries.
     * @return  A {@link VBridgeConfig} object.
     */
    protected static VTenantConfig createVTenantConfig(String desc,
            Integer idle, Integer hard) {
        if (idle == null) {
            if (hard == null) {
                return new VTenantConfig(desc);
            } else {
                return new VTenantConfig(desc, -1, hard.intValue());
            }
        } else if (hard == null) {
            return new VTenantConfig(desc, idle.intValue(), -1);
        }

        return new VTenantConfig(desc, idle.intValue(), hard.intValue());
    }

    /**
     * Create a {@link VBridgeConfig} object.
     *
     * @param desc  Description of the virtual bridge.
     * @param age   {@code age} value for aging interval for MAC address table.
     * @return  A {@link VBridgeConfig} object.
     */
    protected static VBridgeConfig createVBridgeConfig(String desc, Integer age) {
        if (age == null) {
            return new VBridgeConfig(desc);
        } else {
            return new VBridgeConfig(desc, age);
        }
    }

    /**
     * Create a list of {@link EthernetAddress} and a {@code null}.
     *
     * @return A list of {@link EthernetAddress}.
     */
    protected static List<EthernetAddress> createEthernetAddresses() {
        return createEthernetAddresses(true);
    }

    /**
     * Create a list of {@link EthernetAddress}.
     *
     * @param setNull  Set {@code null} to returned list if {@code true}.
     * @return A list of {@link EthernetAddress}.
     */
    protected static List<EthernetAddress> createEthernetAddresses(
        boolean setNull) {
        List<EthernetAddress> list = new ArrayList<EthernetAddress>();
        byte [][] addrbytes = {
            new byte[] {(byte)0x00, (byte)0x00, (byte)0x00,
                        (byte)0x00, (byte)0x00, (byte)0x01},
            new byte[] {(byte)0x12, (byte)0x34, (byte)0x56,
                        (byte)0x78, (byte)0x9a, (byte)0xbc},
            new byte[] {(byte)0xfe, (byte)0xdc, (byte)0xba,
                        (byte)0x98, (byte)0x76, (byte)0x54}
        };

        if (setNull) {
            list.add(null);
        }

        for (byte[] addr : addrbytes) {
            try {
                EthernetAddress ea;
                ea = new EthernetAddress(addr);
                list.add(ea);
            } catch (Exception e) {
                unexpected(e);
            }
        }

        return list;
    }

    /**
     * Create a {@link EthernetAddress} instance which represents the
     * MAC address specified by a long integer value.
     *
     * @param mac  A long integer value which represents the MAC address.
     * @return  A {@link EthernetAddress} instance.
     */
    protected static EthernetAddress createEthernetAddresses(long mac) {
        byte[] addr = NetUtils.longToByteArray6(mac);
        EthernetAddress eaddr = null;
        try {
            eaddr = new EthernetAddress(addr);
        } catch (Exception e) {
            unexpected(e);
        }

        return eaddr;
    }

    /**
     * Create a list of {@link InetAddress} set and a {@code null}.
     *
     * @return A list of {@link InetAddress} set.
     */
    protected static List<Set<InetAddress>> createInetAddresses() {
        return createInetAddresses(true);
    }

    /**
     * Create a list of {@link InetAddress} set.
     *
     * @param setNull  Set {@code null} to returned list if {@code true}.
     * @return A list of {@link InetAddress} set.
     */
    protected static List<Set<InetAddress>> createInetAddresses(boolean setNull) {
        List<Set<InetAddress>> list = new ArrayList<Set<InetAddress>>();
        String[][] arrays = {
            {"0.0.0.0"},
            {"255.255.255.255", "10.1.2.3"},
            {"0.0.0.0", "10.0.0.1", "192.255.255.1",
             "2001:420:281:1004:e123:e688:d655:a1b0"},
            {},
        };

        if (setNull) {
            list.add(null);
        }

        for (String[] array : arrays) {
            Set<InetAddress> iset = new HashSet<InetAddress>();
            try {
                for (String addr : array) {
                    assertTrue(iset.add(InetAddress.getByName(addr)));
                }
                list.add(iset);
            } catch (Exception e) {
                unexpected(e);
            }
        }
        return list;
    }

    /**
     * Create a {@link InetAddress} instance which represents the specified
     * IP address.
     *
     * @param addr  A raw IP address.
     * @return  A {@link InetAddress} instance.
     */
    protected static InetAddress createInetAddress(byte[] addr) {
        InetAddress iaddr = null;
        try {
            iaddr = InetAddress.getByAddress(addr);
        } catch (Exception e) {
            unexpected(e);
        }

        return iaddr;
    }

    /**
     * Create a list of {@link VBridgePath} objects.
     *
     * @return  A list of {@link VBridgePath} objects.
     */
    protected static List<VBridgePath> createVBridgePaths() {
        List<VBridgePath> list = new ArrayList<VBridgePath>();
        for (int tidx = 0; tidx < 2; tidx++) {
            String tname = "tenant" + tidx;
            for (int bidx = 0; bidx < 2; bidx++) {
                VBridgePath path = new VBridgePath(tname, "bridge" + bidx);
                list.add(path);
            }
        }

        return list;
    }

    /**
     * Create a list of {@link VBridgePath} objects.
     *
     * @param subclass  If {@code true} is specified, instances of classes
     *                  which extend {@link VBridgePath} are added to the
     *                  returned list.
     * @return  A list of {@link VBridgePath} objects.
     */
    protected static List<VBridgePath> createVBridgePaths(boolean subclass) {
        if (!subclass) {
            return createVBridgePaths();
        }

        List<VBridgePath> list = new ArrayList<VBridgePath>();
        String[] bnames = {"bridge1", "bridge2"};
        String[] inames = {"if_1", "if_2"};
        for (String tname: new String[]{"tenant1", "tenant2"}) {
            for (String bname: bnames) {
                VBridgePath bpath = new VBridgePath(tname, bname);
                list.add(bpath);

                for (String iname: inames) {
                    list.add(new VBridgeIfPath(tname, bname, iname));
                    list.add(new VlanMapPath(bpath, iname));
                }
            }
        }

        return list;
    }

    /**
     * create a {@link PacketContext} object.
     *
     * @param eth   A {@link Ethernet} object.
     * @param nc    A incoming node connector.
     * @return A {@link PacketContext} object.
     */
    protected PacketContext createPacketContext(Ethernet eth, NodeConnector nc) {
        PacketContext pctx = null;
        RawPacket raw = null;
        try {
            raw = new RawPacket(eth.serialize());
        } catch (Exception e) {
            unexpected(e);
        }

        raw.setIncomingNodeConnector(copy(nc));
        pctx = new PacketContext(raw, eth);

        return pctx;
    }

    /**
     * Create a {@link PacketContext} object for unicast packet transmission.
     *
     * @param eth   A {@link Ethernet} object.
     * @param nc    A incoming node connector.
     * @return A {@link PacketContext} object.
     */
    protected PacketContext createUnicastPacketContext(Ethernet eth,
                                                       NodeConnector nc) {
        PacketContext pctx = createPacketContext(eth, nc);
        pctx.addUnicastMatchFields();
        return pctx;
    }

    /**
     * create a {@link RawPacket} object.
     *
     * @param eth   A {@link Ethernet} object.
     * @param nc    A incoming node connector.
     * @return A {@link RawPacket} object.
     */
    protected RawPacket createRawPacket(Ethernet eth, NodeConnector nc) {
        RawPacket raw = null;
        try {
            raw = new RawPacket(eth.serialize());
        } catch (Exception e) {
            unexpected(e);
        }
        raw.setIncomingNodeConnector(copy(nc));

        return raw;
    }

    /**
     * create a {@link Ethernet} object of IPv4 Packet.
     *
     * @param src       A source MAC address
     * @param dst       A destination MAC address
     * @param sender    A sender address
     * @param target    A target address
     * @param vlan      specify vlan ID. if vlan < 0, vlan tag is not added.
     * @return  A {@link Ethernet} object.
     */
    protected Ethernet createIPv4Packet(byte[] src, byte[] dst, byte[] sender,
            byte[] target, short vlan) {

        IPv4 ip = new IPv4();
        ip.setVersion((byte)4).
            setIdentification((short)5).
            setDiffServ((byte)0).
            setECN((byte)0).
            setTotalLength((short)84).
            setFlags((byte)2).
            setFragmentOffset((short)0).
            setTtl((byte)64);

        ip.setDestinationAddress(getInetAddressFromAddress(target));
        ip.setSourceAddress(getInetAddressFromAddress(sender));

        Ethernet eth = new Ethernet();
        eth.setSourceMACAddress(src).setDestinationMACAddress(dst);

        if (vlan >= 0) {
            eth.setEtherType(EtherTypes.VLANTAGGED.shortValue());

            IEEE8021Q vlantag = new IEEE8021Q();
            vlantag.setCfi((byte)0x0).setPcp((byte)0x0).setVid((short)vlan).
                setEtherType(EtherTypes.IPv4.shortValue()).setParent(eth);
            eth.setPayload(vlantag);

            vlantag.setPayload(ip);

        } else {
            eth.setEtherType(EtherTypes.IPv4.shortValue()).setPayload(ip);
        }
        return eth;
    }

    /**
     * create a {@link Ethernet} object of ARP Packet.
     *
     * @param src       A source MAC address
     * @param dst       A destination MAC address
     * @param sender    A sender address
     * @param target    A target address
     * @param vlan      specify vlan ID. if vlan < 0, vlan tag is not added.
     * @param arptype   ARP.REQUEST or ARP.REPLY. (ARP Reply is not implemented yet )
     * @return  A {@link Ethernet object.}
     */
    protected Ethernet createARPPacket(byte[] src, byte[] dst, byte[] sender,
            byte[] target, short vlan, short arptype) {
        ARP arp = new ARP();
        arp.setHardwareType(ARP.HW_TYPE_ETHERNET).
            setProtocolType(EtherTypes.IPv4.shortValue()).
            setHardwareAddressLength((byte)EthernetAddress.SIZE).
            setProtocolAddressLength((byte)target.length).
            setOpCode(arptype).
            setSenderHardwareAddress(src).setSenderProtocolAddress(sender).
            setTargetHardwareAddress(dst).setTargetProtocolAddress(target);

        Ethernet eth = new Ethernet();
        eth.setSourceMACAddress(src).setDestinationMACAddress(dst);

        if (vlan >= 0) {
            eth.setEtherType(EtherTypes.VLANTAGGED.shortValue());

            IEEE8021Q vlantag = new IEEE8021Q();
            vlantag.setCfi((byte)0x0).setPcp((byte)0x0).setVid(vlan)
                    .setEtherType(EtherTypes.ARP.shortValue()).setParent(eth);
            eth.setPayload(vlantag);

            vlantag.setPayload(arp);

        } else {
            eth.setEtherType(EtherTypes.ARP.shortValue()).setPayload(arp);
        }
        return eth;
    }

    /**
     * create a {@link PacketContext} object of ARP Request.
     *
     * @param src       A source MAC address
     * @param dst       A destination MAC address
     * @param sender    A sender address
     * @param target    A target address
     * @param vlan      specify vlan ID. if vlan < 0, vlan tag is not added.
     * @param nc        A node connector
     * @param arptype   ARP.REQUEST or ARP.REPLY. (ARP Reply is not implemented yet )
     * @return  A {@link PacketContext} object.
     */
    protected PacketContext createARPPacketContext(byte[] src, byte[] dst,
            byte[] sender, byte[] target, short vlan, NodeConnector nc,
            short arptype) {
        return createUnicastPacketContext(
                createARPPacket(src, dst, sender, target, vlan, arptype), nc);
    }

    /**
     * create a {@link PacketContext} object of IPv4 packet.
     *
     * @param src       A source MAC address
     * @param dst       A destination MAC address
     * @param sender    A sender address
     * @param target    A target address
     * @param vlan      specify vlan ID. if vlan < 0, vlan tag is not added.
     * @param nc        A node connector
     * @return  A {@link PacketContext} object.
     */
    protected PacketContext createIPv4PacketContext(byte[] src, byte[] dst,
            byte[] sender, byte[] target, short vlan, NodeConnector nc) {
        return createUnicastPacketContext(
                createIPv4Packet(src, dst, sender, target, vlan), nc);
    }

    /**
     * create a {@link RawPacket} object of ARP Request.
     *
     * @param src       A source MAC address
     * @param dst       A destination MAC address
     * @param sender    A sender address
     * @param target    A target address
     * @param vlan      specify val ID. if vlan < 0, vlan tag is not added.
     * @param nc        A node connector
     * @param arptype   ARP.REQUEST or ARP.REPLY. (ARP Reply is not implemented yet )
     * @return  A {@link PacketContext} object.
     */
    protected RawPacket createARPRawPacket(byte[] src, byte[] dst,
            byte[] sender, byte[] target, short vlan, NodeConnector nc,
            short arptype) {
        return createRawPacket(
                createARPPacket(src, dst, sender, target, vlan, arptype), nc);
    }

    /**
     * create a {@link RawPacket} object of IPv4 packet.
     *
     * @param src       A source MAC address
     * @param dst       A destination MAC address
     * @param sender    A sender address
     * @param target    A target address
     * @param vlan      specify vlan ID. if vlan < 0, vlan tag is not added.
     * @param nc        A node connector
     * @return  A {@link PacketContext} object.
     */
    protected RawPacket createIPv4RawPacket(byte[] src, byte[] dst,
            byte[] sender, byte[] target, short vlan, NodeConnector nc) {
        return createRawPacket(
                createIPv4Packet(src, dst, sender, target, vlan), nc);
    }

    /**
     * Create a {@link EthernetHost} instance which represents the
     * specified MAC address and VLAN ID.
     *
     * @param mac   A long integer value which represents the MAC address.
     * @param vlan  A VLAN ID.
     * @return  A {@link EthernetHost} instance.
     */
    protected static EthernetHost createEthernetHost(long mac, short vlan) {
        EthernetAddress eaddr = createEthernetAddresses(mac);
        return new EthernetHost(eaddr, vlan);
    }

    /**
     * Create a list of set of {@link DataLinkHost} instances.
     *
     * @param limit  The number of ethernet addresses to be created.
     * @return  A list of set of {@link DataLinkHost} instances.
     */
    protected static List<Set<DataLinkHost>> createDataLinkHostSet(int limit) {
        return createDataLinkHostSet(limit, true);
    }

    /**
     * Create a list of set of {@link DataLinkHost} instances.
     *
     * @param limit  The number of ethernet addresses to be created.
     * @param setNull  Set {@code null} to returned list if {@code true}.
     * @return  A list of set of {@link DataLinkHost} instances.
     */
    protected static List<Set<DataLinkHost>> createDataLinkHostSet(
        int limit, boolean setNull) {
        List<Set<DataLinkHost>> list = new ArrayList<Set<DataLinkHost>>();
        if (setNull) {
            list.add(null);
        }

        HashSet<DataLinkHost> set = new HashSet<DataLinkHost>();
        short[] vlans = {0, 4095};
        for (short vlan: vlans) {
            for (EthernetAddress ether: createEthernetAddresses()) {
                set.add(new EthernetHost(ether, vlan));
                list.add(new HashSet<DataLinkHost>(set));
                if (list.size() >= limit) {
                    return list;
                }
            }
        }

        for (int i = 0; i < 2; i++) {
            TestDataLink dladdr = new TestDataLink("addr" + i);
            set.add(new TestDataLinkHost(dladdr));
            list.add(new HashSet<DataLinkHost>(set));
            if (list.size() >= limit) {
                break;
            }
        }

        return list;
    }

    /**
     * get {@link InetAddress} object from byte arrays.
     *
     * @param ipaddr    byte arrays of IP Address.
     * @return  A InetAddress object.
     */
    protected InetAddress getInetAddressFromAddress(byte[] ipaddr) {
        InetAddress inet = null;
        try {
            inet = InetAddress.getByAddress(ipaddr);
        } catch (UnknownHostException e) {
            unexpected(e);
        }
        return inet;
    }

    /**
     * Determine whether the specified two strings are identical or not.
     *
     * <p>
     *   This method returns {@code true} if both strings are {@code null}.
     * </p>
     *
     * @param str1  A string to be tested.
     * @param str2  A string to be tested.
     * @return  {@code true} only if thw two strings are identical.
     */
    protected static boolean equals(String str1, String str2) {
        if (str1 == null) {
            return (str2 == null);
        }

        return str1.equals(str2);
    }

    /**
     * Join the separated strings with inserting a separator.
     *
     * @param prefix     A string to be prepended to the string.
     * @param suffix     A string to be appended to the string.
     * @param separator  A separator string.
     * @param args       An array of objects. Note that {@code null} in this
     *                   array is always ignored.
     */
    protected static String joinStrings(String prefix, String suffix,
            String separator, Object... args) {
        StringBuilder builder = new StringBuilder();
        if (prefix != null) {
            builder.append(prefix);
        }

        boolean first = true;
        for (Object o : args) {
            if (o != null) {
                if (first) {
                    first = false;
                } else {
                    builder.append(separator);
                }
                builder.append(o.toString());
            }
        }

        if (suffix != null) {
            builder.append(suffix);
        }

        return builder.toString();
    }

    /**
     * Ensure that the given two objects are identical.
     *
     * @param set  A set of tested objects.
     * @param o1   An object to be tested.
     * @param o2   An object to be tested.
     */
    protected static void testEquals(Set<Object> set, Object o1, Object o2) {
        assertEquals(o1, o2);
        assertEquals(o2, o1);
        assertEquals(o1, o1);
        assertEquals(o2, o2);
        assertEquals(o1.hashCode(), o2.hashCode());
        assertFalse(o1.equals(null));
        assertFalse(o1.equals(new Object()));
        assertFalse(o1.equals("string"));
        assertFalse(o1.equals(set));

        for (Object o : set) {
            assertFalse("o1=" + o1 + ", o=" + o, o1.equals(o));
            assertFalse(o.equals(o1));
        }

        assertTrue(set.add(o1));
        assertFalse(set.add(o1));
        assertFalse(set.add(o2));
    }

    /**
     * Ensure that an instance of {@link VTenantPath} is comparable.
     *
     * @param set  A set of {@link VTenantPath} variants.
     */
    protected static void comparableTest(Set<VTenantPath> set) {
        TreeSet<VTenantPath> treeSet = new TreeSet<VTenantPath>(set);

        VTenantPath empty = new VTenantPath(null);
        boolean added = set.add(empty);
        assertEquals(added, treeSet.add(empty));
        assertEquals(set, treeSet);

        // The first element in the set must be a VTenantPath instance with
        // null name.
        Iterator<VTenantPath> it = treeSet.iterator();
        assertTrue(it.hasNext());
        VTenantPath prev = it.next();
        assertEquals(VTenantPath.class, prev.getClass());
        assertNull(prev.getTenantName());

        Class<?> prevClass = VTenantPath.class;
        HashSet<Class<?>> classSet = new HashSet<Class<?>>();
        ArrayList<String> prevComponens = new ArrayList<String>();
        prevComponens.add(null);

        while (it.hasNext()) {
            VTenantPath path = it.next();
            assertTrue(prev.compareTo(path) < 0);
            assertFalse(prev.equals(path));

            ArrayList<String> components = new ArrayList<String>();
            components.add(path.getTenantName());
            if (path instanceof VNodePath) {
                components.add(((VNodePath)path).getTenantNodeName());
            }
            if (path instanceof VBridgeIfPath) {
                components.add(((VBridgeIfPath)path).getInterfaceName());
            } else if (path instanceof VlanMapPath) {
                components.add(((VlanMapPath)path).getMapId());
            }

            int prevSize = prevComponens.size();
            int compSize = components.size();
            Class<?> cls = path.getClass();
            boolean classChanged = false;
            if (prevSize == compSize) {
                if (cls.equals(prevClass)) {
                    checkPathOrder(prevComponens, components);
                } else {
                    String name = cls.getName();
                    String prevName = prevClass.getName();
                    assertTrue("name=" + name + ", prevName=" + prevName,
                               prevName.compareTo(name) < 0);
                    classChanged = true;
                }
            } else {
                assertTrue(prevSize < compSize);
                classChanged = true;
            }

            if (classChanged) {
                assertTrue(classSet.add(cls));
                prevClass = cls;
            }

            prevComponens = components;
            prev = path;
        }
    }

    /**
     * Verify the order of the path components.
     *
     * @param lesser    A path components that should be less than
     *                  {@code greater}.
     * @param greater   A path components to be compared.
     */
    private static void checkPathOrder(List<String> lesser,
                                       List<String> greater) {
        for (int i = 0; i < lesser.size(); i++) {
            String l = lesser.get(i);
            String g = greater.get(i);
            if (l == null) {
                return;
            }
            assertNotNull(g);
            int ret = l.compareTo(g);
            if (ret != 0) {
                assertTrue(ret < 0);
                return;
            }
        }

        fail("Identical: lesser=" + lesser + ", greater=" + greater);
    }

    /**
     * Ensure that the given object is serializable.
     *
     * @param o  An object to be tested.
     * @return  A deserialized object is returned.
     */
    protected static Object serializeTest(Object o) {
        Object newobj = serializeAndDeserialize(o);

        if (o instanceof Enum) {
            assertSame(o, newobj);
        } else {
            assertNotSame(o, newobj);
            assertEquals(o, newobj);
        }

        return newobj;
    }

    /**
     * Ensure that the given object is serializable.
     *
     * <p>
     *   Note: This method doesn't check if deserialized object equals
     *   input object.
     * </p>
     *
     * @param o  An object to be tested.
     * @return  A deserialized object is returned.
     */
    protected static Object eventSerializeTest(Object o) {
        Object newobj = serializeAndDeserialize(o);
        assertNotSame(o, newobj);

        return newobj;
    }

    /**
     * Serialize and deserialize object.
     *
     * @param o An {@link Object} serialized and deserialized.
     * @return A deserialized object.
     */
    private static Object serializeAndDeserialize(Object o) {
        // Serialize the given object.
        byte[] bytes = null;
        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bout);

            out.writeObject(o);
            out.close();
            bytes = bout.toByteArray();
        } catch (Exception e) {
            unexpected(e);
        }
        assertTrue(bytes.length != 0);

        // Deserialize the object.
        Object newobj = null;
        try {
            ByteArrayInputStream bin = new ByteArrayInputStream(bytes);
            ObjectInputStream in = new ObjectInputStream(bin);
            newobj = in.readObject();
            in.close();
        } catch (Exception e) {
            unexpected(e);
        }
        return newobj;
    }

    /**
     * setup a startup directory
     */
    protected static void setupStartupDir() {
        File confdir = new File(GlobalConstants.STARTUPHOME.toString());
        boolean result = confdir.exists();
        if (!result) {
            result = confdir.mkdirs();
        } else {
            File[] list = confdir.listFiles();
            for (File f : list) {
                f.delete();
            }
        }
    }

    /**
     * cleanup a startup directory
     */
    protected static void cleanupStartupDir() {
        String currdir = new File(".").getAbsoluteFile().getParent();
        File confdir = new File(GlobalConstants.STARTUPHOME.toString());

        if (confdir.exists()) {
            File[] list = confdir.listFiles();
            for (File f : list) {
                f.delete();
            }

            while (confdir != null && confdir.getAbsolutePath() != currdir) {
                confdir.delete();
                String pname = confdir.getParent();
                if (pname == null) {
                    break;
                }
                confdir = new File(pname);
            }
        }
    }

    /**
     * Delete the specified directory recursively.
     *
     * @param file  A {@link File} instance which represents a file or
     *              directory to be removed.
     */
    protected static void delete(File file) {
        if (!file.exists()) {
            return;
        }

        File[] files = file.listFiles();
        if (files == null) {
            // Delete the specified file.
            file.delete();
            return;
        }

        // Make the specified directory empty.
        for (File f: files) {
            delete(f);
        }

        // Delete the specified directory.
        file.delete();
    }

    /**
     * Return a path to the container configuration directory.
     *
     * @param container  The name of the container.
     * @return  A {@link File} instance which represents the container
     *          configuration directory.
     */
    protected static File getConfigDir(String container) {
        File dir = new File(GlobalConstants.STARTUPHOME.toString(), container);
        return new File(dir, "vtn");
    }

    /**
     * Return a path to the tenant configuration directory.
     *
     * @param container  The name of the container.
     * @return  A {@link File} instance which represents the tenant
     *          configuration directory.
     */
    protected static File getTenantConfigDir(String container) {
        File dir = getConfigDir(container);
        return new File(dir, ContainerConfig.Type.TENANT.toString());
    }

    /**
     * Delete configuration directory after test.
     */
    @After
    public void deleteStartUpHome() {
        String dir = GlobalConstants.STARTUPHOME.toString();
        File file = new File(dir).getParentFile();
        delete(file);
    }

    /**
     * check a Ethernet packet whether expected parameters are set.
     *
     * @param msg       if check is failed, report error with a this string.
     * @param eth       An input ethernet frame data.
     * @param ethType   expected ethernet type.
     * @param destMac   expected destination mac address.
     * @param srcMac    expected source mac address.
     * @param vlan      expected vlan id. (if expected untagged, specify 0 or less than 0)
     * @param protoType expected protocol type.
     * @param opCode    expected opCode. if this is not ARP, opCode is not checked.
     * @param senderMac expected sender HW address.
     * @param targetMac expected target HW address.
     * @param senderAddr    expected sender protocol address.
     * @param targetAddr    expected target protocol address.
     *
     */
    protected void checkOutEthernetPacket(String msg, Ethernet eth,
                                          EtherTypes ethType, byte[] srcMac,
                                          byte[] destMac, short vlan,
                                          EtherTypes protoType, short opCode,
                                          byte[] senderMac, byte[] targetMac,
                                          byte[] senderAddr,
                                          byte[] targetAddr) {
        ARP arp = null;
        if (vlan > 0) {
            assertEquals(msg, EtherTypes.VLANTAGGED.shortValue(),
                         eth.getEtherType());
            IEEE8021Q vlantag = (IEEE8021Q)eth.getPayload();
            assertEquals(msg, vlan, vlantag.getVid());
            assertEquals(msg, ethType.shortValue(), vlantag.getEtherType());
            if (ethType.shortValue() == EtherTypes.ARP.shortValue()) {
                arp = (ARP)vlantag.getPayload();
            }
        } else {
            assertEquals(msg, ethType.shortValue(), eth.getEtherType());
            if (ethType.shortValue() == EtherTypes.ARP.shortValue()) {
                arp = (ARP)eth.getPayload();
            }
        }

        if (srcMac != null) {
            assertArrayEquals(msg, srcMac, eth.getSourceMACAddress());
        }
        if (destMac != null) {
            assertArrayEquals(msg, destMac, eth.getDestinationMACAddress());
        }

        if (ethType != null
                && ethType.shortValue() == EtherTypes.ARP.shortValue()) {
            assertNotNull(msg, arp);
            if (protoType != null) {
                assertEquals(msg, protoType.shortValue(), arp.getProtocolType());
            }
            if (opCode >= 0) {
                assertEquals(msg, opCode, arp.getOpCode());
            }
            if (senderMac != null) {
                assertArrayEquals(msg, senderMac,
                        arp.getSenderHardwareAddress());
            }
            if (targetMac != null) {
                assertArrayEquals(msg, targetMac,
                        arp.getTargetHardwareAddress());
            }
            if (senderAddr != null) {
                assertArrayEquals(msg, senderAddr,
                        arp.getSenderProtocolAddress());
            }
            if (targetAddr != null) {
                assertArrayEquals(msg, targetAddr,
                        arp.getTargetProtocolAddress());
            }
        }
    }

    /**
     * check a Ethernet packet whether expected parameters are set.
     * (for IPv4 packet)
     *
     * @param msg       if check is failed, report error with a this string.
     * @param eth       input ethernet frame data.
     * @param ethType   expected ethernet type.
     * @param destMac   expected destination mac address.
     * @param srcMac    expected source mac address.
     * @param vlan      expected vlan id. (if expected untagged, specify 0 or less than 0)
     */
    protected void checkOutEthernetPacketIPv4(String msg, Ethernet eth,
                                              EtherTypes ethType,
                                              byte[] srcMac, byte[] destMac,
                                              short vlan) {
        checkOutEthernetPacket(msg, eth, ethType, srcMac, destMac, vlan, null,
                               (short)-1, null, null, null, null);
    }

    /**
     * let a thread sleep. used for dispatch other thread.
     *
     * @param millis    the length of time in millisecond.
     */
    protected void sleep(long millis) {
        Thread.yield();
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            unexpected(e);
        }
    }

    /**
     * Flush all pending tasks on the VTN task thread.
     */
    protected void flushTasks(VTNManagerImpl vtnMgr) {
        NopTask task = new NopTask();
        vtnMgr.postTask(task);
        assertTrue(task.await(10, TimeUnit.SECONDS));
    }

    /**
     * A dummy task to flush tasks on the VTN task thread.
     */
    private class NopTask implements Runnable {
        /**
         * A latch to wait for completion.
         */
        private final CountDownLatch  latch = new CountDownLatch(1);

        /**
         * Wake up all threads waiting for this task.
         */
        @Override
        public void run() {
            latch.countDown();
        }

        /**
         * Wait for completion of this task.
         *
         * @param timeout  The maximum time to wait.
         * @param unit     The time unit of the {@code timeout} argument.
         * @return  {@code true} is returned if this task completed.
         *          Otherwise {@code false} is returned.
         */
        private boolean await(long timeout, TimeUnit unit) {
            try {
                return latch.await(timeout, unit);
            } catch (InterruptedException e) {
                return false;
            }
        }
    }
}

