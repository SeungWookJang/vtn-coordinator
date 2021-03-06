/**
 * Copyright (c) 2014 NEC Corporation
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vtn.app.run.config.rest.response.beans;

import org.opendaylight.vtn.app.run.config.json.annotations.JsonElement;
import org.opendaylight.vtn.app.run.config.json.annotations.JsonObject;

/**
 * InetAddressDetailsList - Bean Representaion for Array of InetAddressDetails
 * object from the JSON Response.
 *
 */
@JsonObject
public class InetAddressDetailsList {

    /**
     * Address attribute for InetAddressDetails
     */
    @JsonElement(name = "address")
    private String address = null;

    public InetAddressDetailsList() {
    }

    /**
     * getAddress - function to get the address values for this object.
     *
     * @return {@link String}
     */
    public String getAddress() {
        return address;
    }

    /**
     * setAddress - function to set the address values for this object.
     *
     * @param address
     */
    public void setAddress(String address) {
        this.address = address;
    }

    /**
     * String representation of the object.
     *
     */
    @Override
    public String toString() {
        return "Address[address:" + address + "]";
    }
}
