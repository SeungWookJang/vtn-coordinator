#!/usr/bin/python

#
# Copyright (c) 2014 NEC Corporation
# All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this
# distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
#

import requests, json, collections, time, controller
import vtn_testconfig, vtn_vbr, vbrif_portmap, vtn_vterm, vtermif_portmap
import flowfilter, flowlistentry
import resp_code

CONTROLLERDATA=vtn_testconfig.CONTROLLERDATA

def test_vtermif_flowfilter_audit():
  print "CREATE Controller"
  retval = controller.add_controller_ex('ControllerFirst')
  if retval != 0:
    print "Controller Create Failed"
    exit(1)

  print "TEST 1 : VTERMIF->FLOWFILTER TEST"
  # Delay for AUDIT
  retval=controller.wait_until_state('ControllerFirst', "up")
  if retval != 0:
    print "Controller state check Failed"
    exit(1)

  retval=vtn_vbr.create_vtn('VtnOne')
  if retval != 0:
    print "VTN Create Failed"
    exit(1)

  retval=vtn_vbr.create_vbr('VtnOne','VbrOne','ControllerFirst')
  if retval != 0:
    print "VTN Create Failed"
    exit(1)

  retval=vbrif_portmap.create_vbrif('VtnOne','VbrOne','VbrIfOne')
  if retval != 0:
    print "VBRIF Create Failed"
    exit(1)

  retval=vtn_vbr.create_vbr('VtnOne','VbrTwo','ControllerFirst')
  if retval != 0:
    print "VTN Create Failed"
    exit(1)

  retval=vbrif_portmap.create_vbrif('VtnOne','VbrTwo','VbrIfTwo')
  if retval != 0:
    print "VBRIF Create Failed"
    exit(1)

  retval=vtn_vbr.validate_vtn_at_controller('VtnOne','ControllerFirst')
  if retval != 0:
    print "VTN Validate Failed"
    exit(1)

  retval=vtn_vbr.validate_vbr_at_controller('VtnOne','VbrOne','ControllerFirst')
  if retval != 0:
    print "VBR Validate Failed"
    exit(1)

  retval=vbrif_portmap.validate_vbrif_at_controller('VtnOne','VbrOne','VbrIfOne','ControllerFirst')
  if retval != 0:
    print "After Create VBRIF Validate Failed"
    exit(1)

  retval=vbrif_portmap.create_portmap('VtnOne','VbrOne','VbrIfOne');
  if retval != 0:
    print "Portmap Create Failed"
    exit(1)

  retval=vbrif_portmap.create_portmap('VtnOne','VbrTwo','VbrIfTwo');
  if retval != 0:
    print "Portmap Create Failed"
    exit(1)

  retval = vtn_vterm.create_vterm('VtnOne','VTermOne','ControllerFirst')
  if retval != 0:
    print "VTERM Create Failed"
    exit(1)

  retval = vtermif_portmap.create_vtermif('VtnOne','VTermOne','VTermIfOne')
  if retval != 0:
    print "VTERMIF Create Failed"
    exit(1)
    retval=flowlistentry.create_flowlist('FlowlistOne')

  retval = vtermif_portmap.create_portmap('VtnOne','VTermOne','VTermIfOne');
  if retval != 0:
    print "Portmap Create Failed"
    exit(1)

  retval=flowlistentry.create_flowlist('FlowlistOne')
  if retval != 0:
    print "FlowList Create Failed"
    exit(1)

  retval=flowlistentry.create_flowlistentry('FlowlistOne', 'FlowlistentryOne','ControllerFirst')
  if retval != 0:
    print "FlowlistEntry Create Failed"
    exit(1)

  retval=flowfilter.create_flowfilter('VtnOne|VTermOne|VbrIfOne|VTermIfOne', 'FlowfilterOne')
  if retval != 0:
    print "VTERMFlowFilter Create Failed"
    exit(1)

  print "****UPDATE Controller IP to invalid****"
  test_invalid_ipaddr= vtn_testconfig.ReadValues(CONTROLLERDATA,'ControllerFirst')['invalid_ipaddr']
  retval = controller.update_controller_ex('ControllerFirst',ipaddr=test_invalid_ipaddr)
  if retval != 0:
    print "controller invalid_ip update failed"
    exit(1)
  # Delay for AUDIT
  retval = controller.wait_until_state('ControllerFirst',"down")
  if retval != 0:
    print "controller state change failed"
    exit(1)

  retval=flowfilter.create_flowfilter_entry('VtnOne|VTermOne|VbrIfOne|VTermIfOne', 'FlowfilterOne')
  if retval != 0:
    print "VTERMFlowFilterEntry Create Failed"
    exit(1)

  retval=flowfilter.validate_flowfilter_entry('VtnOne|VTermOne|VbrIfOne|VTermIfOne', 'FlowfilterOne', presence='yes', position=0)
  if retval != 0:
    print "VTERMFlowFilterEntry Validation at Co-ordinator Failed "
    exit(1)

  print "****UPDATE Controller IP to Valid****"
  test_controller_ipaddr= vtn_testconfig.ReadValues(CONTROLLERDATA,'ControllerFirst')['ipaddr']
  retval = controller.update_controller_ex('ControllerFirst',ipaddr=test_controller_ipaddr)
  if retval != 0:
    print "controller valid_ip update failed"
    exit(1)
  # Delay for AUDIT
  retval = controller.wait_until_state('ControllerFirst',"up")
  if retval != 0:
    print "controller state change failed"
    exit(1)

  retval=flowfilter.validate_flowfilter_at_controller('VtnOne|VTermOne|VbrIfOne|VTermIfOne', 'ControllerFirst', 'FlowfilterOne', presence='yes', position=0)
  if retval != 0:
    print "FlowFilter validation at Controller Failed"
    exit(1)

  retval=flowfilter.delete_flowfilter_entry('VtnOne|VTermOne|VbrIfOne|VTermIfOne', 'FlowfilterOne')
  if retval != 0:
    print "VTERMFlowFilterEntry deletete Failed"
    exit(1)

  retval=flowfilter.delete_flowfilter('VtnOne|VTermOne|VbrIfOne|VTermIfOne', 'FlowfilterOne')
  if retval != 0:
    print "VTERMFlowFilter deletete Failed"
    exit(1)

  retval=flowlistentry.delete_flowlistentry('FlowlistOne', 'FlowlistentryOne')
  if retval != 0:
    print "FlowilistEntry deletete Failed"
    exit(1)

  retval=flowlistentry.delete_flowlist('FlowlistOne')
  if retval != 0:
    print "Flowilist deletete Failed"
    exit(1)

  retval=flowfilter.validate_flowfilter_at_controller('VtnOne|VTermOne|VbrIfOne|VTermIfOne', 'ControllerFirst', 'FlowfilterOne', presence='no', position=0)
  if retval != 0:
    print "FlowFilter validation Failed after deleting"
    exit(1)

  retval=vtn_vbr.delete_vtn('VtnOne')
  if retval != 0:
    print "DELETE VTN Failed"
    exit(1)

  retval=vtn_vbr.validate_vtn_at_controller('VtnOne','ControllerFirst', presence='no')
  if retval != 0:
    print "VTN Validate Failed"
    exit(1)

  print "DELETE CONTROLLER"
  retval=controller.delete_controller_ex('ControllerFirst')
  if retval != 0:
     print "CONTROLLER delete failed"
     exit(1)
  print "VTERM->FLOWFILTER AUDIT TEST SUCCESS"

# Main Block
if __name__ == '__main__':
    print '*****VTERM FLOWFILTER TESTS******'
    test_vtermif_flowfilter_audit()
else:
    print 'VTERM_FLOWFILTER LOADED AS MODULE'
