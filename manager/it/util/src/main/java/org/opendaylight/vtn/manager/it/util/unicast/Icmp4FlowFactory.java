/*
 * Copyright (c) 2015 NEC Corporation
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vtn.manager.it.util.unicast;

import org.opendaylight.vtn.manager.it.ofmock.OfMockService;
import org.opendaylight.vtn.manager.it.util.TestHost;
import org.opendaylight.vtn.manager.it.util.packet.EthernetFactory;
import org.opendaylight.vtn.manager.it.util.packet.Icmp4Factory;
import org.opendaylight.vtn.manager.it.util.packet.Inet4Factory;

import org.opendaylight.controller.sal.utils.IPProtocols;

/**
 * {@code Icmp4FlowFactory} describes a test environment for configuring an
 * ICMPv4 unicast flow.
 */
public final class Icmp4FlowFactory extends Inet4FlowFactory {
    /**
     * Raw payload for test packet.
     */
    private static final byte[]  PAYLOAD = {
        (byte)0xaa, (byte)0xbb, (byte)0xcc,
    };

    /**
     * ICMPv4 type.
     */
    private Byte  icmpType;

    /**
     * Construct a new instance.
     *
     * @param ofmock  openflowplugin mock-up service.
     */
    public Icmp4FlowFactory(OfMockService ofmock) {
        super(ofmock);
        setProtocol(IPProtocols.ICMP.byteValue());
    }

    /**
     * Return the ICMPv4 type to be used for test.
     *
     * @return  A {@link Byte} instance or {@code null}.
     */
    public Byte getType() {
        return icmpType;
    }

    /**
     * Set the ICMPv4 type value to be used for test.
     *
     * @param type  ICMPv4 type.
     * @return  This instance.
     */
    public Icmp4FlowFactory setType(byte type) {
        icmpType = Byte.valueOf(type);
        return this;
    }

    // Inet4FlowFactory

    /**
     * Override this method to prevent IPv4 packet from setting raw payload.
     *
     * @param i4fc  Unused.
     */
    @Override
    protected void setRawPayload(Inet4Factory i4fc) {
    }

    // UnicastFlowFactory

    /**
     * {@inheritDoc}
     */
    @Override
    public EthernetFactory createPacketFactory(TestHost src, TestHost dst) {
        EthernetFactory efc = super.createPacketFactory(src, dst);
        Inet4Factory i4fc = efc.getNextFactory(Inet4Factory.class);
        Icmp4Factory ic4fc = Icmp4Factory.newInstance(i4fc);
        ic4fc.setRawPayload(PAYLOAD);

        if (icmpType != null) {
            ic4fc.setType(icmpType.byteValue());
        }

        return efc;
    }
}
