/*
 * Copyright (c) 2015 NEC Corporation
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vtn.manager.it.util.packet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static org.opendaylight.vtn.manager.it.util.ModelDrivenTestBase.toIpv4Prefix;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Set;

import org.opendaylight.vtn.manager.util.NumberUtils;

import org.opendaylight.vtn.manager.it.util.match.FlowMatchType;

import org.opendaylight.controller.sal.packet.IPv4;
import org.opendaylight.controller.sal.packet.Packet;
import org.opendaylight.controller.sal.utils.EtherTypes;

import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.IpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._3.match.Ipv4MatchBuilder;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Dscp;

/**
 * {@code Inet4Factory} is a utility class used to create or to verify an
 * IPv4 packet.
 */
public final class Inet4Factory extends PacketFactory {
    /**
     * Default value for the time-to-live value.
     */
    private static final byte  DEFAULT_TTL = (byte)64;

    /**
     * The source IP address.
     */
    private InetAddress  sourceAddress;

    /**
     * The target IP address.
     */
    private InetAddress  destinationAddress;

    /**
     * IP protocl number.
     */
    private byte  protocol;

    /**
     * Time-to-live value.
     */
    private byte  timeToLive = DEFAULT_TTL;

    /**
     * DSCP value.
     */
    private byte  dscp;

    /**
     * Construct a new instance.
     *
     * @param efc  An {@link EthernetFactory} instance.
     * @return  An {@link Inet4Factory} instance.
     */
    public static Inet4Factory newInstance(EthernetFactory efc) {
        Inet4Factory i4fc = new Inet4Factory();
        efc.setEtherType(EtherTypes.IPv4.shortValue()).setNextFactory(i4fc);

        return i4fc;
    }

    /**
     * Construct a new instance.
     *
     * @param efc  An {@link EthernetFactory} instance.
     * @param src  The source IP address.
     * @param dst  The destination IP address.
     * @return  An {@link Inet4Factory} instance.
     */
    public static Inet4Factory newInstance(EthernetFactory efc,
                                           InetAddress src, InetAddress dst) {
        Inet4Factory i4fc = new Inet4Factory(src, dst);
        efc.setEtherType(EtherTypes.IPv4.shortValue()).setNextFactory(i4fc);

        return i4fc;
    }

    /**
     * Construct a new instance that indicates an IPv4 packet.
     */
    Inet4Factory() {}

    /**
     * Construct a new instance that indicates an IPv4 packet.
     *
     * @param src  The source IP address.
     * @param dst  The destination IP address.
     */
    Inet4Factory(InetAddress src, InetAddress dst) {
        setSourceAddress(src);
        setDestinationAddress(dst);
    }

    /**
     * Return the source IP address.
     *
     * @return  An {@link InetAddress} or {@code null}.
     */
    public InetAddress getSourceAddress() {
        return sourceAddress;
    }

    /**
     * Return the destination IP address.
     *
     * @return  An {@link InetAddress} or {@code null}.
     */
    public InetAddress getDestinationAddress() {
        return destinationAddress;
    }

    /**
     * Return the IP protocol number.
     *
     * @return  IP protocol number.
     */
    public byte getProtocol() {
        return protocol;
    }

    /**
     * Return the time-to-live value.
     *
     * @return  TTL value.
     */
    public byte getTimeToLive() {
        return timeToLive;
    }

    /**
     * Return the DSCP value.
     *
     * @return  DSCP value.
     */
    public byte getDscp() {
        return dscp;
    }

    /**
     * Set the source IP address.
     *
     * @param ip  An {@link InetAddress}.
     * @return  This instance.
     */
    public Inet4Factory setSourceAddress(InetAddress ip) {
        if (ip != null) {
            assertTrue(ip instanceof Inet4Address);
        }
        sourceAddress = ip;
        return this;
    }

    /**
     * Set the destination IP address.
     *
     * @param ip  An {@link InetAddress}.
     * @return  This instance.
     */
    public Inet4Factory setDestinationAddress(InetAddress ip) {
        if (ip != null) {
            assertTrue(ip instanceof Inet4Address);
        }
        destinationAddress = ip;
        return this;
    }

    /**
     * Set the IP protocol number.
     *
     * @param proto  IP protocol number.
     * @return  This instance.
     */
    public Inet4Factory setProtocol(byte proto) {
        protocol = proto;
        return this;
    }

    /**
     * Set the time-to-live value.
     *
     * @param ttl  TTL value.
     * @return  This instance.
     */
    public Inet4Factory setTimeToLive(byte ttl) {
        timeToLive = ttl;
        return this;
    }

    /**
     * Set the DSCP value.
     *
     * @param d  DSCP value.
     * @return   This instance.
     */
    public Inet4Factory setDscp(byte d) {
        dscp = d;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Packet createPacket() {
        IPv4 ip = new IPv4().setTtl(timeToLive).setDiffServ(dscp).
            setSourceAddress(sourceAddress).
            setDestinationAddress(destinationAddress).
            setProtocol(protocol);

        return ip;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void verify(Packet packet) {
        assertTrue(packet instanceof IPv4);
        IPv4 ip = (IPv4)packet;

        int srcInet = toInteger(sourceAddress);
        int dstInet = toInteger(destinationAddress);
        assertEquals(srcInet, ip.getSourceAddress());
        assertEquals(dstInet, ip.getDestinationAddress());
        assertEquals(protocol, ip.getProtocol());
        assertEquals(timeToLive, ip.getTtl());
        assertEquals(dscp, ip.getDiffServ());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    int initMatch(MatchBuilder builder, Set<FlowMatchType> types) {
        int ipCount = 0;
        int v4Count = 0;
        IpMatchBuilder imb = new IpMatchBuilder();
        Ipv4MatchBuilder i4mb = new Ipv4MatchBuilder();

        if (types.contains(FlowMatchType.IP_SRC)) {
            i4mb.setIpv4Source(toIpv4Prefix(sourceAddress));
            v4Count++;
        }
        if (types.contains(FlowMatchType.IP_DST)) {
            i4mb.setIpv4Destination(toIpv4Prefix(destinationAddress));
            v4Count++;
        }
        if (types.contains(FlowMatchType.IP_PROTO)) {
            imb.setIpProtocol(toShort(protocol));
            ipCount++;
        }
        if (types.contains(FlowMatchType.IP_DSCP)) {
            imb.setIpDscp(new Dscp(toShort(dscp)));
            ipCount++;
        }

        if (ipCount > 0) {
            builder.setIpMatch(imb.build());
        }
        if (v4Count > 0) {
            builder.setLayer3Match(i4mb.build());
        }

        return ipCount + v4Count;
    }

    /**
     * Convert an IPv4 address into an integer value.
     *
     * @param ip  An {@link InetAddress} instance.
     * @return    An integer value.
     */
    private int toInteger(InetAddress ip) {
        return NumberUtils.toInteger(ip.getAddress());
    }

    // Object

    /**
     * {@inheritDoc}
     */
    @Override
    public Inet4Factory clone() {
        return (Inet4Factory)super.clone();
    }
}
