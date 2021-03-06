/*
 * Copyright (c) 2015 NEC Corporation
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vtn.manager.internal.util.flow.match;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.Unmarshaller;

import org.junit.Test;

import org.opendaylight.vtn.manager.flow.cond.EthernetMatch;
import org.opendaylight.vtn.manager.util.EtherAddress;

import org.opendaylight.vtn.manager.internal.util.packet.EtherHeader;
import org.opendaylight.vtn.manager.internal.util.rpc.RpcErrorTag;
import org.opendaylight.vtn.manager.internal.util.rpc.RpcException;

import org.opendaylight.vtn.manager.internal.TestBase;
import org.opendaylight.vtn.manager.internal.XmlNode;
import org.opendaylight.vtn.manager.internal.XmlDataType;
import org.opendaylight.vtn.manager.internal.XmlValueType;

import org.opendaylight.controller.sal.utils.Status;
import org.opendaylight.controller.sal.utils.StatusCode;

import org.opendaylight.yang.gen.v1.urn.opendaylight.vtn.flow.cond.rev150313.vtn.match.fields.VtnEtherMatch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vtn.flow.cond.rev150313.vtn.match.fields.VtnEtherMatchBuilder;

import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.Match;

import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.EtherType;

/**
 * JUnit test for {@link VTNEtherMatch}.
 */
public class VTNEtherMatchTest extends TestBase {
    /**
     * Root XML element name associated with {@link VTNEtherMatch} class.
     */
    private static final String  XML_ROOT = "vtn-ether-match";

    /**
     * Return a list of {@link XmlDataType} instances that specifies XML node
     * types mapped to a {@link VTNEtherMatch} instance.
     *
     * @param name    The name of the target node.
     * @param parent  Path to the parent node.
     * @return  A list of {@link XmlDataType} instances.
     */
    public static List<XmlDataType> getXmlDataTypes(String name,
                                                    String ... parent) {
        ArrayList<XmlDataType> dlist = new ArrayList<>();
        Collections.addAll(
            dlist,
            new XmlValueType("source-address",
                             EtherAddress.class).add(name).prepend(parent),
            new XmlValueType("destination-address",
                             EtherAddress.class).add(name).prepend(parent),
            new XmlValueType("ether-type",
                             Integer.class).add(name).prepend(parent),
            new XmlValueType("vlan-id",
                             Integer.class).add(name).prepend(parent),
            new XmlValueType("vlan-pcp",
                             Integer.class).add(name).prepend(parent));
        return dlist;
    }

    /**
     * Test case for the following constructors.
     *
     * <ul>
     *   <li>{@link VTNEtherMatch#VTNEtherMatch()}</li>
     *   <li>{@link VTNEtherMatch#VTNEtherMatch(Integer)}</li>
     *   <li>{@link VTNEtherMatch#setEtherType(Integer)}</li>
     * </ul>
     *
     * @throws Exception  An error occurred.
     */
    @Test
    public void testConstructor1() throws Exception {
        VTNEtherMatch ematch = new VTNEtherMatch();
        assertEquals(null, ematch.getSourceAddress());
        assertEquals(null, ematch.getDestinationAddress());
        assertEquals(null, ematch.getEtherType());
        assertEquals(null, ematch.getVlanId());
        assertEquals(null, ematch.getVlanPriority());
        assertEquals(true, ematch.isEmpty());

        int[] types = {
            0, 1, 0x800, 0x806, 0x86dd,
        };
        for (int type: types) {
            Integer etype = Integer.valueOf(type);
            ematch = new VTNEtherMatch(etype);
            assertEquals(null, ematch.getSourceAddress());
            assertEquals(null, ematch.getDestinationAddress());
            assertEquals(etype, ematch.getEtherType());
            assertEquals(null, ematch.getVlanId());
            assertEquals(null, ematch.getVlanPriority());
            assertEquals(false, ematch.isEmpty());

            ematch = new VTNEtherMatch();
            assertEquals(null, ematch.getEtherType());
            ematch.setEtherType(etype);
            assertEquals(etype, ematch.getEtherType());
            ematch.setEtherType(etype);
            assertEquals(etype, ematch.getEtherType());
            for (int i = 1; i <= 10; i++) {
                int t = type + i;
                try {
                    ematch.setEtherType(t);
                    unexpected();
                } catch (RpcException e) {
                    assertEquals(RpcErrorTag.BAD_ELEMENT, e.getErrorTag());
                    Status st = e.getStatus();
                    assertEquals(StatusCode.BADREQUEST, st.getCode());
                    String msg = "Ethernet type conflict: type=0x" +
                        Integer.toHexString(type) + ", expected=0x" +
                        Integer.toHexString(t);
                    assertEquals(msg, st.getDescription());
                }
            }
        }
    }

    /**
     * Test case for the following methods.
     *
     * <ul>
     *   <li>{@link VTNEtherMatch#VTNEtherMatch(EthernetMatch)}</li>
     *   <li>{@link VTNEtherMatch#create(Match)}</li>
     *   <li>{@link VTNEtherMatch#setMatch(MatchBuilder)}</li>
     *   <li>Getter methods.</li>
     * </ul>
     *
     * @throws Exception  An error occurred.
     */
    @Test
    public void testConstructor2() throws Exception {
        EtherAddress[] srcs = {
            null, new EtherAddress(1L), new EtherAddress(0x000102030405L),
        };
        EtherAddress[] dsts = {
            null, new EtherAddress(0xf0f1f2f3f4f5L),
            new EtherAddress(0xa8b9cadbecfdL),
        };
        Integer[] types = {null, 0x800, 0x86dd};
        Integer[] vlans = {null, 0, 1, 4095};
        Short[] priorities = {0, 3, 7};

        EtherMatchParams params = new EtherMatchParams();
        for (EtherAddress src: srcs) {
            params.setSourceAddress(src);
            for (EtherAddress dst: dsts) {
                params.setDestinationAddress(dst);
                for (Integer type: types) {
                    params.setEtherType(type);
                    for (Integer vlan: vlans) {
                        params.setVlanId(vlan).setVlanPriority((Short)null);
                        VTNEtherMatch ematch = params.toVTNEtherMatch();
                        params.verify(ematch);
                        if (vlan == null ||
                            vlan.intValue() == EtherHeader.VLAN_NONE) {
                            continue;
                        }

                        for (Short pri: priorities) {
                            params.setVlanPriority(pri);
                            ematch = params.toVTNEtherMatch();
                            params.verify(ematch);
                        }
                    }
                }
            }
        }

        // Create broken EthernetMatch.
        String[] badAddrs = {
            "", "aa:bb:cc:dd:ee:ff:11", "00:11", "bad_address",
        };
        Unmarshaller um = createUnmarshaller(EthernetMatch.class);
        for (String addr: badAddrs) {
            String xml = new XmlNode("ethermatch").
                setAttribute("src", addr).toString();
            EthernetMatch em = unmarshal(um, xml, EthernetMatch.class);
            assertNotNull(em.getValidationStatus());
            try {
                new VTNEtherMatch(em);
                unexpected();
            } catch (RpcException e) {
                assertEquals(RpcErrorTag.BAD_ELEMENT, e.getErrorTag());
                Status st = e.getStatus();
                assertEquals(StatusCode.BADREQUEST, st.getCode());
                assertEquals("Invalid source MAC address: " + addr,
                             st.getDescription());
            }
        }

        // Invalid ether types.
        Integer[] badTypes = {
            Integer.MIN_VALUE, -10, -1,
            0x10000, 0x10001, 0x33333333, Integer.MAX_VALUE,
        };
        for (Integer type: badTypes) {
            params.setEtherType(type);
            EthernetMatch em = params.toEthernetMatch();
            assertEquals(null, em.getValidationStatus());
            try {
                new VTNEtherMatch(em);
                unexpected();
            } catch (RpcException e) {
                assertEquals(RpcErrorTag.BAD_ELEMENT, e.getErrorTag());
                Status st = e.getStatus();
                assertEquals(StatusCode.BADREQUEST, st.getCode());
                assertEquals("Invalid Ethernet type: " + type,
                             st.getDescription());
            }
        }

        // Invalid VLAN ID.
        params.setEtherType(0x800);
        params.setVlanPriority((Short)null);
        Short[] badVlanIds = {
            Short.MIN_VALUE, -30000, -10000, -3, -2, -1,
            0x1000, 0x1001, 0x5000, Short.MAX_VALUE,
        };
        for (Short vid: badVlanIds) {
            params.setVlanId(vid);
            EthernetMatch em = params.toEthernetMatch();
            assertEquals(null, em.getValidationStatus());
            try {
                new VTNEtherMatch(em);
                unexpected();
            } catch (RpcException e) {
                assertEquals(RpcErrorTag.BAD_ELEMENT, e.getErrorTag());
                Status st = e.getStatus();
                assertEquals(StatusCode.BADREQUEST, st.getCode());
                assertEquals("Invalid VLAN ID: " + vid,
                             st.getDescription());
            }
        }

        // Invalid VLAN priority.
        params.setVlanId(1);
        Byte[] badPcps = {
            Byte.MIN_VALUE, -100, -50, -2, -1,
            8, 9, 10, 50, 100, Byte.MAX_VALUE,
        };
        for (Byte pcp: badPcps) {
            params.setVlanPriority(pcp);
            EthernetMatch em = params.toEthernetMatch();
            assertEquals(null, em.getValidationStatus());
            try {
                new VTNEtherMatch(em);
                unexpected();
            } catch (RpcException e) {
                assertEquals(RpcErrorTag.BAD_ELEMENT, e.getErrorTag());
                Status st = e.getStatus();
                assertEquals(StatusCode.BADREQUEST, st.getCode());
                assertEquals("Invalid VLAN priority: " + pcp,
                             st.getDescription());
            }
        }

        // Specifying VLAN priority without VLAN ID.
        Integer[] untagged = {null, EtherHeader.VLAN_NONE};
        for (Integer vid: untagged) {
            params.setVlanId(vid);
            for (byte pcp = 0; pcp <= 7; pcp++) {
                params.setVlanPriority(pcp);
                EthernetMatch em = params.toEthernetMatch();
                assertEquals(null, em.getValidationStatus());
                try {
                    new VTNEtherMatch(em);
                    unexpected();
                } catch (RpcException e) {
                    assertEquals(RpcErrorTag.BAD_ELEMENT, e.getErrorTag());
                    Status st = e.getStatus();
                    assertEquals(StatusCode.BADREQUEST, st.getCode());
                    assertEquals("VLAN priority requires a valid VLAN ID.",
                                 st.getDescription());
                }
            }
        }

        Match empty = new MatchBuilder().build();
        assertEquals(null, VTNEtherMatch.create(empty));
    }

    /**
     * Test case for the following methods.
     *
     * <ul>
     *   <li>{@link VTNEtherMatch#VTNEtherMatch(org.opendaylight.yang.gen.v1.urn.opendaylight.vtn.flow.cond.rev150313.VtnEtherMatchFields)}</li>
     *   <li>{@link VTNEtherMatch#create(Match)}</li>
     *   <li>{@link VTNEtherMatch#setMatch(MatchBuilder)}</li>
     *   <li>Getter methods.</li>
     *   <li>JAXB bindings.</li>
     * </ul>
     *
     * @throws Exception  An error occurred.
     */
    @Test
    public void testConstructor3() throws Exception {
        EtherAddress[] srcs = {
            null, new EtherAddress(1L), new EtherAddress(0x000102030405L),
        };
        EtherAddress[] dsts = {
            null, new EtherAddress(0xf0f1f2f3f4f5L),
            new EtherAddress(0xa8b9cadbecfdL),
        };
        Integer[] types = {null, 0x800, 0x86dd};
        Integer[] vlans = {null, 0, 1, 4095};
        Short[] priorities = {0, 3, 7};

        EtherMatchParams params = new EtherMatchParams();
        Class<VTNEtherMatch> mtype = VTNEtherMatch.class;
        for (EtherAddress src: srcs) {
            params.setSourceAddress(src);
            for (EtherAddress dst: dsts) {
                params.setDestinationAddress(dst);
                for (Integer type: types) {
                    params.setEtherType(type);
                    for (Integer vlan: vlans) {
                        params.setVlanId(vlan).setVlanPriority((Short)null);
                        VtnEtherMatch vem = params.toVtnEtherMatch();
                        VTNEtherMatch ematch = new VTNEtherMatch(vem);
                        params.verify(ematch);

                        // JAXB test.
                        VTNEtherMatch jaxb = jaxbTest(ematch, mtype, XML_ROOT);
                        jaxb.verify();
                        VtnEtherMatch vem1 =
                            jaxb.toVtnEtherMatchBuilder().build();
                        assertEquals(vem, vem1);

                        if (vlan == null ||
                            vlan.intValue() == EtherHeader.VLAN_NONE) {
                            continue;
                        }

                        for (Short pri: priorities) {
                            params.setVlanPriority(pri);
                            vem = params.toVtnEtherMatch();
                            ematch = new VTNEtherMatch(vem);
                            params.verify(ematch);

                            // JAXB test.
                            jaxb = jaxbTest(ematch, mtype, XML_ROOT);
                            jaxb.verify();
                            vem1 = jaxb.toVtnEtherMatchBuilder().build();
                            assertEquals(vem, vem1);
                        }
                    }
                }
            }
        }

        // Invalid ether types.
        Long[] badTypes = {
            0x10000L, 0x10001L, 0x20000L, 0x7fffffffL, 0x80000000L,
            0xaaaaaaaaL, 0xccccccccL, 0xffff0000L, 0xffffffffL,
        };
        for (Long type: badTypes) {
            VtnEtherMatch vem = new VtnEtherMatchBuilder().
                setEtherType(new EtherType(type)).build();
            try {
                new VTNEtherMatch(vem);
                unexpected();
            } catch (RpcException e) {
                assertEquals(RpcErrorTag.BAD_ELEMENT, e.getErrorTag());
                Status st = e.getStatus();
                assertEquals(StatusCode.BADREQUEST, st.getCode());
                assertEquals("Invalid Ethernet type: " + type,
                             st.getDescription());
            }
        }

        // Specifying VLAN priority without VLAN ID.
        Integer[] untagged = {null, EtherHeader.VLAN_NONE};
        for (Integer vid: untagged) {
            params.setVlanId(vid);
            for (byte pcp = 0; pcp <= 7; pcp++) {
                params.setVlanPriority(pcp);
                VtnEtherMatch vem = params.toVtnEtherMatch();
                try {
                    new VTNEtherMatch(vem);
                    unexpected();
                } catch (RpcException e) {
                    assertEquals(RpcErrorTag.BAD_ELEMENT, e.getErrorTag());
                    Status st = e.getStatus();
                    assertEquals(StatusCode.BADREQUEST, st.getCode());
                    assertEquals("VLAN priority requires a valid VLAN ID.",
                                 st.getDescription());
                }
            }
        }
    }

    /**
     * Test case for {@link VTNEtherMatch#verify()}.
     *
     * @throws Exception  An error occurred.
     */
    @Test
    public void testVerify() throws Exception {
        Unmarshaller um = createUnmarshaller(VTNEtherMatch.class);

        // Invalid ether types.
        Integer[] badTypes = {
            Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -0x70000000,
            -0x10000000, -0x10000, -0xffff, -10, -3, -2, -1,
            0x10000, 0x10001, 0x20000, 0x10000000, 0x50000000,
            Integer.MAX_VALUE - 2, Integer.MAX_VALUE - 1, Integer.MAX_VALUE,
        };
        for (Integer type: badTypes) {
            XmlNode root = new XmlNode(XML_ROOT).
                add(new XmlNode("ether-type", type));
            String xml = root.toString();
            VTNEtherMatch ematch = unmarshal(um, xml, VTNEtherMatch.class);
            try {
                ematch.verify();
                unexpected();
            } catch (RpcException e) {
                assertEquals(RpcErrorTag.BAD_ELEMENT, e.getErrorTag());
                Status st = e.getStatus();
                assertEquals(StatusCode.BADREQUEST, st.getCode());
                assertEquals("Invalid Ethernet type: " + type,
                             st.getDescription());
            }
        }

        // Invalid VLAN ID.
        Integer[] badVlanIds = {
            Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -0x70000000,
            -0x10000000, -0x10000, -0xffff, -10, -3, -2, -1,
            0x1000, 0x1001, 0x1002, 0x20000, 0xffffff, 0x60000000,
            Integer.MAX_VALUE - 2, Integer.MAX_VALUE - 1, Integer.MAX_VALUE,
        };
        for (Integer vid: badVlanIds) {
            XmlNode root = new XmlNode(XML_ROOT).
                add(new XmlNode("vlan-id", vid));
            String xml = root.toString();
            VTNEtherMatch ematch = unmarshal(um, xml, VTNEtherMatch.class);
            try {
                ematch.verify();
                unexpected();
            } catch (RpcException e) {
                assertEquals(RpcErrorTag.BAD_ELEMENT, e.getErrorTag());
                Status st = e.getStatus();
                assertEquals(StatusCode.BADREQUEST, st.getCode());
                assertEquals("Invalid VLAN ID: " + vid,
                             st.getDescription());
            }
        }

        // Invalid VLAN priority.
        Short[] badPcps = {
            Short.MIN_VALUE, -30000, -20000, -10000, -0x100, -3, -2, -1,
            8, 9, 10, 0x100, 300, 10000, Short.MAX_VALUE - 1, Short.MAX_VALUE,
        };
        for (Short pcp: badPcps) {
            XmlNode root = new XmlNode(XML_ROOT).
                add(new XmlNode("vlan-pcp", pcp));
            String xml = root.toString();
            VTNEtherMatch ematch = unmarshal(um, xml, VTNEtherMatch.class);
            try {
                ematch.verify();
                unexpected();
            } catch (RpcException e) {
                assertEquals(RpcErrorTag.BAD_ELEMENT, e.getErrorTag());
                Status st = e.getStatus();
                assertEquals(StatusCode.BADREQUEST, st.getCode());
                assertEquals("Invalid VLAN priority: " + pcp,
                             st.getDescription());
            }
        }

        // Specifying VLAN priority without VLAN ID.
        Integer[] untagged = {null, EtherHeader.VLAN_NONE};
        for (Integer vid: untagged) {
            for (byte pcp = 0; pcp <= 7; pcp++) {
                XmlNode root = new XmlNode(XML_ROOT).
                    add(new XmlNode("vlan-pcp", pcp));
                if (vid != null) {
                    root.add(new XmlNode("vlan-id", vid));
                }
                String xml = root.toString();
                VTNEtherMatch ematch = unmarshal(um, xml, VTNEtherMatch.class);
                try {
                    ematch.verify();
                    unexpected();
                } catch (RpcException e) {
                    assertEquals(RpcErrorTag.BAD_ELEMENT, e.getErrorTag());
                    Status st = e.getStatus();
                    assertEquals(StatusCode.BADREQUEST, st.getCode());
                    assertEquals("VLAN priority requires a valid VLAN ID.",
                                 st.getDescription());
                }
            }
        }
    }

    /**
     * Test case for object identity.
     *
     * <ul>
     *   <li>{@link VTNEtherMatch#equals(Object)}</li>
     *   <li>{@link VTNEtherMatch#hashCode()}</li>
     *   <li>{@link VTNEtherMatch#setConditionKey(StringBuilder)}</li>
     * </ul>
     *
     * @throws Exception  An error occurred.
     */
    @Test
    public void testEquals() throws Exception {
        HashSet<Object> set = new HashSet<>();
        HashSet<String> keySet = new HashSet<>();

        EtherAddress[] srcs = {
            null, new EtherAddress(1L), new EtherAddress(0x000102030405L),
        };
        EtherAddress[] dsts = {
            null, new EtherAddress(0xf0f1f2f3f4f5L),
            new EtherAddress(0xa8b9cadbecfdL),
        };
        Integer[] types = {null, 0x800, 0x86dd};
        Integer[] vlans = {null, 0, 1, 4095};
        Short[] priorities = {0, 3, 7};

        int count = 0;
        StringBuilder b = new StringBuilder();
        EtherMatchParams params = new EtherMatchParams();
        for (EtherAddress src: srcs) {
            params.setSourceAddress(src);
            for (EtherAddress dst: dsts) {
                params.setDestinationAddress(dst);
                for (Integer type: types) {
                    params.setEtherType(type);
                    for (Integer vlan: vlans) {
                        params.setVlanId(vlan).setVlanPriority((Short)null);
                        VTNEtherMatch ematch1 = params.toVTNEtherMatch();
                        VTNEtherMatch ematch2 = params.toVTNEtherMatch();
                        testEquals(set, ematch1, ematch2);
                        count++;

                        b.setLength(0);
                        ematch1.setConditionKey(b);
                        assertEquals(true, keySet.add(b.toString()));
                        b.setLength(0);
                        ematch2.setConditionKey(b);
                        assertEquals(false, keySet.add(b.toString()));

                        if (vlan == null ||
                            vlan.intValue() == EtherHeader.VLAN_NONE) {
                            continue;
                        }

                        for (Short pri: priorities) {
                            params.setVlanPriority(pri);
                            ematch1 = params.toVTNEtherMatch();
                            ematch2 = params.toVTNEtherMatch();
                            testEquals(set, ematch1, ematch2);
                            count++;

                            b.setLength(0);
                            ematch1.setConditionKey(b);
                            assertEquals(true, keySet.add(b.toString()));
                            b.setLength(0);
                            ematch2.setConditionKey(b);
                            assertEquals(false, keySet.add(b.toString()));
                        }
                    }
                }
            }
        }

        assertEquals(count, set.size());
        assertEquals(count, keySet.size());
    }

    /**
     * Ensure that {@link VTNEtherMatch} is mapped to XML root element.
     *
     * @throws Exception  An error occurred.
     */
    @Test
    public void testJAXB() throws Exception {
        Unmarshaller um = createUnmarshaller(VTNEtherMatch.class);
        EtherMatchParams params = new EtherMatchParams();

        // Empty match.
        String xml = new XmlNode(XML_ROOT).toString();
        VTNEtherMatch ematch = unmarshal(um, xml, VTNEtherMatch.class);
        ematch.verify();
        assertEquals(params.toVTNEtherMatch(), ematch);

        // Specifying all fields.
        EtherAddress src = new EtherAddress(0x001122334455L);
        EtherAddress dst = new EtherAddress(0x0abcdef12345L);
        Integer etype = Integer.valueOf(0x806);
        Integer vid = Integer.valueOf(4095);
        Short pcp = Short.valueOf((short)7);
        params.setSourceAddress(src);
        params.setDestinationAddress(dst);
        params.setEtherType(etype);
        params.setVlanId(vid);
        params.setVlanPriority(pcp);

        final String tagSrc = "source-address";
        final String tagDst = "destination-address";
        final String tagType = "ether-type";
        final String tagVid = "vlan-id";
        final String tagPcp = "vlan-pcp";
        xml = new XmlNode(XML_ROOT).
            add(new XmlNode(tagSrc, src.getText())).
            add(new XmlNode(tagDst, dst.getText())).
            add(new XmlNode(tagType, etype)).
            add(new XmlNode(tagVid, vid)).
            add(new XmlNode(tagPcp, pcp)).toString();
        ematch = unmarshal(um, xml, VTNEtherMatch.class);
        ematch.verify();
        assertEquals(params.toVTNEtherMatch(), ematch);

        // Specifying single field.
        params.reset().setSourceAddress(src);
        xml = new XmlNode(XML_ROOT).
            add(new XmlNode(tagSrc, src.getText())).toString();
        ematch = unmarshal(um, xml, VTNEtherMatch.class);
        ematch.verify();
        assertEquals(params.toVTNEtherMatch(), ematch);

        params.reset().setDestinationAddress(dst);
        xml = new XmlNode(XML_ROOT).
            add(new XmlNode(tagDst, dst.getText())).toString();
        ematch = unmarshal(um, xml, VTNEtherMatch.class);
        ematch.verify();
        assertEquals(params.toVTNEtherMatch(), ematch);

        params.reset().setEtherType(etype);
        xml = new XmlNode(XML_ROOT).
            add(new XmlNode(tagType, etype)).toString();
        ematch = unmarshal(um, xml, VTNEtherMatch.class);
        ematch.verify();
        assertEquals(params.toVTNEtherMatch(), ematch);

        params.reset().setVlanId(vid);
        xml = new XmlNode(XML_ROOT).
            add(new XmlNode(tagVid, vid)).toString();
        ematch = unmarshal(um, xml, VTNEtherMatch.class);
        ematch.verify();
        assertEquals(params.toVTNEtherMatch(), ematch);

        params.setVlanPriority(pcp);
        xml = new XmlNode(XML_ROOT).
            add(new XmlNode(tagVid, vid)).
            add(new XmlNode(tagPcp, pcp)).toString();
        ematch = unmarshal(um, xml, VTNEtherMatch.class);
        ematch.verify();
        assertEquals(params.toVTNEtherMatch(), ematch);

        // Ensure that broken values in XML can be detected.
        jaxbErrorTest(VTNEtherMatch.class, getXmlDataTypes(XML_ROOT));
    }

    /**
     * Test case for {@link VTNEtherMatch#match(FlowMatchContext)}.
     *
     * @throws Exception  An error occurred.
     */
    @Test
    public void testMatch() throws Exception {
        matchTest(Integer.valueOf(0), null);

        Integer[] vlanIds = {
            1, 2, 100, 3000, 4095,
        };
        Short[] vlanPcps = {
            0, 3, 7,
        };

        for (Integer vid: vlanIds) {
            for (Short pcp: vlanPcps) {
                matchTest(vid, pcp);
            }
        }
    }

    /**
     * Run tests for {@link VTNEtherMatch#match(FlowMatchContext)}.
     *
     * @param vid  A VLAN ID.
     * @param pcp  A VLAN priority.
     * @throws Exception  An error occurred.
     */
    private void matchTest(Integer vid, Short pcp) throws Exception {
        EtherAddress src = new EtherAddress(0x001122334455L);
        EtherAddress dst = new EtherAddress(0xaabbccddeeffL);
        int etype = 0x806;
        EtherMatchParams header = new EtherMatchParams();
        header.setSourceAddress(src);
        header.setDestinationAddress(dst);
        header.setEtherType(etype);
        header.setVlanId(vid);
        header.setVlanPriority(pcp);
        TestMatchContext ctx = new TestMatchContext().setEtherHeader(header);

        // Empty match should match every packet.
        VTNEtherMatch ematch = new VTNEtherMatch();
        assertEquals(true, ematch.match(ctx));
        ctx.checkMatchFields();

        // Specify single field in Ethernet frame.
        ctx.reset();
        EtherMatchParams params = new EtherMatchParams();
        params.setSourceAddress(src);
        ematch = params.toVTNEtherMatch();
        assertEquals(true, ematch.match(ctx));
        ctx.checkMatchFields(FlowMatchType.DL_SRC);

        ctx.reset();
        params.reset().setDestinationAddress(dst);
        ematch = params.toVTNEtherMatch();
        assertEquals(true, ematch.match(ctx));
        ctx.checkMatchFields(FlowMatchType.DL_DST);

        ctx.reset();
        params.reset().setEtherType(etype);
        ematch = params.toVTNEtherMatch();
        assertEquals(true, ematch.match(ctx));
        ctx.checkMatchFields(FlowMatchType.DL_TYPE);

        // FlowMatchType.DL_VLAN should never be set into FlowMatchContext.
        ctx.reset();
        params.reset().setVlanId(vid);
        ematch = params.toVTNEtherMatch();
        assertEquals(true, ematch.match(ctx));
        ctx.checkMatchFields();

        Set<FlowMatchType> expected = EnumSet.of(
            FlowMatchType.DL_SRC, FlowMatchType.DL_DST, FlowMatchType.DL_TYPE);
        if (vid.intValue() != EtherHeader.VLAN_NONE) {
            params.setVlanPriority(pcp);
            ematch = params.toVTNEtherMatch();
            assertEquals(true, ematch.match(ctx));
            ctx.checkMatchFields(FlowMatchType.DL_VLAN_PCP);
            expected.add(FlowMatchType.DL_VLAN_PCP);
        }

        // Specfify all fields.
        ctx.reset();
        params.setSourceAddress(src);
        params.setDestinationAddress(dst);
        params.setEtherType(etype);
        ematch = params.toVTNEtherMatch();
        assertEquals(true, ematch.match(ctx));
        ctx.checkMatchFields(expected);

        // Ensure that match() returns false if one field does not match.
        EtherAddress anotherMac = new EtherAddress(0x102030405060L);
        ctx.reset();
        params.setSourceAddress(anotherMac);
        ematch = params.toVTNEtherMatch();
        assertEquals(false, ematch.match(ctx));
        ctx.checkMatchFields(FlowMatchType.DL_SRC);

        ctx.reset();
        params.setSourceAddress(src);
        params.setDestinationAddress(anotherMac);
        ematch = params.toVTNEtherMatch();
        assertEquals(false, ematch.match(ctx));
        ctx.checkMatchFields(FlowMatchType.DL_SRC, FlowMatchType.DL_DST);

        int anotherType = 0x800;
        ctx.reset();
        params.setDestinationAddress(dst);
        params.setEtherType(anotherType);
        ematch = params.toVTNEtherMatch();
        assertEquals(false, ematch.match(ctx));
        ctx.checkMatchFields(FlowMatchType.DL_SRC, FlowMatchType.DL_DST,
                             FlowMatchType.DL_TYPE);

        params.setEtherType(etype);
        if (vid.intValue() == EtherHeader.VLAN_NONE) {
            Integer[] vids = {1, 4095};
            for (Integer v: vids) {
                ctx.reset();
                params.setVlanId(v);
                ematch = params.toVTNEtherMatch();
                assertEquals(false, ematch.match(ctx));
                ctx.checkMatchFields(FlowMatchType.DL_SRC,
                                     FlowMatchType.DL_DST,
                                     FlowMatchType.DL_TYPE);

                ctx.reset();
                params.setVlanPriority((short)0);
                ematch = params.toVTNEtherMatch();
                assertEquals(false, ematch.match(ctx));
                ctx.checkMatchFields(FlowMatchType.DL_SRC,
                                     FlowMatchType.DL_DST,
                                     FlowMatchType.DL_TYPE);
            }
        } else {
            int anotherVid = vid.intValue() + 1;
            if (anotherVid > 4095) {
                anotherVid = 1;
            }
            Integer[] vids = {EtherHeader.VLAN_NONE, anotherVid};
            for (Integer v: vids) {
                ctx.reset();
                params.setVlanId(v);
                if (v.intValue() == EtherHeader.VLAN_NONE) {
                    params.setVlanPriority((Short)null);
                } else {
                    params.setVlanPriority(pcp);
                }
                ematch = params.toVTNEtherMatch();
                assertEquals(false, ematch.match(ctx));
                ctx.checkMatchFields(FlowMatchType.DL_SRC,
                                     FlowMatchType.DL_DST,
                                     FlowMatchType.DL_TYPE);
            }

            short anotherPcp = (short)((pcp.shortValue() + 1) & 0x7);
            ctx.reset();
            params.setVlanId(vid);
            params.setVlanPriority(anotherPcp);
            ematch = params.toVTNEtherMatch();
            assertEquals(false, ematch.match(ctx));
            ctx.checkMatchFields(FlowMatchType.DL_SRC, FlowMatchType.DL_DST,
                                 FlowMatchType.DL_TYPE,
                                 FlowMatchType.DL_VLAN_PCP);
        }
    }
}
