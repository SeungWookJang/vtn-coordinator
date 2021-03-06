/*
 * Copyright (c) 2014-2015 NEC Corporation
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vtn.manager.internal.packet.cache;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import org.opendaylight.vtn.manager.internal.PacketContext;
import org.opendaylight.vtn.manager.internal.util.flow.match.FlowMatchType;

import org.opendaylight.vtn.manager.internal.TestBase;

import org.opendaylight.controller.sal.action.Action;
import org.opendaylight.controller.sal.action.SetTpDst;
import org.opendaylight.controller.sal.action.SetTpSrc;
import org.opendaylight.controller.sal.match.Match;
import org.opendaylight.controller.sal.match.MatchField;
import org.opendaylight.controller.sal.match.MatchType;
import org.opendaylight.controller.sal.packet.Ethernet;
import org.opendaylight.controller.sal.packet.ICMP;
import org.opendaylight.controller.sal.packet.IPv4;
import org.opendaylight.controller.sal.utils.IPProtocols;

/**
 * JUnit test for {@link IcmpPacket}.
 */
public class IcmpPacketTest extends TestBase {
    /**
     * The flag bit which indicates the ICMP type.
     */
    private static final int  ICMP_TYPE = 0x1;

    /**
     * The flag bit which indicates the ICMP code.
     */
    private static final int  ICMP_CODE = 0x2;

    /**
     * The flag bits which indicates all modifyable fields.
     */
    private static final int  ICMP_ALL = (ICMP_TYPE | ICMP_CODE);

    /**
     * ICMP identifier.
     */
    private short  identifier;

    /**
     * ICMP sequence number.
     */
    private short  sequenceNumber = 0x777;

    /**
     * Test case for getter methods.
     *
     * @throws Exception  An error occurred.
     */
    @Test
    public void testGetter() throws Exception {
        short[] types = {0, 1, 10, 50, 80, 100, 150, 200, 254, 255};
        short[] codes = {0, 1, 33, 66, 88, 128, 143, 230, 254, 255};
        for (short type: types) {
            for (short code: codes) {
                ICMP pkt = createICMP(type, code);
                IcmpPacket icmp = new IcmpPacket(pkt);
                assertEquals(type, icmp.getIcmpType());
                assertEquals(code, icmp.getIcmpCode());

                // commit() should return false.
                Ethernet ether = createEthernet(pkt);
                PacketContext pctx = createPacketContext(
                    ether, EtherPacketTest.NODE_CONNECTOR);
                assertFalse(icmp.commit(pctx));
                assertEquals(null, pctx.getFilterActions());
                for (FlowMatchType mtype: FlowMatchType.values()) {
                    assertFalse(pctx.hasMatchField(mtype));
                }
            }
        }
    }

    /**
     * Test case for setter methods and below methods.
     *
     * <ul>
     *   <li>{@link IcmpPacket#clone()}</li>
     *   <li>{@link IcmpPacket#updateChecksum(Inet4Packet)}</li>
     * </ul>
     *
     * @throws Exception  An error occurred.
     */
    @Test
    public void testSetter() throws Exception {
        short type0 = 0;
        short code0 = 8;
        short type1 = 10;
        short code1 = 100;
        short type2 = 33;
        short code2 = -1;

        Map<Class<? extends Action>, Action> salActions =
            new LinkedHashMap<Class<? extends Action>, Action>();
        salActions.put(SetTpSrc.class, new SetTpSrc(type2));
        salActions.put(SetTpDst.class, new SetTpDst(code2));

        for (int flags = ICMP_TYPE; flags <= ICMP_ALL; flags++) {
            ICMP pkt = createICMP(type0, code0);
            IcmpPacket icmp = new IcmpPacket(pkt);
            short checksum = pkt.getChecksum();

            short type = type0;
            short code = code0;
            Ethernet ether = createEthernet(pkt);
            PacketContext pctx =
                createPacketContext(ether, EtherPacketTest.NODE_CONNECTOR);
            for (Action act: salActions.values()) {
                pctx.addFilterAction(act);
            }
            IcmpPacket icmp1 = icmp.clone();

            if ((flags & ICMP_TYPE) != 0) {
                // Modify ICMP type.
                icmp1.setIcmpType(type1);
                type = type1;
            }
            if ((flags & ICMP_CODE) != 0) {
                // Modify ICMP code.
                icmp1.setIcmpCode(code1);
                code = code1;
            }

            assertEquals(type, icmp1.getIcmpType());
            assertEquals(code, icmp1.getIcmpCode());

            // The packet should not be modified until commit() is called.
            assertSame(pkt, icmp1.getPacket());

            assertEquals(true, icmp1.commit(pctx));
            ICMP newPkt = icmp1.getPacket();
            assertNotSame(pkt, newPkt);
            assertEquals((byte)type, newPkt.getType());
            assertEquals((byte)code, newPkt.getCode());
            assertEquals(checksum, newPkt.getChecksum());
            assertEquals(identifier, newPkt.getIdentifier());
            assertEquals(sequenceNumber, newPkt.getSequenceNumber());
            assertEquals((byte)type0, pkt.getType());
            assertEquals((byte)code0, pkt.getCode());
            assertEquals(checksum, pkt.getChecksum());
            assertEquals(identifier, pkt.getIdentifier());
            assertEquals(sequenceNumber, pkt.getSequenceNumber());

            assertTrue(pctx.hasMatchField(FlowMatchType.DL_TYPE));
            assertTrue(pctx.hasMatchField(FlowMatchType.IP_PROTO));

            // updateChecksum() must return false.
            assertFalse(icmp.updateChecksum(null));

            List<Action> filterActions =
                new ArrayList<Action>(pctx.getFilterActions());
            assertEquals(new ArrayList<Action>(salActions.values()),
                         filterActions);

            // Actions for unchanged field will be removed if corresponding
            // match type is configured in PacketContext.
            List<Action> actions = new ArrayList<Action>();
            if ((flags & ICMP_TYPE) != 0) {
                actions.add(salActions.get(SetTpSrc.class));
            }
            if ((flags & ICMP_CODE) != 0) {
                actions.add(salActions.get(SetTpDst.class));
            }

            pctx = createPacketContext(ether, EtherPacketTest.NODE_CONNECTOR);
            for (FlowMatchType mt: FlowMatchType.values()) {
                pctx.addMatchField(mt);
            }
            for (Action act: salActions.values()) {
                pctx.addFilterAction(act);
            }
            assertEquals(true, icmp1.commit(pctx));
            assertSame(newPkt, icmp1.getPacket());
            filterActions = new ArrayList<Action>(pctx.getFilterActions());
            assertEquals(actions, filterActions);

            // The original packet should not be affected.
            assertEquals(type0, icmp.getIcmpType());
            assertEquals(code0, icmp.getIcmpCode());
            assertSame(pkt, icmp.getPacket());

            assertEquals((byte)type0, pkt.getType());
            assertEquals((byte)code0, pkt.getCode());
            assertEquals(checksum, pkt.getChecksum());
            assertEquals(identifier, pkt.getIdentifier());
            assertEquals(sequenceNumber, pkt.getSequenceNumber());

            // Set values in the original packet.
            icmp1.setIcmpType(type0);
            icmp1.setIcmpCode(code0);
            assertEquals(false, icmp1.commit(pctx));
            assertEquals(type0, icmp1.getIcmpType());
            assertEquals(code0, icmp1.getIcmpCode());

            // Ensure that a set of modified values is deeply cloned.
            IcmpPacket icmp2 = icmp1.clone();
            assertEquals(type0, icmp1.getIcmpType());
            assertEquals(code0, icmp1.getIcmpCode());
            assertEquals(type0, icmp2.getIcmpType());
            assertEquals(code0, icmp2.getIcmpCode());
            icmp2.setIcmpType(type1);
            icmp2.setIcmpCode(code1);
            assertEquals(type0, icmp1.getIcmpType());
            assertEquals(code0, icmp1.getIcmpCode());
            assertEquals(type1, icmp2.getIcmpType());
            assertEquals(code1, icmp2.getIcmpCode());
        }
    }

    /**
     * Test case for {@link IcmpPacket#setMatch(Match, Set)}.
     */
    @Test
    public void testSetMatch() {
        short type = 33;
        short code = 66;
        Map<FlowMatchType, MatchField> tpFields = new HashMap<>();
        tpFields.put(FlowMatchType.ICMP_TYPE,
                     new MatchField(MatchType.TP_SRC, type));
        tpFields.put(FlowMatchType.ICMP_CODE,
                     new MatchField(MatchType.TP_DST, code));

        short type1 = 29;
        short code1 = 1;
        ICMP pkt = createICMP(type, code);
        IcmpPacket icmp = new IcmpPacket(pkt);

        Match match = new Match();
        Set<FlowMatchType> fields = EnumSet.noneOf(FlowMatchType.class);
        icmp.setMatch(match, fields);
        List<MatchType> matches = match.getMatchesList();
        assertEquals(0, matches.size());

        for (Map.Entry<FlowMatchType, MatchField> entry: tpFields.entrySet()) {
            FlowMatchType fmtype = entry.getKey();
            MatchField mfield = entry.getValue();
            MatchType mtype = mfield.getType();

            match = new Match();
            fields = EnumSet.of(fmtype);
            icmp.setMatch(match, fields);
            matches = match.getMatchesList();
            assertEquals(1, matches.size());
            assertEquals(mfield, match.getField(mtype));
        }

        // setMatch() always has to see the original.
        icmp.setIcmpType(type1);
        icmp.setIcmpCode(code1);
        fields = EnumSet.noneOf(FlowMatchType.class);
        fields.addAll(tpFields.keySet());
        match = new Match();
        icmp.setMatch(match, fields);
        matches = match.getMatchesList();
        assertEquals(tpFields.size(), matches.size());
        for (MatchField mfield: tpFields.values()) {
            MatchType mtype = mfield.getType();
            assertEquals(mfield, match.getField(mtype));
        }
    }

    /**
     * Create an {@link ICMP} instance for test.
     *
     * @param type   ICMP type.
     * @param code   ICMP code.
     * @return  An {@link ICMP} instance.
     */
    private ICMP createICMP(short type, short code) {
        ICMP pkt = new ICMP();
        identifier++;
        sequenceNumber++;
        pkt.setType((byte)type).setCode((byte)code).
            setIdentifier(identifier).setSequenceNumber(sequenceNumber);

        // Serialize the packet to update checksum.
        return copy(pkt, new ICMP());
    }

    /**
     * Create an {@link Ethernet} instance that contains the given ICMP packet.
     *
     * @param icmp  An {@link ICMP} instance.
     * @return  An {@link Ethernet} instance.
     */
    private Ethernet createEthernet(ICMP icmp) {
        IPv4 ipv4 = createIPv4(IPProtocols.ICMP.shortValue(), icmp);
        return createEthernet(ipv4);
    }
}
