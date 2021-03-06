/*
 * Copyright (c) 2014 NEC Corporation
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vtn.manager.internal.cluster;

import java.util.HashSet;

import org.junit.Test;

import org.opendaylight.vtn.manager.VTNException;
import org.opendaylight.vtn.manager.flow.action.SetIcmpTypeAction;

import org.opendaylight.vtn.manager.internal.TestBase;

import org.opendaylight.controller.sal.utils.StatusCode;

/**
 * JUnit test for {@link SetIcmpTypeActionImpl}.
 */
public class SetIcmpTypeActionImplTest extends TestBase {
    /**
     * Test case for getter methods.
     */
    @Test
    public void testGetter() {
        short[] types = {0, 1, 2, 10, 20, 50, 100, 120, 160, 200, 254, 255};
        for (short type: types) {
            SetIcmpTypeAction act = new SetIcmpTypeAction(type);
            try {
                SetIcmpTypeActionImpl impl = new SetIcmpTypeActionImpl(act);
                assertEquals(act, impl.getFlowAction());
            } catch (Exception e) {
                unexpected(e);
            }
        }

        // null action.
        try {
            new SetIcmpTypeActionImpl(null);
            unexpected();
        } catch (VTNException e) {
            assertEquals(StatusCode.BADREQUEST, e.getStatus().getCode());
        }

        // Invalid ICMP type.
        short[] invalid = {Short.MIN_VALUE, -100, -10, -2, -1,
                           256, 257, 300, 500, 1000, 3000, Short.MAX_VALUE};
        for (short type: invalid) {
            SetIcmpTypeAction act = new SetIcmpTypeAction(type);
            try {
                SetIcmpTypeActionImpl impl = new SetIcmpTypeActionImpl(act);
                unexpected();
            } catch (VTNException e) {
                assertEquals(StatusCode.BADREQUEST, e.getStatus().getCode());
            }
        }
    }

    /**
     * Test case for {@link SetIcmpTypeActionImpl#equals(Object)} and
     * {@link SetIcmpTypeActionImpl#hashCode()}.
     */
    @Test
    public void testEquals() {
        HashSet<Object> set = new HashSet<Object>();
        short[] types = {0, 1, 2, 10, 20, 50, 100, 120, 160, 200, 254, 255};
        for (short type: types) {
            SetIcmpTypeAction act1 = new SetIcmpTypeAction(type);
            SetIcmpTypeAction act2 = new SetIcmpTypeAction(type);
            try {
                SetIcmpTypeActionImpl impl1 = new SetIcmpTypeActionImpl(act1);
                SetIcmpTypeActionImpl impl2 = new SetIcmpTypeActionImpl(act2);
                testEquals(set, impl1, impl2);
            } catch (Exception e) {
                unexpected(e);
            }
        }

        assertEquals(types.length, set.size());
    }

    /**
     * Test case for {@link SetIcmpTypeActionImpl#toString()}.
     */
    @Test
    public void testToString() {
        short[] types = {0, 1, 2, 10, 20, 50, 100, 120, 160, 200, 254, 255};
        for (short type: types) {
            SetIcmpTypeAction act = new SetIcmpTypeAction(type);
            try {
                SetIcmpTypeActionImpl impl = new SetIcmpTypeActionImpl(act);
                assertEquals("SetIcmpTypeActionImpl[type=" + type + "]",
                             impl.toString());
            } catch (Exception e) {
                unexpected(e);
            }
        }
    }

    /**
     * Ensure that {@link SetIcmpTypeActionImpl} is serializable.
     */
    @Test
    public void testSerialize() {
        short[] types = {0, 1, 2, 10, 20, 50, 100, 120, 160, 200, 254, 255};
        for (short type: types) {
            SetIcmpTypeAction act = new SetIcmpTypeAction(type);
            try {
                SetIcmpTypeActionImpl impl = new SetIcmpTypeActionImpl(act);
                serializeTest(impl);
            } catch (Exception e) {
                unexpected(e);
            }
        }
    }
}
