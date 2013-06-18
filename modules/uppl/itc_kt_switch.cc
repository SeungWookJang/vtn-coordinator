/*
 * Copyright (c) 2012-2013 NEC Corporation
 * All rights reserved.
 * 
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */

/**
 * @brief   KT Switch implementation
 * @file    itc_kt_switch.cc
 *
 */

#include "itc_kt_switch.hh"
#include "itc_kt_controller.hh"
#include "itc_kt_ctr_domain.hh"
#include "itc_kt_logicalport.hh"
#include "itc_kt_port.hh"
#include "itc_kt_link.hh"
#include "odbcm_utils.hh"
using unc::uppl::PhysicalLayer;
using unc::uppl::ODBCMUtils;

/** Constructor
 * * @Description : This function instantiates parent and child key types for
 * kt_switch
 * * * @param[in] : None
 * * * @return    : None
 * */
Kt_Switch::Kt_Switch() {
  parent = NULL;
  for (int i = 0; i < KT_SWITCH_CHILD_COUNT; ++i) {
    child[i] = NULL;
  }
  // Populate Structure to be used for syntax validation
  Fill_Attr_Syntax_Map();
}

/** Destructor
 * * @Description : This function clears the parent and child key types for
 * instances for kt_switch
 * * * @param[in] : None
 * * * @return    : None
 * */
Kt_Switch::~Kt_Switch() {
  // Delete parent object
  if (parent != NULL) {
    delete parent;
    parent = NULL;
  }
  // Delete all child objects
  for (int i = 0; i < KT_SWITCH_CHILD_COUNT; ++i) {
    if (child[i] != NULL) {
      delete child[i];
      child[i] = NULL;
    }
  }
}

/** GetChildClassPointer
 * * @Description : This function creates a new child class instance
 * of KtSwitch based on index passed.
 * * @param[in] : KtSwitchChildClass - Child class index enum
 * * @return    : Kt_Base* - child class pointer
 * */
Kt_Base* Kt_Switch::GetChildClassPointer(KtSwitchChildClass KIndex) {
  switch (KIndex) {
    case KIdxPort : {
      if (child[KIndex] == NULL) {
        child[KIndex] = new Kt_Port();
      }
      break;
    }
    default: {
      pfc_log_info("Invalid index %d passed to GetChildClassPointer()",
                   KIndex);
      PFC_ASSERT(PFC_FALSE);
      return NULL;
    }
  }
  return child[KIndex];
}

/** DeleteKeyInstance
 * * @Description : This function deletes the given  instance of KT_Switch
 * * @param[in] : key_struct - key for the switch instance
 * data_type - UNC_DT_* , delete only allowed in STATE
 * * @return    : UPPL_RC_SUCCESS is returned when the delete
 * is done successfully.
 * * UPPL_RC_ERR_* is returned when the delete is error
 * */
UpplReturnCode Kt_Switch::DeleteKeyInstance(void* key_struct,
                                            uint32_t data_type,
                                            uint32_t key_type) {
  UpplReturnCode delete_status = UPPL_RC_SUCCESS;
  // Check whether operation is allowed on the given DT type
  // if is_internal is set as true, the notification is received from driver
  if (((unc_keytype_datatype_t)data_type != UNC_DT_STATE) &&
      ((unc_keytype_datatype_t)data_type != UNC_DT_IMPORT)) {
    pfc_log_error("Delete operation is provided on unsupported data type");
    return UPPL_RC_ERR_OPERATION_NOT_ALLOWED;
  }

  key_switch_t *obj_key_switch = reinterpret_cast<key_switch_t*>(key_struct);
  string switch_id = (const char*)obj_key_switch->switch_id;
  string controller_name = (const char*)obj_key_switch->ctr_key.controller_name;
  vector<val_switch_st_t> vect_val_switch;
  vector<key_switch_t> vect_switch_id;

  if (switch_id.length() == 0) {
    delete_status = ReadBulkInternal(key_struct, NULL,
                                     data_type, UPPL_MAX_REP_CT,
                                     vect_val_switch, vect_switch_id);
  } else {
    vect_switch_id.push_back(*obj_key_switch);
  }

  // Delete child classes and then delete switch
  vector<key_switch_t> ::iterator switch_iter = vect_switch_id.begin();
  for (; switch_iter != vect_switch_id.end(); ++switch_iter) {
    string sw_id = (const char*)(*switch_iter).switch_id;
    pfc_log_debug("Iterating port entries for switch %s...", sw_id.c_str());
    for (int child_class = 0;
        child_class < KT_SWITCH_CHILD_COUNT;
        ++child_class) {
      // Filling key_struct corresponding to the key type
      void *child_key_struct = getChildKeyStruct(child_class,
                                                 switch_id,
                                                 controller_name);
      child[child_class] = GetChildClassPointer(
          (KtSwitchChildClass)child_class);
      if (child[child_class] == NULL) {
        // Free child key struct
        FreeChildKeyStruct(child_class, child_key_struct);
        continue;
      }
      delete_status = child[child_class]->DeleteKeyInstance(child_key_struct,
                                                            data_type,
                                                            UNC_KT_PORT);
      delete child[child_class];
      child[child_class] = NULL;
      FreeChildKeyStruct(child_class, child_key_struct);
      if (delete_status == UPPL_RC_ERR_NO_SUCH_INSTANCE) {
        pfc_log_debug("No child instance for switch");
      }
      if (delete_status != UPPL_RC_ERR_NO_SUCH_INSTANCE &&
          delete_status != UPPL_RC_SUCCESS) {
        // child delete , failed so return error
        pfc_log_error("Delete failed for child with %d with error %d",
                      child_class, delete_status);
        delete_status = UPPL_RC_ERR_CFG_SEMANTIC;
        break;
      }
    }
  }
  // Driver Send kt_switch delete to Physical
  // Switcg delete corresponding LogicalPort
  Kt_LogicalPort logical_port_obj;
  key_logical_port_t logical_port_key_obj;
  val_logical_port_st_t logical_port_val_obj;

  memset(logical_port_key_obj.domain_key.domain_name, '\0',
         sizeof(logical_port_key_obj.domain_key.domain_name));

  memset(logical_port_key_obj.port_id, 0,
         sizeof(logical_port_key_obj.port_id));

  memset(logical_port_val_obj.logical_port.valid, UNC_VF_INVALID,
         sizeof(logical_port_val_obj.logical_port.valid));

  memset(logical_port_val_obj.valid, UNC_VF_INVALID,
         sizeof(logical_port_val_obj.valid));

  memcpy(logical_port_key_obj.domain_key.ctr_key.controller_name,
         controller_name.c_str(),
         (controller_name.length())+1);

  memcpy(logical_port_val_obj.logical_port.switch_id,
         switch_id.c_str(),
         (switch_id.length())+1);

  logical_port_val_obj.valid[kIdxLogicalPortSt] = UNC_VF_VALID;
  logical_port_val_obj.logical_port.valid[kIdxLogicalPortSwitchId] =
      UNC_VF_VALID;

  // call read internal
  vector<void *> key_val;
  vector<void *> val_struct;
  key_val.push_back(reinterpret_cast<void *>(&logical_port_key_obj));
  val_struct.push_back(reinterpret_cast<void *>(&logical_port_val_obj));
  UpplReturnCode lp_read_status = logical_port_obj.ReadInternal(
      key_val, val_struct, UNC_DT_STATE, UNC_OP_READ);
  if (lp_read_status == UPPL_RC_SUCCESS) {
    // form key struct with all required primary keys and call delete
    logical_port_obj.DeleteKeyInstance(
        key_val[0],
        UNC_DT_STATE,
        UNC_KT_LOGICAL_PORT);
    // Clear key and value struct
    key_logical_port_t *key_log_port = reinterpret_cast<key_logical_port_t*>
    (key_val[0]);
    if (key_log_port != NULL) {
      delete key_log_port;
      key_log_port = NULL;
    }
    val_logical_port_t *val_log_port = reinterpret_cast<val_logical_port_t*>
    (val_struct[0]);
    if (val_log_port != NULL) {
      delete val_log_port;
      val_log_port = NULL;
    }
  }

  // Structure used to send request to ODBC
  DBTableSchema kt_switch_dbtableschema;

  // Construct Primary key list
  vector<string> vect_prim_keys;
  vect_prim_keys.push_back(CTR_NAME);

  // Construct TableAttrSchema structure
  // TableAttrSchema holds table_name, primary key, attr_name
  vector<TableAttrSchema> vect_table_attr_schema;
  TableAttrSchema kt_switch_table_attr_schema;
  list< vector<TableAttrSchema> > row_list;
  // controller_name
  PhyUtil::FillDbSchema(CTR_NAME, controller_name,
                        controller_name.length(), DATATYPE_UINT8_ARRAY_32,
                        vect_table_attr_schema);

  if (switch_id.length() > 0) {
    // switch_id
    vect_prim_keys.push_back(SWITCH_ID);
    PhyUtil::FillDbSchema(SWITCH_ID, switch_id,
                          switch_id.length(), DATATYPE_UINT8_ARRAY_256,
                          vect_table_attr_schema);
  }

  // Send request to ODBC for switch_table delete
  kt_switch_dbtableschema.set_table_name(UPPL_SWITCH_TABLE);
  kt_switch_dbtableschema.set_primary_keys(vect_prim_keys);
  row_list.push_back(vect_table_attr_schema);
  kt_switch_dbtableschema.set_row_list(row_list);
  PhysicalLayer *physical_layer = PhysicalLayer::get_instance();
  ODBCM_RC_STATUS delete_db_status = physical_layer->get_odbc_manager()-> \
      DeleteOneRow((unc_keytype_datatype_t)data_type,
                   kt_switch_dbtableschema);
  if (delete_db_status != ODBCM_RC_SUCCESS) {
    if (delete_db_status == ODBCM_RC_CONNECTION_ERROR) {
      // log fatal error to log daemon
      pfc_log_fatal("DB connection not available or cannot access DB");
      delete_status = UPPL_RC_ERR_DB_ACCESS;
    } else {
      // log error to log daemon
      pfc_log_error("Delete operation has failed in switch common table");
      delete_status = UPPL_RC_ERR_DB_DELETE;
    }
  }
  return delete_status;
}

/* * ReadInternal
 * * @Description : This function reads the given instance of Kt Switch
 * * @param[in] : key_struct - key for kt switch instance
 * value_struct - value for kt switch instance
 * data_type - UNC_DT_* , read allowed in STATE
 * * @return    : UPPL_RC_SUCCESS is returned when the response is added to
 * ipc session successfully
 * UPPL_RC_ERR_* is returned when ipc response could not be added to sess.
 * */
UpplReturnCode Kt_Switch::ReadInternal(vector<void *> &key_val,
                                       vector<void *> &val_struct,
                                       uint32_t data_type,
                                       uint32_t operation_type) {
  pfc_log_debug("Inside ReadInternal of KT_SWITCH");
  vector<key_switch_t> vect_switch_id;
  vector<val_switch_st_t> vect_val_switch_st;
  uint32_t max_rep_ct = 1;
  if (operation_type != UNC_OP_READ) {
    max_rep_ct = UPPL_MAX_REP_CT;
  }
  // Get read response from database
  void *key_struct = key_val[0];
  void *void_val_struct = NULL;
  if (!val_struct.empty()) {
    void_val_struct = val_struct[0];
  }
  UpplReturnCode read_status = ReadSwitchValFromDB(key_struct,
                                                   void_val_struct,
                                                   data_type,
                                                   operation_type,
                                                   max_rep_ct,
                                                   vect_val_switch_st,
                                                   vect_switch_id);
  key_val.clear();
  val_struct.clear();
  if (read_status != UPPL_RC_SUCCESS) {
    pfc_log_error("Read operation has failed");
  } else {
    for (unsigned int iIndex = 0 ; iIndex < vect_val_switch_st.size();
        ++iIndex) {
      key_switch_t *key_switch = new key_switch_t(vect_switch_id[iIndex]);
      val_switch_st_t *val_switch = new val_switch_st_t
          (vect_val_switch_st[iIndex]);
      val_struct.push_back(reinterpret_cast<void *>(val_switch));
      key_val.push_back(reinterpret_cast<void *>(key_switch));
    }
  }
  return read_status;
}

/** ReadBulk
 * * @Description : This function reads the max_rep_ct number of instances
 *  of the KT_Switch
 * * @param[in] :
 * key_struct - key for switch instance
 * data_type - UNC_DT_* , ReadBulk supported for STATE
 * option1,option2 - specifies any additional condition for read operation
 * max_rep_ct - specifies no of rows to be returned
 * parent_call - indicates whether parent has called this read bulk
 * is_read_next - indicates whether this function is invoked from read_next
 * * @return    : UPPL_RC_SUCCESS is returned when the response is added to
 * ipc session successfully
 * UPPL_RC_ERR_* is returned when ipc response could not be added to sess
 * */
UpplReturnCode Kt_Switch::ReadBulk(void* key_struct,
                                   uint32_t data_type,
                                   uint32_t option1,
                                   uint32_t option2,
                                   uint32_t &max_rep_ct,
                                   int child_index,
                                   pfc_bool_t parent_call,
                                   pfc_bool_t is_read_next) {
  pfc_log_info("Processing ReadBulk of Kt_Switch");
  UpplReturnCode read_status = UPPL_RC_SUCCESS;
  key_switch_t obj_key_switch =
      *(reinterpret_cast<key_switch_t*>(key_struct));
  if ((unc_keytype_datatype_t)data_type != UNC_DT_STATE) {
    // Not supported
    pfc_log_debug("ReadBulk operation is not allowed in %d data type",
                  data_type);
    read_status = UPPL_RC_ERR_OPERATION_NOT_ALLOWED;
    return read_status;
  }
  string str_switch_id =
      reinterpret_cast<char *>(obj_key_switch.switch_id);
  string str_controller_name =
      reinterpret_cast<char *>(obj_key_switch.ctr_key.controller_name);
  pfc_bool_t switch_exists = PFC_FALSE;
  vector<val_switch_st_t> vect_val_switch;
  vector<key_switch_t> vect_switch_id;
  if (max_rep_ct == 0) {
    pfc_log_info("max_rep_ct is 0");
    return UPPL_RC_SUCCESS;
  }
  // Check for child call
  if (child_index == -1 &&
      !str_switch_id.empty()) {
    // Check for key existence
    vector<string> vect_key_value;
    vect_key_value.push_back(str_controller_name);
    vect_key_value.push_back(str_switch_id);
    UpplReturnCode key_exist_status = IsKeyExists(
        (unc_keytype_datatype_t)data_type,
        vect_key_value);
    if (key_exist_status == UPPL_RC_SUCCESS) {
      switch_exists = PFC_TRUE;
    }
  }
  void *val_struct = NULL;
  // Read the switch values based on given key structure
  read_status = ReadBulkInternal(key_struct,
                                 val_struct,
                                 data_type,
                                 max_rep_ct,
                                 vect_val_switch,
                                 vect_switch_id);
  if (switch_exists == PFC_TRUE) {
    vect_switch_id.insert(vect_switch_id.begin(), obj_key_switch);
    val_switch_st_t dummy_val_switch;
    vect_val_switch.insert(vect_val_switch.begin(), dummy_val_switch);
  }
  if (read_status == UPPL_RC_SUCCESS ||
      switch_exists == PFC_TRUE) {
    PhysicalCore *physical_core = PhysicalLayer::get_instance()->
        get_physical_core();
    InternalTransactionCoordinator *itc_trans  =
        physical_core->get_internal_transaction_coordinator();
    // For each switch , read the child's attributes
    vector<val_switch_st_t> ::iterator vect_iter = vect_val_switch.begin();
    vector<key_switch_t> ::iterator switch_iter = vect_switch_id.begin();
    for (; switch_iter != vect_switch_id.end(); ++switch_iter,
    ++vect_iter) {
      pfc_log_debug("Iterating switch entries...");
      string fKey = (const char*)(*switch_iter).switch_id;
      // int err = 0;
      if (switch_exists == PFC_FALSE) {
        pfc_log_debug("Adding switch - '%s' to session", fKey.c_str());
        key_switch_t *key_buffer = new key_switch_t(*switch_iter);
        BulkReadBuffer obj_key_buffer = {
            UNC_KT_SWITCH, IS_KEY,
            reinterpret_cast<void *>(key_buffer)
        };
        itc_trans->AddToBuffer(obj_key_buffer);
        val_switch_st_t *val_buffer = new val_switch_st_t(*vect_iter);
        BulkReadBuffer obj_value_buffer = {
            UNC_KT_SWITCH, IS_STATE_VALUE,
            reinterpret_cast<void *>(val_buffer)
        };
        itc_trans->AddToBuffer(obj_value_buffer);
        BulkReadBuffer obj_sep_buffer = {
            UNC_KT_SWITCH, IS_SEPARATOR, NULL
        };
        itc_trans->AddToBuffer(obj_sep_buffer);
        --max_rep_ct;
        if (max_rep_ct == 0) {
          return UPPL_RC_SUCCESS;
        }
      }
      switch_exists = PFC_FALSE;
      int st_child_index =
          (child_index >= 0 && child_index <= KIdxPort)
          ? child_index+1 : KIdxPort;
      for (int kIdx = st_child_index; kIdx <= KIdxPort; ++kIdx) {
        pfc_log_debug("Switch Iterating child %d", kIdx);
        void *child_key_struct = getChildKeyStruct(kIdx, fKey,
                                                   str_controller_name);
        child[kIdx] = GetChildClassPointer((KtSwitchChildClass)kIdx);
        if (child[kIdx] == NULL) {
          // Free child key struct
          FreeChildKeyStruct(kIdx, child_key_struct);
          continue;
        }
        pfc_log_debug("Switch Calling child %d read bulk", kIdx);
        UpplReturnCode ch_read_status = child[kIdx]->ReadBulk(
            child_key_struct,
            data_type,
            option1,
            option2,
            max_rep_ct,
            -1,
            true,
            is_read_next);
        pfc_log_debug("ReadBulk response from child %d is %d",
                      kIdx, ch_read_status);
        delete child[kIdx];
        child[kIdx] = NULL;
        FreeChildKeyStruct(kIdx, child_key_struct);
        if (max_rep_ct == 0) {
          pfc_log_debug("max_rep_ct reached zero, so returning");
          return UPPL_RC_SUCCESS;
        }
      }
      str_switch_id = fKey;
      // reset child index
      child_index = -1;
    }
  }
  if (max_rep_ct >0 && parent_call == false) {
    pfc_log_debug("max_rep_ct is %d and parent_call is %d, calling parent",
                  max_rep_ct, parent_call);
    // Filling key_struct corresponding to the key type
    pfc_log_debug("Calling parent Kt_Controller read bulk");
    Kt_Controller nextKin;
    key_ctr_t nextkin_key_struct;
    memset(nextkin_key_struct.controller_name,
           '\0',
           sizeof(nextkin_key_struct.controller_name));
    memcpy(nextkin_key_struct.controller_name,
           str_controller_name.c_str(),
           str_controller_name.length() +1);
    read_status = nextKin.ReadBulk(
        reinterpret_cast<void *>(&nextkin_key_struct),
        data_type,
        option1,
        option2,
        max_rep_ct,
        1,
        false,
        is_read_next);
    pfc_log_debug("read_status from next kin Kt_Ctr is %d", read_status);
    return UPPL_RC_SUCCESS;
  }
  pfc_log_debug("switch reached end of table");
  pfc_log_debug("read_status=%d", read_status);
  if (read_status == UPPL_RC_ERR_NO_SUCH_INSTANCE) {
    read_status = UPPL_RC_SUCCESS;
  }
  return read_status;
}

/** ReadBulkInternal
 * * @Description : This function reads bulk rows of KtSwitch in switch table
 * * of specified data type
 * * @param[in] : key_struct - key for switch instance
 * value_struct - value struct for kt switch instance
 * data_type - UNC_DT_*, Read supported for STATE
 * max_rep_ct - specified no of rows to be returned
 * vect_val_switch - indicated the fetched values from db of val_switch type
 * vect_switch_id - indicated the fetched switch id from db
 * * @return    : UPPL_RC_SUCCESS is returned when the response is added to
 * ipc session successfully
 * UPPL_RC_ERR_* is returned when ipc response could not be added to sess
 * */
UpplReturnCode Kt_Switch::ReadBulkInternal(
    void* key_struct,
    void* val_struct,
    uint32_t data_type,
    uint32_t max_rep_ct,
    vector<val_switch_st_t> &vect_val_switch,
    vector<key_switch_t> &vect_switch_id) {
  if (max_rep_ct <= 0) {
    return UPPL_RC_SUCCESS;
  }
  PhysicalLayer *physical_layer = PhysicalLayer::get_instance();
  UpplReturnCode read_status = UPPL_RC_SUCCESS;
  ODBCM_RC_STATUS read_db_status = ODBCM_RC_SUCCESS;
  DBTableSchema kt_switch_dbtableschema;
  // Populate DBSchema for switch_table
  void* old_value_struct;
  vector<ODBCMOperator> vect_key_operations;
  PopulateDBSchemaForKtTable(kt_switch_dbtableschema,
                             key_struct,
                             val_struct,
                             UNC_OP_READ_BULK, 0, 0,
                             vect_key_operations, old_value_struct);
  pfc_log_debug("Calling GetBulkRows");
  // Read rows from DB
  read_db_status = physical_layer->get_odbc_manager()-> \
      GetBulkRows((unc_keytype_datatype_t)data_type, max_rep_ct,
                  kt_switch_dbtableschema,
                  (unc_keytype_operation_t)UNC_OP_READ_BULK);
  pfc_log_debug("GetBulkRows return: %d", read_db_status);
  if (read_db_status == ODBCM_RC_RECORD_NOT_FOUND) {
    pfc_log_debug("No record found");
    read_status = UPPL_RC_ERR_NO_SUCH_INSTANCE;
    return read_status;
  } else if (read_db_status != ODBCM_RC_SUCCESS) {
    read_status = UPPL_RC_ERR_DB_GET;
    // log error to log daemon
    pfc_log_error("Read operation has failed with error %d", read_db_status);
    return read_status;
  }
  // From the values received from DB, populate val_switch structure
  FillSwitchValueStructure(kt_switch_dbtableschema,
                           vect_val_switch,
                           max_rep_ct,
                           UNC_OP_READ_BULK,
                           vect_switch_id);
  return read_status;
}

/** PerformSyntaxValidation
 * * @Description : This function performs syntax validation for KT_SWITCH
 * * @param[in] : key_struct - key for kt switch instance
 * value_struct - value for kt switch instance
 * operation - UNC_OP_*
 * data_type - UNC_DT_*
 * * @return    : UPPL_RC_SUCCESS is returned when the validation is successful
 * UPPL_RC_ERR_* is returned when validation is failure or associated error code
 * */
UpplReturnCode Kt_Switch::PerformSyntaxValidation(void* key_struct,
                                                  void* val_struct,
                                                  uint32_t operation,
                                                  uint32_t data_type) {
  pfc_log_info("Inside PerformSyntax Validation of KT_SWITCH");
  UpplReturnCode ret_code = UPPL_RC_SUCCESS;
  pfc_ipcresp_t mandatory = PFC_TRUE;

  // Validate Key Structure
  key_switch *key = reinterpret_cast<key_switch_t*>(key_struct);
  string value = reinterpret_cast<char*>(key->ctr_key.controller_name);
  IS_VALID_STRING_KEY(CTR_NAME, value, operation, ret_code, mandatory);
  if (ret_code != UPPL_RC_SUCCESS) {
    return UPPL_RC_ERR_CFG_SYNTAX;
  }

  value = reinterpret_cast<char*>(key->switch_id);
  IS_VALID_STRING_KEY(SWITCH_ID, value, operation, ret_code, mandatory);
  if (ret_code != UPPL_RC_SUCCESS) {
    return UPPL_RC_ERR_CFG_SYNTAX;
  }

  // Validate Value structure
  if (val_struct != NULL) {
    unsigned int valid_val = 0;
    // validate description
    val_switch *value_switch = reinterpret_cast<val_switch_t*>(val_struct);
    valid_val =
        PhyUtil::uint8touint(value_switch->valid[kIdxSwitchDescription]);
    string value = reinterpret_cast<char*>(value_switch->description);
    IS_VALID_STRING_VALUE(SWITCH_DESCRIPTION, value, operation,
                          valid_val, ret_code, mandatory);
    if (ret_code != UPPL_RC_SUCCESS) {
      return UPPL_RC_ERR_CFG_SYNTAX;
    }

    // validate model
    valid_val = PhyUtil::uint8touint(value_switch->valid[kIdxSwitchModel]);
    value = reinterpret_cast<char*>(value_switch->model);
    IS_VALID_STRING_VALUE(SWITCH_MODEL, value, operation,
                          valid_val, ret_code, mandatory);
    if (ret_code != UPPL_RC_SUCCESS) {
      return UPPL_RC_ERR_CFG_SYNTAX;
    }

    // validate IP Address
    valid_val = PhyUtil::uint8touint(value_switch->valid[kIdxSwitchIPAddress]);
    IS_VALID_IPV4_VALUE(SWITCH_IP_ADDRESS, value_switch->ip_address, operation,
                        valid_val, ret_code, mandatory);
    if (ret_code != UPPL_RC_SUCCESS) {
      return UPPL_RC_ERR_CFG_SYNTAX;
    }

    // validate ipv6 Address
    valid_val =
        PhyUtil::uint8touint(value_switch->valid[kIdxSwitchIPV6Address]);
    IS_VALID_IPV6_VALUE(SWITCH_IPV6_ADDRESS, value_switch->ipv6_address,
                        operation, valid_val, ret_code, mandatory);
    if (ret_code != UPPL_RC_SUCCESS) {
      return UPPL_RC_ERR_CFG_SYNTAX;
    }

    // validate admin_status
    valid_val =
        PhyUtil::uint8touint(value_switch->valid[kIdxSwitchAdminStatus]);
    IS_VALID_INT_VALUE(SWITCH_ADMIN_STATUS, value_switch->admin_status,
                       operation, valid_val, ret_code, mandatory);
    if (ret_code != UPPL_RC_SUCCESS) {
      return UPPL_RC_ERR_CFG_SYNTAX;
    }

    // validate domain Name
    valid_val = PhyUtil::uint8touint(value_switch->valid[kIdxSwitchDomainName]);
    value = reinterpret_cast<char*>(value_switch->domain_name);
    IS_VALID_STRING_VALUE(SWITCH_DOMAIN_NAME, value, operation,
                          valid_val, ret_code, mandatory);
    if (ret_code != UPPL_RC_SUCCESS) {
      return UPPL_RC_ERR_CFG_SYNTAX;
    }
  }
  return ret_code;
}

/** PerformSemanticValidation
 * * @Description : This function performs semantic validation
 *                  for UNC_KT_SWITCH
 * * @param[in] : key_struct - specifies key instance of KT_SWITCH
 * value_struct - specifies value of KT_SWITCH
 * operation - UNC_OP*
 * data_type - UNC_DT*
 * * @return    : UPPL_RC_SUCCESS if semantic validation is successful
 * or UPPL_RC_ERR_* if failed
 * */
UpplReturnCode Kt_Switch::PerformSemanticValidation(void* key_struct,
                                                    void* val_struct,
                                                    uint32_t operation,
                                                    uint32_t data_type) {
  UpplReturnCode status = UPPL_RC_SUCCESS;
  pfc_log_debug("Inside PerformSemanticValidation of KT_SWITCH");
  // Check whether the given instance of switch in DB
  key_switch_t *obj_key_switch = reinterpret_cast<key_switch_t*>(key_struct);
  string switch_id = (const char*)obj_key_switch->switch_id;
  string controller_name = (const char*)obj_key_switch->
      ctr_key.controller_name;
  vector<string> switch_vect_key_value;
  switch_vect_key_value.push_back(controller_name);
  switch_vect_key_value.push_back(switch_id);
  UpplReturnCode key_status = IsKeyExists((unc_keytype_datatype_t)data_type,
                                          switch_vect_key_value);
  pfc_log_debug("IsKeyExists status %d", key_status);

  // In case of create operation, key should not exist
  if (operation == UNC_OP_CREATE) {
    if (key_status == UPPL_RC_SUCCESS) {
      pfc_log_error("Key instance already exists");
      pfc_log_error("Hence create operation not allowed");
      status = UPPL_RC_ERR_INSTANCE_EXISTS;
    } else {
      pfc_log_info("key instance not exist create operation allowed");
    }

  } else if (operation == UNC_OP_UPDATE || operation == UNC_OP_DELETE ||
      operation == UNC_OP_READ) {
    // In case of update/delete/read operation, key should exist
    if (key_status != UPPL_RC_SUCCESS) {
      pfc_log_error("Key instance does not exist");
      pfc_log_error("Hence update/delete/read operation not allowed");
      status = UPPL_RC_ERR_NO_SUCH_INSTANCE;
    } else {
      pfc_log_info("key instance exist update/del/read operation allowed");
    }
  }

  if (operation == UNC_OP_CREATE && status == UPPL_RC_SUCCESS) {
    vector<string> parent_vect_key_value;
    parent_vect_key_value.push_back(controller_name);
    Kt_Controller KtObj;
    uint32_t parent_data_type = data_type;
    if (data_type == UNC_DT_IMPORT) {
      parent_data_type = UNC_DT_RUNNING;
    }
    UpplReturnCode parent_key_status = KtObj.IsKeyExists(
        (unc_keytype_datatype_t)parent_data_type, parent_vect_key_value);
    pfc_log_debug("Parent IsKeyExists status %d", parent_key_status);
    if (parent_key_status != UPPL_RC_SUCCESS) {
      status = UPPL_RC_ERR_PARENT_DOES_NOT_EXIST;
    }
  }
  pfc_log_debug("status of SemanticValidation before returning=%d", status);
  return status;
}

/** HandleDriverAlarms
 * * @Description : This function processes the alarm notification
 * received from driver
 * * @param[in] : alarm type - contains type to indicate PATH_FAULT alarm
 * operation - contains UNC_OP_CREATE or UNC_OP_DELETE
 * key_struct - indicate key instance of KT_SWITCH
 * value_struct - indicates the alarm value structure
 * * @return : UPPL_RC_SUCCESS if alarm is handled successfully or
 * UPPL_RC_ERR*
 * */
UpplReturnCode Kt_Switch::HandleDriverAlarms(uint32_t data_type,
                                             uint32_t alarm_type,
                                             uint32_t oper_type,
                                             void* key_struct,
                                             void* val_struct) {
  UpplReturnCode status = UPPL_RC_SUCCESS;
  key_switch_t *obj_key_switch =
      reinterpret_cast<key_switch_t*>(key_struct);
  string controller_name =
      (const char*)obj_key_switch->ctr_key.controller_name;
  string switch_id = (const char*)obj_key_switch->switch_id;
  memcpy(obj_key_switch->switch_id,
         switch_id.c_str(),
         switch_id.length()+1);
  memcpy(obj_key_switch->ctr_key.controller_name,
         controller_name.c_str(),
         controller_name.length()+1);
  pfc_log_info("alarm sent by driver is: %d", alarm_type);
  pfc_log_info("operation type: %d", oper_type);
  // Read old_alarm_status from db
  uint64_t alarm_status_db = 0;
  UpplReturnCode read_alarm_status = GetAlarmStatus(data_type,
                                                    key_struct,
                                                    alarm_status_db);
  if (read_alarm_status == UPPL_RC_SUCCESS) {
    uint64_t new_alarm_status = 0;
    uint64_t old_alarm_status = alarm_status_db;
    pfc_log_info("alarm_status received from db: %" PFC_PFMT_u64,
                 old_alarm_status);
    if (alarm_type == UNC_FLOW_ENT_FULL) {
      if (oper_type == UNC_OP_CREATE) {
        // Set flow entry full alarm
        new_alarm_status = old_alarm_status |
            ALARM_UPPL_ALARMS_FLOW_ENT_FULL;   // 0000 0001
      } else if (oper_type == UNC_OP_DELETE) {
        // clear flow entry full alarm
        new_alarm_status = old_alarm_status & 0xFFFE;  // 1111 1111 1111 1110
      }
    }
    if (alarm_type == UNC_OFS_LACK_FEATURES) {
      if (oper_type == UNC_OP_CREATE) {
        // Set OFS_LACK_FEATURES alarm
        new_alarm_status = old_alarm_status |
            ALARM_UPPL_ALARMS_OFS_LACK_FEATURES;   // 0000 0010
      } else if (oper_type == UNC_OP_DELETE) {
        // Clear OFS_LACK_FEATURES alarm
        new_alarm_status = old_alarm_status & 0xFFFD;  // 1111 1111 1111 1101
      }
    }
    if (old_alarm_status == new_alarm_status) {
      pfc_log_info("old and new alarms status are same, so return");
      return UPPL_RC_SUCCESS;
    }
    // Call UpdateKeyInstance to update the new alarm status and
    // new oper status value in DB
    val_switch_st obj_val_switch_st;
    memset(obj_val_switch_st.valid, '\0',
           sizeof(obj_val_switch_st.valid));
    memset(obj_val_switch_st.switch_val.valid, '\0',
           sizeof(obj_val_switch_st.switch_val.valid));
    obj_val_switch_st.valid[kIdxSwitchAlarmStatus] = UNC_VF_VALID;
    obj_val_switch_st.alarms_status = new_alarm_status;
    // Calling UPDATE KEY INSTANCE for update in DB
    void* old_value_struct;
    status = UpdateKeyInstance(obj_key_switch,
                               reinterpret_cast<void*> (&obj_val_switch_st),
                               data_type,
                               UNC_KT_SWITCH,
                               old_value_struct);
    if (status == UPPL_RC_SUCCESS && data_type != UNC_DT_IMPORT) {
      // Send oper_status notification to north bound
      // From value_struct
      val_switch_st old_val_switch, new_val_switch;

      old_val_switch.alarms_status = old_alarm_status;
      old_val_switch.valid[kIdxSwitch] = UNC_VF_INVALID;
      old_val_switch.valid[kIdxSwitchOperStatus] = UNC_VF_INVALID;
      old_val_switch.valid[kIdxSwitchAlarmStatus] = UNC_VF_VALID;

      new_val_switch.alarms_status = new_alarm_status;
      new_val_switch.valid[kIdxSwitch] = UNC_VF_INVALID;
      new_val_switch.valid[kIdxSwitchOperStatus] = UNC_VF_INVALID;
      new_val_switch.valid[kIdxSwitchAlarmStatus] = UNC_VF_VALID;
      int err = 0;
      ServerEvent ser_evt((pfc_ipcevtype_t)UPPL_EVENTS_KT_SWITCH, err);
      ser_evt.addOutput((uint32_t)UNC_OP_UPDATE);
      ser_evt.addOutput(data_type);
      ser_evt.addOutput((uint32_t)UPPL_EVENTS_KT_SWITCH);
      ser_evt.addOutput(*obj_key_switch);
      ser_evt.addOutput(new_val_switch);
      ser_evt.addOutput(old_val_switch);
      PhysicalLayer *physical_layer = PhysicalLayer::get_instance();
      // Notify operstatus modifications
      status = (UpplReturnCode) physical_layer
          ->get_ipc_connection_manager()->SendEvent(&ser_evt);
      if (status != UPPL_RC_SUCCESS) {
        pfc_log_info("Update alarm status could not be sent to NB, error %d",
                     status);
      }
    } else {
      pfc_log_info("Update alarm status in db status %d", status);
    }
    val_switch_st_t *val_switch =
        reinterpret_cast<val_switch_st_t*>(old_value_struct);
    if (val_switch != NULL) {
      delete val_switch;
      val_switch = NULL;
    }
  } else {
    pfc_log_info("Reading alarm status from db failed with %d",
                 read_alarm_status);
  }
  return status;
}

/** IsKeyExists
 * * @Description : This function checks whether the controller_id exists in DB
 * * @param[in] : data_type - UNC_DT_*
 * key_values - contains switch_id
 * * @return    : UPPL_RC_SUCCESS or UPPL_RC_ERR* based in operation type
 * */
UpplReturnCode Kt_Switch::IsKeyExists(unc_keytype_datatype_t data_type,
                                      vector<string> key_values) {
  pfc_log_debug("Inside IsKeyExists");
  PhysicalLayer *physical_layer = PhysicalLayer::get_instance();
  UpplReturnCode check_status = UPPL_RC_SUCCESS;

  if (key_values.empty()) {
    // No key given, return failure
    pfc_log_error("No key given. Returning error");
    return UPPL_RC_ERR_BAD_REQUEST;
  }

  string controller_name = key_values[0];
  string switch_id = key_values[1];

  // Structure used to send request to ODBC
  DBTableSchema kt_switch_dbtableschema;

  // Construct Primary key list
  vector<string> vect_prim_keys;
  vect_prim_keys.push_back(CTR_NAME);
  vect_prim_keys.push_back(SWITCH_ID);

  // Construct TableAttrSchema structure
  // TableAttrSchema holds table_name, primary key, attr_name
  vector<TableAttrSchema> vect_table_attr_schema;
  TableAttrSchema kt_switch_table_attr_schema;
  list< vector<TableAttrSchema> > row_list;

  // controller_name
  PhyUtil::FillDbSchema(CTR_NAME, controller_name,
                        controller_name.length(), DATATYPE_UINT8_ARRAY_32,
                        vect_table_attr_schema);
  // switch_id
  PhyUtil::FillDbSchema(SWITCH_ID, switch_id,
                        switch_id.length(),
                        DATATYPE_UINT8_ARRAY_256,
                        vect_table_attr_schema);

  kt_switch_dbtableschema.set_table_name(UPPL_SWITCH_TABLE);
  kt_switch_dbtableschema.set_primary_keys(vect_prim_keys);
  row_list.push_back(vect_table_attr_schema);
  kt_switch_dbtableschema.set_row_list(row_list);

  //  Send request to ODBC for switch_table
  ODBCM_RC_STATUS check_db_status = physical_layer->get_odbc_manager()->
      IsRowExists(data_type, kt_switch_dbtableschema);
  if (check_db_status == ODBCM_RC_CONNECTION_ERROR) {
    // log error to log daemon
    pfc_log_error("DB connection not available or cannot access DB");
    check_status = UPPL_RC_ERR_DB_ACCESS;
  } else if (check_db_status == ODBCM_RC_ROW_EXISTS) {
    pfc_log_debug("DB returned success for Row exists");
  } else {
    pfc_log_info("DB Returned failure for IsRowExists");
    check_status = UPPL_RC_ERR_NO_SUCH_INSTANCE;
  }
  pfc_log_debug("check_status = %d", check_status);
  return check_status;
}

/** PopulateDBSchemaForKtTable
 * * @Description : This function populates the DBAttrSchema to be used to
 *                  send request to ODBC
 * * @param[in] : DBTableSchema - DBtableschema associated with KT_Switch
 * key_struct - key instance of Kt_Switch
 * val_struct - val instance of Kt_Switch
 * operation_type - UNC_OP*
 * * @return    : UPPL_RC_SUCCESS or UPPL_RC_ERR*
 * */
void Kt_Switch::PopulateDBSchemaForKtTable(
    DBTableSchema &kt_switch_dbtableschema,
    void* key_struct,
    void* val_struct,
    uint8_t operation_type,
    uint32_t option1,
    uint32_t option2,
    vector<ODBCMOperator> &vect_key_operations,
    void* &old_value_struct,
    CsRowStatus row_status,
    pfc_bool_t is_filtering,
    pfc_bool_t is_state) {
  // Construct Primary key list
  vector<string> vect_prim_keys;
  vect_prim_keys.push_back(CTR_NAME);

  // construct TableAttrSchema structure
  // TableAttrSchema holds table_name, primary key, attr_name
  TableAttrSchema kt_switch_table_attr_schema;
  vector<TableAttrSchema> vect_table_attr_schema;
  list < vector<TableAttrSchema> > row_list;

  key_switch_t *obj_key_switch = reinterpret_cast<key_switch_t*>(key_struct);
  val_switch_st_t *obj_val_switch =
      reinterpret_cast<val_switch_st_t*>(val_struct);

  stringstream valid;
  pfc_log_info("operation: %d", operation_type);

  // controller_name
  string controller_name = (const char*)obj_key_switch
      ->ctr_key.controller_name;
  pfc_log_info("controller name: %s", controller_name.c_str());
  PhyUtil::FillDbSchema(CTR_NAME, controller_name,
                        controller_name.length(), DATATYPE_UINT8_ARRAY_32,
                        vect_table_attr_schema);

  // switch_id
  string switch_id = (const char*) obj_key_switch->switch_id;
  if (operation_type == UNC_OP_READ_SIBLING_BEGIN ||
      operation_type == UNC_OP_READ_SIBLING_COUNT) {
    switch_id = "";
  }

  pfc_log_info("switch_id: %s ", switch_id.c_str());
  PhyUtil::FillDbSchema(SWITCH_ID, switch_id,
                        switch_id.length(),
                        DATATYPE_UINT8_ARRAY_256,
                        vect_table_attr_schema);

  val_switch_st_t *val_switch_valid_st = NULL;
  if (operation_type == UNC_OP_UPDATE) {
    // get valid array for update req
    pfc_log_debug("Get Valid value from port Update Valid Flag");
    unc_keytype_validflag_t new_valid_val = UNC_VF_VALID;
    val_switch_valid_st = new val_switch_st_t();
    UpdateSwitchValidFlag(key_struct, val_struct,
                          *val_switch_valid_st, new_valid_val);
    old_value_struct = reinterpret_cast<void*>(val_switch_valid_st);
  }
  GetSwitchValStructure(
      obj_val_switch,
      vect_table_attr_schema,
      vect_prim_keys,
      operation_type,
      val_switch_valid_st,
      valid);
  GetSwitchStateValStructure(
      obj_val_switch,
      vect_table_attr_schema,
      vect_prim_keys,
      operation_type,
      val_switch_valid_st,
      valid);
  vect_prim_keys.push_back(SWITCH_ID);
  PhyUtil::reorder_col_attrs(vect_prim_keys, vect_table_attr_schema);
  kt_switch_dbtableschema.set_table_name(UPPL_SWITCH_TABLE);
  kt_switch_dbtableschema.set_primary_keys(vect_prim_keys);
  row_list.push_back(vect_table_attr_schema);
  kt_switch_dbtableschema.set_row_list(row_list);
  return;
}

/** FillSwitchValueStructure
 * * @Description : This function populates val_switch by values retrieved
 * from DB
 * * * @param[in] : switch dbtable schema - dbtableschema associated with
 * Kt_Switch
 * vect_obj_val_switch - vector of type val_switch_st
 * max_rep_ct - specifies no of rows to be returned
 * operation_type - UNC_OP*
 * switch_id - vector of key structure of switch
 * * @return    : void
 * */
void Kt_Switch::FillSwitchValueStructure(
    DBTableSchema &kt_switch_dbtableschema,
    vector<val_switch_st_t> &vect_obj_val_switch,
    uint32_t &max_rep_ct,
    uint32_t operation_type,
    vector<key_switch_t> &switch_id) {
  // populate IPC value structure based on the response recevied from DB
  list < vector<TableAttrSchema> > res_switch_row_list =
      kt_switch_dbtableschema.get_row_list();
  list < vector<TableAttrSchema> > :: iterator res_switch_iter =
      res_switch_row_list.begin();
  max_rep_ct = res_switch_row_list.size();
  pfc_log_debug("res_switch_row_list.size: %d", max_rep_ct);

  // populate IPC value structure based on the response recevied from DB
  for (; res_switch_iter != res_switch_row_list.end(); ++res_switch_iter)  {
    vector<TableAttrSchema> res_switch_table_attr_schema = (*res_switch_iter);
    vector<TableAttrSchema> :: iterator vect_switch_iter =
        res_switch_table_attr_schema.begin();

    val_switch_st_t obj_val_switch;
    memset(&obj_val_switch, 0, sizeof(val_switch_st_t));
    memset(&obj_val_switch.switch_val, 0, sizeof(val_switch_t));
    key_switch_t obj_key_switch;
    memset(&obj_key_switch, 0, sizeof(obj_key_switch));
    // Read all attributes
    vector<int> valid_flag, cs_attr;
    for (; vect_switch_iter != res_switch_table_attr_schema.end();
        ++vect_switch_iter) {
      // populate values from switch_table
      TableAttrSchema tab_schema = (*vect_switch_iter);
      string attr_name = tab_schema.table_attribute_name;
      string attr_value;
      if (attr_name == SWITCH_ID) {
        memset(obj_key_switch.switch_id, '\0',
               sizeof(obj_key_switch.switch_id));
        PhyUtil::GetValueFromDbSchema(tab_schema, attr_value,
                                      DATATYPE_UINT8_ARRAY_256);
        memcpy(obj_key_switch.switch_id, attr_value.c_str(),
               attr_value.length()+1);
        switch_id.push_back(obj_key_switch);
        pfc_log_debug("switch_id : %s", attr_value.c_str());
      }
      if (attr_name == CTR_NAME) {
        PhyUtil::GetValueFromDbSchema(tab_schema, attr_value,
                                      DATATYPE_UINT8_ARRAY_32);
        pfc_log_debug("controller_name: %s", attr_value.c_str());
        memcpy(obj_key_switch.ctr_key.controller_name, attr_value.c_str(),
               attr_value.length()+1);
      }
      if (attr_name == SWITCH_DESCRIPTION) {
        PhyUtil::GetValueFromDbSchema(tab_schema, attr_value,
                                      DATATYPE_UINT8_ARRAY_128);
        memcpy(obj_val_switch.switch_val.description,
               attr_value.c_str(),
               attr_value.length()+1);
        pfc_log_debug("Description: %s",
                      obj_val_switch.switch_val.description);
      }
      if (attr_name == SWITCH_MODEL) {
        PhyUtil::GetValueFromDbSchema(tab_schema, attr_value,
                                      DATATYPE_UINT8_ARRAY_16);
        memcpy(obj_val_switch.switch_val.model,
               attr_value.c_str(),
               attr_value.length()+1);
        pfc_log_debug("Model: %s", obj_val_switch.switch_val.model);
      }
      if (attr_name == SWITCH_IP_ADDRESS) {
        PhyUtil::GetValueFromDbSchema(tab_schema, attr_value,
                                      DATATYPE_IPV4);
        inet_pton(AF_INET, (const char *)attr_value.c_str(),
                  &obj_val_switch.switch_val.ip_address.s_addr);
        pfc_log_debug("ip_address :%s", attr_value.c_str());
      }
      if (attr_name == SWITCH_IPV6_ADDRESS) {
        PhyUtil::GetValueFromDbSchema(tab_schema, attr_value,
                                      DATATYPE_IPV6);
        inet_pton(AF_INET6, (const char *)attr_value.c_str(),
                  &obj_val_switch.switch_val.ipv6_address.s6_addr);
        pfc_log_debug("ipv6_address :%s", attr_value.c_str());
        char str[INET6_ADDRSTRLEN];
        // Checking filled up value
        inet_ntop(AF_INET6, &obj_val_switch.switch_val.ipv6_address.s6_addr,
                  str, INET6_ADDRSTRLEN);
        pfc_log_debug("ipv6_address :%s", str);
      }
      if (attr_name == SWITCH_ADMIN_STATUS) {
        PhyUtil::GetValueFromDbSchema(tab_schema, attr_value,
                                      DATATYPE_UINT16);
        obj_val_switch.switch_val.admin_status = atoi(attr_value.c_str());
        pfc_log_debug("admin status : %d",
                      obj_val_switch.switch_val.admin_status);
      }
      if (attr_name == SWITCH_DOMAIN_NAME) {
        PhyUtil::GetValueFromDbSchema(tab_schema, attr_value,
                                      DATATYPE_UINT8_ARRAY_32);
        memcpy(obj_val_switch.switch_val.domain_name,
               attr_value.c_str(),
               attr_value.length()+1);
        pfc_log_debug("Domain Name: %s",
                      obj_val_switch.switch_val.domain_name);
      }
      if (attr_name == SWITCH_OPER_STATUS) {
        PhyUtil::GetValueFromDbSchema(tab_schema, attr_value,
                                      DATATYPE_UINT16);
        obj_val_switch.oper_status = atoi(attr_value.c_str());
        pfc_log_debug("oper status : %d", obj_val_switch.oper_status);
      }
      if (attr_name == SWITCH_MANUFACTURER) {
        PhyUtil::GetValueFromDbSchema(tab_schema, attr_value,
                                      DATATYPE_UINT8_ARRAY_256);
        memcpy(obj_val_switch.manufacturer,
               attr_value.c_str(),
               attr_value.length()+1);
        pfc_log_debug("Manufacturer: %s", obj_val_switch.manufacturer);
      }
      if (attr_name == SWITCH_HARDWARE) {
        PhyUtil::GetValueFromDbSchema(tab_schema, attr_value,
                                      DATATYPE_UINT8_ARRAY_256);
        memcpy(obj_val_switch.hardware,
               attr_value.c_str(),
               attr_value.length()+1);
        pfc_log_debug("Hardware: %s", obj_val_switch.hardware);
      }
      if (attr_name == SWITCH_SOFTWARE) {
        PhyUtil::GetValueFromDbSchema(tab_schema, attr_value,
                                      DATATYPE_UINT8_ARRAY_256);
        memcpy(obj_val_switch.software,
               attr_value.c_str(),
               attr_value.length()+1);
        pfc_log_debug("Software: %s", obj_val_switch.software);
      }
      if (attr_name == SWITCH_ALARM_STATUS) {
        PhyUtil::GetValueFromDbSchema(tab_schema, attr_value,
                                      DATATYPE_UINT64);
        obj_val_switch.alarms_status = atol(attr_value.c_str());
        pfc_log_debug("alarms status : %" PFC_PFMT_u64,
                      obj_val_switch.alarms_status);
      }
      if (attr_name == SWITCH_VALID) {
        PhyUtil::GetValueFromDbSchema(tab_schema, attr_value,
                                      DATATYPE_UINT8_ARRAY_11);
        memset(obj_val_switch.valid, 0, 6);
        memset(obj_val_switch.switch_val.valid, 0, 6);
        FrameValidValue(attr_value, obj_val_switch);
        pfc_log_debug("valid: %s", attr_value.c_str());
      }
    }
    vect_obj_val_switch.push_back(obj_val_switch);
    pfc_log_debug("result - vect_obj_val_switch size: %d",
                  (unsigned int) vect_obj_val_switch.size());
  }
  return;
}

/** PerformRead
 * * @Description : This function reads the instance of KT_Switch based on
 * operation type - READ, READ_SIBLING_BEGIN, READ_SIBLING
 * * @param[in] : ipc session id - ipc session id used for TC validation
 * configuration id - configuration id used for TC validation
 * key_struct - key instance of KT_Switch
 * value_struct - value instance of Kt_Switch
 * data_type - UNC_DT_* , read allowed in STATE
 * operation_type -  specifies the operation type
 * sess - ipc server session where the response has to be added
 * option1, option2 - additional condition associated with read operation
 * max_rep_ct - specifies no of rows to be returned
 * * @return    : UPPL_RC_SUCCESS or UPPL_RC_ERR_*
 * */
UpplReturnCode Kt_Switch::PerformRead(uint32_t session_id,
                                      uint32_t configuration_id,
                                      void* key_struct,
                                      void* val_struct,
                                      uint32_t data_type,
                                      uint32_t operation_type,
                                      ServerSession &sess,
                                      uint32_t option1,
                                      uint32_t option2,
                                      uint32_t max_rep_ct) {
  pfc_log_info("Inside PerformRead option1=%d option2=%d max_rep_ct=%d",
               option1, option2, max_rep_ct);
  pfc_log_info("Inside PerformRead operation_type=%d data_type=%d",
               operation_type, data_type);

  physical_response_header rsh = {session_id,
      configuration_id,
      operation_type,
      max_rep_ct,
      option1,
      option2,
      data_type,
      0};
  if (operation_type == UNC_OP_READ) {
    max_rep_ct = 1;
  }
  key_switch_t *obj_key_switch = reinterpret_cast<key_switch_t*>(key_struct);

  // Invalid Operation
  if (option1 != UNC_OPT1_NORMAL) {
    pfc_log_error("Invalid option1 specified for read operation");
    rsh.result_code = UPPL_RC_ERR_INVALID_OPTION1;
    int err = PhyUtil::sessOutRespHeader(sess, rsh);
    err |= sess.addOutput((uint32_t)UNC_KT_SWITCH);
    err |= sess.addOutput(*obj_key_switch);
    if (err != 0) {
      pfc_log_error("addOutput failed for physical_response_header");
      return UPPL_RC_ERR_IPC_WRITE_ERROR;
    }
    return UPPL_RC_SUCCESS;
  }
  if ((unc_keytype_datatype_t)data_type != UNC_DT_STATE)  {
    pfc_log_error("Read operation is provided on unsuported data type");
    rsh.result_code = UPPL_RC_ERR_OPERATION_NOT_ALLOWED;
    int err = PhyUtil::sessOutRespHeader(sess, rsh);
    err |= sess.addOutput((uint32_t)UNC_KT_SWITCH);
    err |= sess.addOutput(*obj_key_switch);
    if (err != 0) {
      pfc_log_error("addOutput failed for physical_response_header");
      return UPPL_RC_ERR_IPC_WRITE_ERROR;
    }
    return UPPL_RC_SUCCESS;
  }


  UpplReturnCode read_status = UPPL_RC_SUCCESS;

  // Read operations will return switch_st based on modified fd

  vector<key_switch_t> vect_switch_id;
  vector<val_switch_st_t> vect_val_switch_st;
  read_status = ReadSwitchValFromDB(key_struct,
                                    val_struct,
                                    data_type,
                                    operation_type,
                                    max_rep_ct,
                                    vect_val_switch_st,
                                    vect_switch_id,
                                    PFC_FALSE);
  rsh.result_code = read_status;
  rsh.max_rep_count = max_rep_ct;
  if (read_status == UPPL_RC_SUCCESS) {
    int err = PhyUtil::sessOutRespHeader(sess, rsh);
    if (err != 0) {
      pfc_log_error("Failure in addOutput");
      return UPPL_RC_ERR_IPC_WRITE_ERROR;
    }
    pfc_log_debug("From db, vect_switch_id size is %d",
                  static_cast<int>(vect_switch_id.size()));
    for (unsigned int index = 0;
        index < vect_switch_id.size();
        ++index) {
      sess.addOutput((uint32_t)UNC_KT_SWITCH);
      sess.addOutput(vect_switch_id[index]);
      sess.addOutput(vect_val_switch_st[index]);
      if (index < vect_switch_id.size()-1) {
        sess.addOutput();  // separator
      }
    }
  } else {
    rsh.max_rep_count = 0;
    int err = PhyUtil::sessOutRespHeader(sess, rsh);
    err |= sess.addOutput((uint32_t)UNC_KT_SWITCH);
    err |= sess.addOutput(*obj_key_switch);
    if (err != 0) {
      pfc_log_error("Failure in addOutput");
      return UPPL_RC_ERR_IPC_WRITE_ERROR;
    }
    pfc_log_debug("Read operation status = %d", read_status);
  }
  pfc_log_debug("Return Value for read operation %d", UPPL_RC_SUCCESS);
  return UPPL_RC_SUCCESS;
}

/** ReadSwitchValFromDB
 * * @Description : This function reads the instance of Kt_Switch based on
 * operation type - READ, READ_SIBLING_BEGIN, READ_SIBLING from data_base
 * * @param[in] : key_struct - key instance of kt Switch
 * value_struct - value struct of kt switch
 * data_type - Specifies the data type for read operation
 * operation type - specifies the operation_type
 * max_rep_ct - max no of rows to be returned
 * vect_val_switch_st - vector of type val_switch_st
 * switch_id - vector of type key_switch
 * is_state - bool variable to specify the state
 * * @return    : UPPL_RC_SUCCESS or UPPL_RC_ERR_*
 * */
UpplReturnCode Kt_Switch::ReadSwitchValFromDB(
    void* key_struct,
    void* val_struct,
    uint32_t data_type,
    uint32_t operation_type,
    uint32_t &max_rep_ct,
    vector<val_switch_st_t> &vect_val_switch_st,
    vector<key_switch_t> &vect_switch_id,
    pfc_bool_t is_state) {
  PhysicalLayer *physical_layer = PhysicalLayer::get_instance();
  // key_switch_t *obj_key_switch =
  // reinterpret_cast<key_switch_t*>(key_struct);

  UpplReturnCode read_status = UPPL_RC_SUCCESS;
  ODBCM_RC_STATUS read_db_status = ODBCM_RC_SUCCESS;

  // Common structures that will be used to send query to ODBC
  // Structure used to send request to ODBC
  DBTableSchema kt_switch_dbtableschema;
  void* old_value_struct;
  vector<ODBCMOperator> vect_key_operations;
  PopulateDBSchemaForKtTable(kt_switch_dbtableschema,
                             key_struct,
                             val_struct,
                             operation_type, 0, 0,
                             vect_key_operations, old_value_struct);

  if (operation_type == UNC_OP_READ) {
    read_db_status = physical_layer->get_odbc_manager()->
        GetOneRow((unc_keytype_datatype_t)data_type,
                  kt_switch_dbtableschema);
  } else {
    read_db_status = physical_layer->get_odbc_manager()->
        GetBulkRows((unc_keytype_datatype_t)data_type, max_rep_ct,
                    kt_switch_dbtableschema,
                    (unc_keytype_operation_t)operation_type);
  }
  if (read_db_status == ODBCM_RC_RECORD_NOT_FOUND) {
    pfc_log_debug("No record found");
    read_status = UPPL_RC_ERR_NO_SUCH_INSTANCE;
    return read_status;
  } else if (read_db_status != ODBCM_RC_SUCCESS) {
    read_status = UPPL_RC_ERR_DB_GET;
    pfc_log_info("Read operation status from odbcm = %d", read_db_status);
    return read_status;
  }

  pfc_log_debug("Read operation result: %d", read_status);
  FillSwitchValueStructure(kt_switch_dbtableschema,
                           vect_val_switch_st,
                           max_rep_ct,
                           operation_type,
                           vect_switch_id);
  pfc_log_debug("vect_val_switch_st size: %d",
                (unsigned int)vect_val_switch_st.size());
  if (vect_val_switch_st.empty()) {
    // Read failed , return error
    read_status = UPPL_RC_ERR_DB_GET;
    // log error to log daemon
    pfc_log_error("Read operation has failed after reading response");
    return read_status;
  }
  return read_status;
}

/** getChildKeyStruct
 * * @Description : This function returns the void * of child key structures
 * * @param[in] : child class index - specifies the enum associated with
 * child class of switch
 * switch  id - switch id associated with KtSwitch
 * controller_name - controller name associated with ktswitch
 * * @return    : void * key structure
 * */
void* Kt_Switch::getChildKeyStruct(int child_class,
                                   string switch_id,
                                   string controller_name) {
  switch (child_class) {
    case kIdxPort: {
      key_port_t *obj_child_key = new key_port_t;
      memcpy(obj_child_key->sw_key.switch_id,
             switch_id.c_str(),
             switch_id.length()+1);
      memcpy(obj_child_key->sw_key.ctr_key.controller_name,
             controller_name.c_str(),
             controller_name.length()+1);
      memset(obj_child_key->port_id, 0, 32);
      void* child_key = reinterpret_cast<void *>(obj_child_key);
      return child_key;
    }
    default: {
      // Do nothing
      pfc_log_info("Invalid index %d passed to getChildKeyStruct()",
                   child_class);
      PFC_ASSERT(PFC_FALSE);
      return NULL;
    }
  }
}

/** FreeChildKeyStruct
 * * @Description : This function clears the void * of child key structures
 * * @param[in] : child class index - specifies the enum associated with
 * child class of switch
 * key_struct of child
 * * @return    : return void
 * */
void Kt_Switch::FreeChildKeyStruct(int child_class,
                                   void *key_struct) {
  switch (child_class) {
    case kIdxPort: {
      key_port_t *obj_child_key =
          reinterpret_cast<key_port_t *>(key_struct);
      if (obj_child_key != NULL) {
        delete obj_child_key;
        obj_child_key = NULL;
      }
      return;
    }
    default: {
      // Do nothing
      pfc_log_info("Invalid index %d passed to FreeChildKeyStruct()",
                   child_class);
      PFC_ASSERT(PFC_FALSE);
      return;
    }
  }
}

/** Fill_Attr_Syntax_Map
 * * @Description : This function fills the attributes associated
 * with the class
 * * @param[in] : void
 * * @return    : void
 * */
void Kt_Switch::Fill_Attr_Syntax_Map() {
  Kt_Class_Attr_Syntax objKeyAttr1Syntax =
  { PFC_IPCTYPE_UINT8, 0, 0, 1, 32, true, "" };
  attr_syntax_map[CTR_NAME] = objKeyAttr1Syntax;

  Kt_Class_Attr_Syntax objKeyAttrSyntax =
  { PFC_IPCTYPE_UINT8, 0, 0, 1, 256, true, ""};
  attr_syntax_map[SWITCH_ID] = objKeyAttrSyntax;

  Kt_Class_Attr_Syntax  objAttrDescSyntax =
  { PFC_IPCTYPE_STRING, 0, 0, 0, 128, false, "" };
  attr_syntax_map[SWITCH_DESCRIPTION] = objAttrDescSyntax;

  Kt_Class_Attr_Syntax objAttrModelSyntax =
  { PFC_IPCTYPE_STRING, 0, 0, 0, 16, false, "" };
  attr_syntax_map[SWITCH_MODEL] = objAttrModelSyntax;

  Kt_Class_Attr_Syntax objAttrIpSyntax =
  { PFC_IPCTYPE_IPV4, 0, 4294967295LL, 0, 0, false,  "" };
  attr_syntax_map[SWITCH_IP_ADDRESS] = objAttrIpSyntax;

  Kt_Class_Attr_Syntax objAttrIPV6Syntax =
  { PFC_IPCTYPE_IPV6, 0, 4294967295LL, 0, 0, false,  "" };
  attr_syntax_map[SWITCH_IPV6_ADDRESS] = objAttrIPV6Syntax;

  Kt_Class_Attr_Syntax objAttrAdminStatusSyntax =
  { PFC_IPCTYPE_UINT8, 0, 1, 0, 0, false, "" };
  attr_syntax_map[SWITCH_ADMIN_STATUS] = objAttrAdminStatusSyntax;

  Kt_Class_Attr_Syntax  objAttrDomainNameSyntax =
  { PFC_IPCTYPE_STRING, 0, 0, 0, 32, false, "" };
  attr_syntax_map[SWITCH_DOMAIN_NAME] = objAttrDomainNameSyntax;

  Kt_Class_Attr_Syntax objAttrValidSyntax =
  { PFC_IPCTYPE_STRING, 0, 0, 0, 6, false, "" };
  attr_syntax_map[SWITCH_VALID] = objAttrValidSyntax;
}

/** HandleOperStatus
 * * @Description: This function perform the required actions
 * when oper status changes
 * * @param[in]: key_struct - key structure of kt switch
 * value_struct - value structure of kt switch
 * * @return: UPPL_RC_SUCCESS or UPPL_RC_ERR*
 * */
UpplReturnCode Kt_Switch::HandleOperStatus(uint32_t data_type,
                                           void *key_struct,
                                           void *value_struct) {
  FN_START_TIME("HandleOperStatus", "Switch");
  PhysicalLayer *physical_layer = PhysicalLayer::get_instance();
  UpplReturnCode return_code = UPPL_RC_SUCCESS;

  if (key_struct == NULL) {
    return_code = UPPL_RC_ERR_BAD_REQUEST;
    FN_END_TIME("HandleOperStatus", "Switch");
    return return_code;
  }
  key_switch_t *obj_key_switch =
      reinterpret_cast<key_switch_t*>(key_struct);
  string controller_name = (const char*)obj_key_switch->
      ctr_key.controller_name;

  // get the controller oper status and decide on the oper status
  key_ctr_t ctr_key;
  memcpy(ctr_key.controller_name, controller_name.c_str(),
         controller_name.length()+1);
  uint8_t ctrl_oper_status = 0;
  UpplSwitchOperStatus switch_oper_status = UPPL_SWITCH_OPER_UNKNOWN;
  Kt_Controller controller;
  UpplReturnCode read_status = controller.
      GetOperStatus(data_type, reinterpret_cast<void*>(&ctr_key),
                    ctrl_oper_status);
  if (read_status == UPPL_RC_SUCCESS) {
    pfc_log_info("Controller's oper_status %d", ctrl_oper_status);
    if (ctrl_oper_status ==
        (UpplControllerOperStatus) UPPL_CONTROLLER_OPER_UP) {
      pfc_log_info("Set Switch oper status as up");
      switch_oper_status = UPPL_SWITCH_OPER_UP;
    }
  } else {
    pfc_log_info("Controller's oper_status read returned failure");
  }
  // Update oper_status in switch table
  return_code = SetOperStatus(data_type,
                              key_struct,
                              switch_oper_status, true);
  pfc_log_info("Set Switch oper status %d", return_code);
  // Call referred classes notify oper status functions
  // Get all switch associated with controller
  vector<string> vect_prim_keys;
  vect_prim_keys.push_back(CTR_NAME);
  vect_prim_keys.push_back(SWITCH_ID);
  // To traverse the list
  vector<key_switch_t> vectSwitchKey;
  list<vector<TableAttrSchema> > ::iterator iter_list;
  string switch_id;
  while (true) {
    DBTableSchema kt_switch_dbtableschema;
    vector<TableAttrSchema> vect_table_attr_schema;
    list< vector<TableAttrSchema> > row_list;
    pfc_log_debug(
        "GetBulkRows called with controller_name %s, switch_id %s",
        controller_name.c_str(), switch_id.c_str());
    PhyUtil::FillDbSchema(CTR_NAME, controller_name,
                          controller_name.length(), DATATYPE_UINT8_ARRAY_32,
                          vect_table_attr_schema);
    PhyUtil::FillDbSchema(SWITCH_ID, switch_id,
                          switch_id.length(), DATATYPE_UINT8_ARRAY_256,
                          vect_table_attr_schema);
    kt_switch_dbtableschema.set_table_name(UPPL_SWITCH_TABLE);
    kt_switch_dbtableschema.set_primary_keys(vect_prim_keys);
    row_list.push_back(vect_table_attr_schema);
    kt_switch_dbtableschema.set_row_list(row_list);
    ODBCM_RC_STATUS db_status = physical_layer->get_odbc_manager()->
        GetBulkRows((unc_keytype_datatype_t)data_type,
                    UPPL_MAX_REP_CT,
                    kt_switch_dbtableschema,
                    (unc_keytype_operation_t)UNC_OP_READ_SIBLING_BEGIN);
    if (db_status != ODBCM_RC_SUCCESS) {
      pfc_log_info("ReadBulk failure");
      break;
    }
    for (iter_list = kt_switch_dbtableschema.row_list_.begin();
        iter_list != kt_switch_dbtableschema.row_list_.end();
        ++iter_list) {
      vector<TableAttrSchema> attributes_vector = *iter_list;
      vector<TableAttrSchema> :: iterator iter_vector;
      key_switch_t key_switch;
      for (iter_vector = attributes_vector.begin();
          iter_vector != attributes_vector.end();
          ++iter_vector) {
        /* Get attribute name of a row */
        TableAttrSchema tab_attr_schema = (*iter_vector);
        if (tab_attr_schema.table_attribute_name == "controller_name") {
          PhyUtil::GetValueFromDbSchema(tab_attr_schema, controller_name,
                                        DATATYPE_UINT8_ARRAY_32);
          pfc_log_debug("Controller Name From GetValueFromDbSchema is %s",
                        controller_name.c_str());
          memcpy(key_switch.ctr_key.controller_name,
                 controller_name.c_str(),
                 controller_name.length() +1);
        } else if (tab_attr_schema.table_attribute_name == "switch_id") {
          PhyUtil::GetValueFromDbSchema(tab_attr_schema, switch_id,
                                        DATATYPE_UINT8_ARRAY_256);
          pfc_log_debug("Switch_id from GetValueFromDbSchema is %s",
                        switch_id.c_str());
          memcpy(key_switch.switch_id,
                 switch_id.c_str(),
                 switch_id.length() +1);
        }
      }
      vectSwitchKey.push_back(key_switch);
    }
  }
  vector<key_switch_t>::iterator keyItr =
      vectSwitchKey.begin();
  for (; keyItr != vectSwitchKey.end(); ++keyItr) {
    // key_switch_t objKeySwitch;
    return_code = NotifyOperStatus(data_type,
                                   reinterpret_cast<void *> (&(*keyItr)), NULL);
    pfc_log_debug("Notify Oper status return %d", return_code);
  }
  FN_END_TIME("HandleOperStatus", "Switch");
  return return_code;
}

/** NotifyOperStatus
 * * @Description: This function notifies the other key types
 * when the oper status changes
 * * @param[in] : key_struct - key structure of kt switch
 * values_struct - value structure of kt switch
 * * @return    : UPPL_RC_SUCCESS or UPPL_RC_ERR*
 * */
UpplReturnCode Kt_Switch::NotifyOperStatus(uint32_t data_type,
                                           void* key_struct,
                                           void* value_struct) {
  UpplReturnCode return_code = UPPL_RC_SUCCESS;
  key_switch_t *obj_key_switch = reinterpret_cast<key_switch_t*>(key_struct);

  string switch_id = (const char*)obj_key_switch->switch_id;
  string controller_name = (const char*)obj_key_switch->
      ctr_key.controller_name;

  // Notify Kt_Ctr_Domain
  Kt_Ctr_Domain ctr_domain_obj;
  key_ctr_domain_t ctr_domain_key_obj;
  memcpy(ctr_domain_key_obj.ctr_key.controller_name,
         controller_name.c_str(),
         controller_name.length()+1);
  return_code = ctr_domain_obj.HandleOperStatus(
      data_type,
      reinterpret_cast<void *> (&ctr_domain_key_obj), NULL);
  pfc_log_info("HandleOperStatus for ctr domain class %d",
               return_code);

  // Notify Kt_Logical_Port
  key_logical_port obj_key_logical_port;
  memcpy(obj_key_logical_port.domain_key.ctr_key.controller_name,
         controller_name.c_str(), (controller_name.length())+1);
  memset(obj_key_logical_port.domain_key.domain_name, 0, 32);
  memset(obj_key_logical_port.port_id, 0, 320);
  Kt_LogicalPort logical_port;
  val_logical_port_st_t obj_val_logical_port;
  memset(&obj_val_logical_port, 0, sizeof(val_logical_port_st_t));
  memcpy(obj_val_logical_port.logical_port.switch_id,
         switch_id.c_str(), switch_id.length()+1);
  memset(obj_val_logical_port.logical_port.valid, 0, 5);
  obj_val_logical_port.logical_port.valid[kIdxLogicalPortSwitchId] =
      UNC_VF_VALID;
  return_code = logical_port.HandleOperStatus(
      data_type,
      reinterpret_cast<void*>(&obj_key_logical_port),
      reinterpret_cast<void*>(&obj_val_logical_port));
  pfc_log_info("Switch:Handle Oper Status for Logical Port Class %d",
               return_code);

  // Notify Kt_Port
  Kt_Port port;
  key_port_t obj_key_port;
  memcpy(obj_key_port.sw_key.ctr_key.controller_name,
         controller_name.c_str(),
         controller_name.length()+1);
  memcpy(obj_key_port.sw_key.switch_id,
         switch_id.c_str(),
         switch_id.length()+1);
  memset(obj_key_port.port_id, 0, 32);
  pfc_log_debug("HandleOperStatus for port with sw id: %s", switch_id.c_str());
  return_code = port.HandleOperStatus(
      data_type,
      reinterpret_cast<void *>(&obj_key_port),
      NULL);
  pfc_log_info("Switch:HandleOperStatus for port class %d", return_code);

  /* Notify Kt_Link
  Kt_Link link;
  key_link_t obj_key_link1;
  memcpy(obj_key_link1.ctr_key.controller_name,
         controller_name.c_str(),
         controller_name.length() +1);
  memcpy(obj_key_link1.switch_id1,
         switch_id.c_str(),
         switch_id.length() +1);
  memset(obj_key_link1.switch_id2, 0, 256);
  memset(obj_key_link1.port_id1, 0, 32);
  memset(obj_key_link1.port_id2, 0, 32);
  return_code = link.HandleOperStatus(reinterpret_cast<void *>(&obj_key_link1),
                                      NULL);
  pfc_log_info("HandleOperStatus for Link class %d", return_code);

  key_link_t obj_key_link2;
  memcpy(obj_key_link2.ctr_key.controller_name,
         controller_name.c_str(),
         controller_name.length() +1);
  memcpy(obj_key_link2.switch_id2,
         switch_id.c_str(),
         switch_id.length() +1);
  memset(obj_key_link2.switch_id1, 0, 256);
  memset(obj_key_link2.port_id1, 0, 32);
  memset(obj_key_link2.port_id2, 0, 32);
  return_code = link.HandleOperStatus(
      data_type, reinterpret_cast<void*>(&obj_key_link2), NULL);
  pfc_log_info("HandleOperStatus for link class %d", return_code);*/

  return return_code;
}

/** GetOperStatus
 * * @Description: This function updates the oper status in db
 * * @param[in]: key_struct - key structure of kt switch
 * oper_status - specifies the oper status
 * * @return:  UPPL_RC_SUCCESS or UPPL_RC_ERR*
 * */
UpplReturnCode Kt_Switch::GetOperStatus(uint32_t data_type,
                                        void* key_struct,
                                        uint8_t &oper_status) {
  PhysicalLayer *physical_layer = PhysicalLayer::get_instance();
  key_switch *obj_key_switch =
      reinterpret_cast<key_switch_t*>(key_struct);
  TableAttrSchema kt_switch_table_attr_schema;
  vector<TableAttrSchema> vect_table_attr_schema;
  list < vector<TableAttrSchema> > row_list;
  vector<string> vect_prim_keys;
  string controller_name = (const char*)obj_key_switch->ctr_key.controller_name;
  string switch_id = (const char*)obj_key_switch->switch_id;
  if (!controller_name.empty()) {
    vect_prim_keys.push_back(CTR_NAME);
  }

  if (!switch_id.empty()) {
    vect_prim_keys.push_back(SWITCH_ID);
  }

  PhyUtil::FillDbSchema(CTR_NAME, controller_name,
                        controller_name.length(), DATATYPE_UINT8_ARRAY_32,
                        vect_table_attr_schema);
  PhyUtil::FillDbSchema(SWITCH_ID, switch_id,
                        switch_id.length(), DATATYPE_UINT8_ARRAY_256,
                        vect_table_attr_schema);
  string oper_value;
  PhyUtil::FillDbSchema(SWITCH_OPER_STATUS, oper_value,
                        oper_value.length(), DATATYPE_UINT16,
                        vect_table_attr_schema);

  DBTableSchema kt_switch_dbtableschema;
  kt_switch_dbtableschema.set_table_name(UPPL_SWITCH_TABLE);
  kt_switch_dbtableschema.set_primary_keys(vect_prim_keys);
  row_list.push_back(vect_table_attr_schema);
  kt_switch_dbtableschema.set_row_list(row_list);

  // Call ODBCManager and update
  ODBCM_RC_STATUS update_db_status =
      physical_layer->get_odbc_manager()->GetOneRow(
          (unc_keytype_datatype_t)data_type,
          kt_switch_dbtableschema);
  if (update_db_status != ODBCM_RC_SUCCESS) {
    // log error
    pfc_log_error("oper_status read operation failed");
    return UPPL_RC_ERR_DB_GET;
  }
  // read the oper status value
  list < vector<TableAttrSchema> > res_switch_row_list =
      kt_switch_dbtableschema.get_row_list();
  list < vector<TableAttrSchema> >::iterator res_switch_iter =
      res_switch_row_list.begin();

  // populate IPC value structure based on the response recevied from DB
  for (; res_switch_iter!= res_switch_row_list.end(); ++res_switch_iter) {
    vector<TableAttrSchema> res_switch_table_attr_schema = (*res_switch_iter);
    vector<TableAttrSchema>:: iterator vect_switch_iter =
        res_switch_table_attr_schema.begin();
    for (; vect_switch_iter != res_switch_table_attr_schema.end();
        ++vect_switch_iter) {
      // populate values from switch_table
      TableAttrSchema tab_schema = (*vect_switch_iter);
      string attr_name = tab_schema.table_attribute_name;
      string attr_value;
      if (attr_name == "oper_status") {
        PhyUtil::GetValueFromDbSchema(tab_schema, attr_value,
                                      DATATYPE_UINT16);
        oper_status = atoi(attr_value.c_str());
        pfc_log_info("oper_status: %d", oper_status);
        break;
      }
    }
  }
  return UPPL_RC_SUCCESS;
}

/** SetOperStatus
 * * @Description: This function updates the oper status in db
 * * *@param[in]: key_struct - key structure of kt switch
 * oper_status - specifies the oper status of kt switch
 * is_single_key - bool variable
 * *@return: UPPL_RC_SUCCESS or UPPL_RC_ERR*
 * */
UpplReturnCode Kt_Switch::SetOperStatus(uint32_t data_type,
                                        void *key_struct,
                                        UpplSwitchOperStatus oper_status,
                                        bool is_single_key) {
  PhysicalLayer *physical_layer = PhysicalLayer::get_instance();
  key_switch_t *obj_key_switch =
      reinterpret_cast<key_switch_t*>(key_struct);
  // TableAttrSchema kt_logical_port_table_attr_schema;
  vector<TableAttrSchema> vect_table_attr_schema;
  list < vector<TableAttrSchema> > row_list;
  vector<string> vect_prim_keys;
  string switch_id = (const char*)obj_key_switch->switch_id;
  string controller_name = (const char*)obj_key_switch->ctr_key.controller_name;
  if (!controller_name.empty()) {
    vect_prim_keys.push_back(CTR_NAME);
    PhyUtil::FillDbSchema(CTR_NAME, controller_name,
                          controller_name.length(), DATATYPE_UINT8_ARRAY_32,
                          vect_table_attr_schema);
  }
  if (is_single_key == false) {
    if (!switch_id.empty()) {
      vect_prim_keys.push_back(SWITCH_ID);
      PhyUtil::FillDbSchema(SWITCH_ID, switch_id,
                            switch_id.length(), DATATYPE_UINT8_ARRAY_256,
                            vect_table_attr_schema);
    }
  }

  string oper_value = PhyUtil::uint8tostr(oper_status);
  PhyUtil::FillDbSchema(SWITCH_OPER_STATUS, oper_value,
                        oper_value.length(), DATATYPE_UINT16,
                        vect_table_attr_schema);

  DBTableSchema kt_switch_dbtableschema;
  kt_switch_dbtableschema.set_table_name(UPPL_SWITCH_TABLE);
  kt_switch_dbtableschema.set_primary_keys(vect_prim_keys);
  row_list.push_back(vect_table_attr_schema);
  kt_switch_dbtableschema.set_row_list(row_list);

  // Call ODBCManager and update
  ODBCM_RC_STATUS update_db_status =
      physical_layer->get_odbc_manager()->UpdateOneRow(
          (unc_keytype_datatype_t)data_type,
          kt_switch_dbtableschema);
  if (update_db_status != ODBCM_RC_SUCCESS) {
    // log error
    pfc_log_error("oper_status update operation failed");
    return UPPL_RC_ERR_DB_UPDATE;
  } else {
    // Notify operstatus change to northbound
    val_switch_st old_val_switch, new_val_switch;
    uint8_t old_oper_status = 0;
    UpplReturnCode read_status = GetOperStatus(data_type,
                                               key_struct,
                                               old_oper_status);
    if (read_status == UPPL_RC_SUCCESS) {
      memset(old_val_switch.valid, 0, 6);
      old_val_switch.oper_status = old_oper_status;
      old_val_switch.valid[kIdxSwitchOperStatus] = UNC_VF_VALID;
      memset(new_val_switch.valid, 0, 6);
      new_val_switch.oper_status = oper_status;
      new_val_switch.valid[kIdxSwitchOperStatus] = UNC_VF_VALID;
      int err = 0;
      // Send notification to Northbound
      ServerEvent ser_evt((pfc_ipcevtype_t)UPPL_EVENTS_KT_SWITCH, err);
      ser_evt.addOutput((uint32_t)UNC_OP_UPDATE);
      ser_evt.addOutput(data_type);
      ser_evt.addOutput((uint32_t)UPPL_EVENTS_KT_SWITCH);
      ser_evt.addOutput(*obj_key_switch);
      ser_evt.addOutput(new_val_switch);
      ser_evt.addOutput(old_val_switch);
      PhysicalLayer *physical_layer = PhysicalLayer::get_instance();
      // Notify operstatus modifications
      UpplReturnCode status = (UpplReturnCode) physical_layer
          ->get_ipc_connection_manager()->SendEvent(&ser_evt);
      pfc_log_debug("Event notification status %d", status);
    }
  }
  return UPPL_RC_SUCCESS;
}

/** GetAlarmStatus
 * * @Description: This function updates the alarm status in db
 * * @param[in]: key_struct - key structure of kt switch
 * alarm status - specifies the alarm status
 * * @return:  UPPL_RC_SUCCESS or UPPL_RC_ERR*
 * */

UpplReturnCode Kt_Switch::GetAlarmStatus(uint32_t data_type,
                                         void* key_struct,
                                         uint64_t &alarms_status) {
  PhysicalLayer *physical_layer = PhysicalLayer::get_instance();
  key_switch *obj_key_switch =
      reinterpret_cast<key_switch_t*>(key_struct);
  TableAttrSchema kt_switch_table_attr_schema;
  vector<TableAttrSchema> vect_table_attr_schema;
  list < vector<TableAttrSchema> > row_list;
  string controller_name = (const char*)obj_key_switch->ctr_key.controller_name;
  string switch_id = (const char*)obj_key_switch->switch_id;
  vector<string> vect_prim_keys;
  if (!controller_name.empty()) {
    vect_prim_keys.push_back(CTR_NAME);
  }
  if (!switch_id.empty()) {
    vect_prim_keys.push_back(SWITCH_ID);
  }

  PhyUtil::FillDbSchema(CTR_NAME, controller_name,
                        controller_name.length(), DATATYPE_UINT8_ARRAY_32,
                        vect_table_attr_schema);

  PhyUtil::FillDbSchema(SWITCH_ID, switch_id,
                        switch_id.length(), DATATYPE_UINT8_ARRAY_256,
                        vect_table_attr_schema);
  string value;
  PhyUtil::FillDbSchema(SWITCH_ALARM_STATUS, value,
                        value.length(), DATATYPE_UINT64,
                        vect_table_attr_schema);

  DBTableSchema kt_switch_dbtableschema;
  kt_switch_dbtableschema.set_table_name(UPPL_SWITCH_TABLE);
  kt_switch_dbtableschema.set_primary_keys(vect_prim_keys);
  row_list.push_back(vect_table_attr_schema);
  kt_switch_dbtableschema.set_row_list(row_list);

  // Call ODBCManager and Update
  ODBCM_RC_STATUS update_db_status =
      physical_layer->get_odbc_manager()->GetOneRow(
          (unc_keytype_datatype_t)data_type,
          kt_switch_dbtableschema);
  if (update_db_status != ODBCM_RC_SUCCESS) {
    // log error
    pfc_log_error("oper_status read operation failed");
    return UPPL_RC_ERR_DB_GET;
  }

  // read the oper status value
  list < vector<TableAttrSchema> > res_switch_row_list =
      kt_switch_dbtableschema.get_row_list();
  list < vector<TableAttrSchema> >::iterator res_switch_iter =
      res_switch_row_list.begin();

  // populate IPC value structure based on the response recevied from DB
  for (; res_switch_iter!= res_switch_row_list.end(); ++res_switch_iter) {
    vector<TableAttrSchema> res_switch_table_attr_schema = (*res_switch_iter);
    vector<TableAttrSchema>:: iterator vect_switch_iter =
        res_switch_table_attr_schema.begin();
    for (; vect_switch_iter != res_switch_table_attr_schema.end();
        ++vect_switch_iter) {
      // populate values from switch_table
      TableAttrSchema tab_schema = (*vect_switch_iter);
      string attr_name = tab_schema.table_attribute_name;
      string attr_value;
      if (attr_name == "alarms_status") {
        PhyUtil::GetValueFromDbSchema(tab_schema, attr_value,
                                      DATATYPE_UINT64);
        alarms_status = atol(attr_value.c_str());
        pfc_log_info("alarms_status: %" PFC_PFMT_u64, alarms_status);
        break;
      }
    }
  }
  return UPPL_RC_SUCCESS;
}

UpplReturnCode Kt_Switch::UpdateSwitchValidFlag(
    void *key_struct, void *val_struct,
    val_switch_st_t &val_switch_valid_st,
    unc_keytype_validflag_t new_valid_val) {
  UpplReturnCode return_code = UPPL_RC_SUCCESS;
  vector<void *> vect_switch_key;
  vector<void *> vect_switch_val;
  vect_switch_key.push_back(key_struct);
  return_code = ReadInternal(vect_switch_key, vect_switch_val,
                             UNC_DT_STATE, UNC_OP_READ);
  if (return_code != UPPL_RC_SUCCESS) {
    return return_code;
  }
  val_switch_st_t *obj_val_switch =
      reinterpret_cast<val_switch_st_t *>(val_struct);
  val_switch_st_t *obj_new_val_switch =
      reinterpret_cast<val_switch_st_t *>(vect_switch_val[0]);
  if (obj_new_val_switch == NULL) {
    pfc_log_debug("update switch valid ret null val");
    return return_code;
  }
  if (new_valid_val == UNC_VF_VALID) {
    // new_valid is valid then return the val st with valid arr
    val_switch_valid_st = *obj_new_val_switch;
    delete obj_new_val_switch;
    key_switch_t *switch_key =
        reinterpret_cast<key_switch_t*> (vect_switch_key[0]);
    if (switch_key != NULL) {
      delete switch_key;
    }
    return return_code;
  }

  if (obj_val_switch != NULL) {
    // new_valid is invalid update the particular attribute
    uint32_t operation_type = UNC_OP_UPDATE;
    stringstream ss_new_valid;

    // desc
    unsigned int valid_val = PhyUtil::uint8touint(
        obj_val_switch->switch_val.valid[kIdxSwitchDescription]);
    if (PhyUtil::IsValidValue(operation_type, valid_val) == true) {
      ss_new_valid << new_valid_val;
    } else {
      pfc_log_debug(
          "invalid value for switch_description ignore the value");
      ss_new_valid <<
          obj_new_val_switch->switch_val.valid[kIdxSwitchDescription];
    }
    // valid val of model
    valid_val = PhyUtil::uint8touint(
        obj_val_switch->switch_val.valid[kIdxSwitchModel]);
    if (PhyUtil::IsValidValue(operation_type, valid_val) == true) {
      ss_new_valid << new_valid_val;
    } else {
      pfc_log_debug("invalid value for model ignore the value");
      ss_new_valid << obj_new_val_switch->switch_val.valid[kIdxSwitchModel];
    }
    // valid val of ip
    valid_val = PhyUtil::uint8touint(
        obj_val_switch->switch_val.valid[kIdxSwitchIPAddress]);
    if (PhyUtil::IsValidValue(operation_type, valid_val) == true) {
      ss_new_valid << new_valid_val;
    } else {
      pfc_log_debug("invalid value for ip ignore the value");
      ss_new_valid <<
          obj_new_val_switch->switch_val.valid[kIdxSwitchIPAddress];
    }
    // valid val of ipv6
    valid_val = PhyUtil::uint8touint(
        obj_val_switch->switch_val.valid[kIdxSwitchIPV6Address]);
    if (PhyUtil::IsValidValue(operation_type, valid_val) == true) {
      ss_new_valid << new_valid_val;
    } else {
      pfc_log_debug("invalid value for ipv6 ignore the value");
      ss_new_valid <<
          obj_new_val_switch->switch_val.valid[kIdxSwitchIPV6Address];
    }
    // valid val of SwitchAdminStatus
    valid_val = PhyUtil::uint8touint(
        obj_val_switch->switch_val.valid[kIdxSwitchAdminStatus]);
    if (PhyUtil::IsValidValue(operation_type, valid_val) == true) {
      ss_new_valid << new_valid_val;
    } else {
      pfc_log_debug("invalid value for adminstatus ignore the value");
      ss_new_valid <<
          obj_new_val_switch->switch_val.valid[kIdxSwitchAdminStatus];
    }
    // valid val of domain name
    valid_val = PhyUtil::uint8touint(
        obj_val_switch->switch_val.valid[kIdxSwitchDomainName]);
    if (PhyUtil::IsValidValue(operation_type, valid_val) == true) {
      ss_new_valid << new_valid_val;
    } else {
      ss_new_valid <<
          obj_new_val_switch->switch_val.valid[kIdxSwitchDomainName];
      pfc_log_debug("invalid value for DomainName ignore the value");
    }
    // valid val of oper_Status
    valid_val = PhyUtil::uint8touint(
        obj_val_switch->valid[kIdxSwitchOperStatus]);
    if (PhyUtil::IsValidValue(operation_type, valid_val) == true) {
      ss_new_valid << new_valid_val;
    } else {
      ss_new_valid << obj_new_val_switch->valid[kIdxSwitchOperStatus];
      pfc_log_debug("invalid value for operstatus ignore the value");
    }
    // valid val of manufacturer
    valid_val = PhyUtil::uint8touint(
        obj_val_switch->valid[kIdxSwitchManufacturer]);
    if (PhyUtil::IsValidValue(operation_type, valid_val) == true) {
      ss_new_valid << new_valid_val;
    } else {
      ss_new_valid << obj_new_val_switch->valid[kIdxSwitchManufacturer];
      pfc_log_debug("invalid value for manufacturer ignore the value");
    }
    // valid val of hardware
    valid_val = PhyUtil::uint8touint(
        obj_val_switch->valid[kIdxSwitchHardware]);
    if (PhyUtil::IsValidValue(operation_type, valid_val) == true) {
      ss_new_valid << new_valid_val;
    } else {
      ss_new_valid << obj_new_val_switch->valid[kIdxSwitchHardware];
      pfc_log_debug("invalid value for hardware ignore the value");
    }
    // valid val of software
    valid_val = PhyUtil::uint8touint(
        obj_val_switch->valid[kIdxSwitchSoftware]);
    if (PhyUtil::IsValidValue(operation_type, valid_val) == true) {
      ss_new_valid << new_valid_val;
    } else {
      ss_new_valid << obj_new_val_switch->valid[kIdxSwitchSoftware];
      pfc_log_debug("invalid value for software ignore the value");
    }
    // valid val of alarmstatus
    valid_val = PhyUtil::uint8touint(
        obj_val_switch->valid[kIdxSwitchAlarmStatus]);
    if (PhyUtil::IsValidValue(operation_type, valid_val) == true) {
      ss_new_valid << new_valid_val;
    } else {
      ss_new_valid << obj_new_val_switch->valid[kIdxSwitchAlarmStatus];
      pfc_log_debug("invalid value for alarmstatus ignore the value");
    }
    return_code = PopulateSchemaForValidFlag(
        key_struct,
        reinterpret_cast<void*> (&obj_new_val_switch),
        ss_new_valid.str().c_str());

    delete obj_new_val_switch;
    obj_new_val_switch = NULL;
    key_switch_t *switch_key = reinterpret_cast<key_switch_t*>
    (vect_switch_key[0]);
    if (switch_key != NULL) {
      delete switch_key;
      switch_key = NULL;
    }
  }
  return return_code;
}

/** PopulateSchemaForValidFlag
 *  * @Description : This function updates the valid flag value
 *  of the port
 *  * @param[in] : key_struct, value_struct
 *  * @return    : success/failure
 */
UpplReturnCode Kt_Switch::PopulateSchemaForValidFlag(void* key_struct,
                                                     void* val_struct,
                                                     string valid_new) {
  PhysicalLayer *physical_layer = PhysicalLayer::get_instance();
  key_switch_t *obj_key_switch=
      reinterpret_cast<key_switch_t*>(key_struct);
  TableAttrSchema kt_switch_table_attr_schema;
  vector<TableAttrSchema> vect_table_attr_schema;
  list < vector<TableAttrSchema> > row_list;
  vector<string> vect_prim_keys;
  vect_prim_keys.push_back(CTR_NAME);
  vect_prim_keys.push_back(SWITCH_ID);

  string switch_id = (const char*)obj_key_switch->switch_id;
  string controller_name = (const char*)obj_key_switch->ctr_key.controller_name;

  PhyUtil::FillDbSchema(CTR_NAME, controller_name,
                        controller_name.length(), DATATYPE_UINT8_ARRAY_32,
                        vect_table_attr_schema);

  PhyUtil::FillDbSchema(SWITCH_ID, switch_id,
                        switch_id.length(), DATATYPE_UINT8_ARRAY_32,
                        vect_table_attr_schema);

  pfc_log_debug("valid new val:%s", valid_new.c_str());
  PhyUtil::FillDbSchema(SWITCH_VALID, valid_new.c_str(),
                        11, DATATYPE_UINT8_ARRAY_11,
                        vect_table_attr_schema);

  DBTableSchema kt_switch_dbtableschema;
  kt_switch_dbtableschema.set_table_name(UPPL_SWITCH_TABLE);
  kt_switch_dbtableschema.set_primary_keys(vect_prim_keys);
  row_list.push_back(vect_table_attr_schema);
  kt_switch_dbtableschema.set_row_list(row_list);

  // Call ODBCManager and update
  ODBCM_RC_STATUS update_db_status =
      physical_layer->get_odbc_manager()->UpdateOneRow(
          UNC_DT_STATE, kt_switch_dbtableschema);
  if (update_db_status != ODBCM_RC_SUCCESS) {
    // log error
    pfc_log_error("domain id update operation failed");
    return UPPL_RC_ERR_DB_UPDATE;
  }
  return UPPL_RC_SUCCESS;
}

/** FrameValidValue
 * * @Description : This function converts the string value from db to uint8
 * * * @param[in] : Attribute value and val_ctr_st
 * * * @return    : Success or associated error code
 * */
void Kt_Switch::FrameValidValue(string attr_value,
                                val_switch_st &obj_val_switch) {
  obj_val_switch.valid[kIdxSwitch] = UNC_VF_VALID;
  for (unsigned int i = 0; i < attr_value.length() ; ++i) {
    unsigned int valid = attr_value[i];
    if (attr_value[i] >= 48) {
      valid = attr_value[i] - 48;
    }
    if (i < 6) {
      obj_val_switch.switch_val.valid[i] = valid;
    } else {
      obj_val_switch.valid[i-5] = valid;
    }
  }
  return;
}

/** GetSwitchValStructure
 * * @Description : This function reads the values given in val_switch structure
 * * @param[in] : DBTableSchema - DBtableschema associated with KT_Switch
 * key_struct - key instance of Kt_Switch
 * val_struct - val instance of Kt_Switch
 * operation_type - UNC_OP*
 * * @return    : UPPL_RC_SUCCESS or UPPL_RC_ERR*
 * */
void Kt_Switch::GetSwitchValStructure(
    val_switch_st_t *obj_val_switch,
    vector<TableAttrSchema> &vect_table_attr_schema,
    vector<string> &vect_prim_keys,
    uint8_t operation_type,
    val_switch_st_t *val_switch_valid_st,
    stringstream &valid) {
  uint16_t valid_val = 0, prev_db_val = 0;
  unsigned int valid_value_struct = UNC_VF_VALID;
  if (obj_val_switch != NULL) {
    valid_value_struct = PhyUtil::uint8touint(
        obj_val_switch->valid[kIdxSwitch]);
  }
  string value;
  // Description
  if (obj_val_switch != NULL && valid_value_struct == UNC_VF_VALID) {
    valid_val = PhyUtil::uint8touint(obj_val_switch->switch_val.
                                     valid[kIdxSwitchDescription]);
    value = (const char*)obj_val_switch->switch_val.description;
    if (operation_type == UNC_OP_UPDATE) {
      prev_db_val =
          PhyUtil::uint8touint(
              val_switch_valid_st->switch_val.valid[kIdxSwitchDescription]);
    }
  } else {
    valid_val = UPPL_NO_VAL_STRUCT;
  }

  PhyUtil::FillDbSchema(SWITCH_DESCRIPTION, value,
                        value.length(), DATATYPE_UINT8_ARRAY_128,
                        operation_type, valid_val, prev_db_val,
                        vect_table_attr_schema, vect_prim_keys, valid);
  value.clear();
  // model
  if (obj_val_switch != NULL && valid_value_struct == UNC_VF_VALID) {
    valid_val = PhyUtil::uint8touint(obj_val_switch->switch_val.
                                     valid[kIdxSwitchModel]);
    value = (const char*)obj_val_switch->switch_val.model;
    if (operation_type == UNC_OP_UPDATE) {
      prev_db_val =
          PhyUtil::uint8touint(
              val_switch_valid_st->switch_val.valid[kIdxSwitchModel]);
    }
  } else {
    valid_val = UPPL_NO_VAL_STRUCT;
  }
  PhyUtil::FillDbSchema(SWITCH_MODEL, value,
                        value.length(), DATATYPE_UINT8_ARRAY_16,
                        operation_type, valid_val, prev_db_val,
                        vect_table_attr_schema, vect_prim_keys, valid);
  value.clear();
  // Ip_address
  char *ip_value = new char[16];
  if (obj_val_switch != NULL && valid_value_struct == UNC_VF_VALID) {
    valid_val = PhyUtil::uint8touint(
        obj_val_switch->switch_val.
        valid[kIdxSwitchIPAddress]);
    pfc_log_info("ip_address : %d",
                 obj_val_switch->switch_val.ip_address.s_addr);
    inet_ntop(AF_INET, &obj_val_switch->switch_val.ip_address,
              ip_value, INET_ADDRSTRLEN);
    pfc_log_info("ip_address: %s", ip_value);
    if (operation_type == UNC_OP_UPDATE) {
      prev_db_val =
          PhyUtil::uint8touint(
              val_switch_valid_st->switch_val.valid[kIdxSwitchIPAddress]);
    }
  } else {
    valid_val = UPPL_NO_VAL_STRUCT;
  }
  PhyUtil::FillDbSchema(SWITCH_IP_ADDRESS, ip_value,
                        strlen(ip_value), DATATYPE_IPV4,
                        operation_type, valid_val, prev_db_val,
                        vect_table_attr_schema, vect_prim_keys, valid);
  delete []ip_value;

  // IPV6 Address
  ip_value = new char[16];
  if (obj_val_switch != NULL && valid_value_struct == UNC_VF_VALID) {
    valid_val = PhyUtil::uint8touint(obj_val_switch->switch_val.
                                     valid[kIdxSwitchIPV6Address]);
    inet_ntop(AF_INET6, &obj_val_switch->switch_val.ipv6_address,
              ip_value, INET6_ADDRSTRLEN);
    if (operation_type == UNC_OP_UPDATE) {
      prev_db_val =
          PhyUtil::uint8touint(
              val_switch_valid_st->switch_val.valid[kIdxSwitchIPV6Address]);
    }
  } else {
    valid_val = UPPL_NO_VAL_STRUCT;
  }
  PhyUtil::FillDbSchema(SWITCH_IPV6_ADDRESS, ip_value,
                        strlen(ip_value), DATATYPE_IPV6,
                        operation_type, valid_val, prev_db_val,
                        vect_table_attr_schema, vect_prim_keys, valid);
  delete []ip_value;
  value.clear();
  // Admin Status
  if (obj_val_switch != NULL && valid_value_struct == UNC_VF_VALID) {
    valid_val = PhyUtil::uint8touint(obj_val_switch->switch_val.
                                     valid[kIdxSwitchAdminStatus]);
    value = PhyUtil::uint8tostr(obj_val_switch->
                                switch_val.admin_status);
    if (operation_type == UNC_OP_UPDATE) {
      prev_db_val =
          PhyUtil::uint8touint(
              val_switch_valid_st->switch_val.valid[kIdxSwitchAdminStatus]);
    }
  } else {
    valid_val = UPPL_NO_VAL_STRUCT;
  }
  PhyUtil::FillDbSchema(SWITCH_ADMIN_STATUS, value,
                        value.length(), DATATYPE_UINT16,
                        operation_type, valid_val, prev_db_val,
                        vect_table_attr_schema, vect_prim_keys, valid);
  value.clear();
  // Domain Name
  if (obj_val_switch != NULL && valid_value_struct == UNC_VF_VALID) {
    valid_val = PhyUtil::uint8touint(obj_val_switch->switch_val.
                                     valid[kIdxSwitchDomainName]);
    value = (const char*)obj_val_switch->switch_val.domain_name;
    if (operation_type == UNC_OP_UPDATE) {
      prev_db_val =
          PhyUtil::uint8touint(
              val_switch_valid_st->switch_val.valid[kIdxSwitchDomainName]);
    }
  } else {
    valid_val = UPPL_NO_VAL_STRUCT;
  }
  PhyUtil::FillDbSchema(SWITCH_DOMAIN_NAME, value,
                        value.length(), DATATYPE_UINT8_ARRAY_32,
                        operation_type, valid_val, prev_db_val,
                        vect_table_attr_schema, vect_prim_keys, valid);
  return;
}

/** GetSwitchStateValStructure
 * * @Description : This function reads the values given in val_switch_st structure
 * * @param[in] : DBTableSchema - DBtableschema associated with KT_Switch
 * key_struct - key instance of Kt_Switch
 * val_struct - val instance of Kt_Switch
 * operation_type - UNC_OP*
 * * @return    : UPPL_RC_SUCCESS or UPPL_RC_ERR*
 * */
void Kt_Switch::GetSwitchStateValStructure(
    val_switch_st_t *obj_val_switch,
    vector<TableAttrSchema> &vect_table_attr_schema,
    vector<string> &vect_prim_keys,
    uint8_t operation_type,
    val_switch_st_t *val_switch_valid_st,
    stringstream &valid) {
  uint16_t valid_val = 0, prev_db_val = 0;
  string value;
  // oper status
  if (obj_val_switch != NULL) {
    valid_val = PhyUtil::uint8touint(obj_val_switch->
                                     valid[kIdxSwitchOperStatus]);
    if (valid_val == UNC_VF_VALID) {
      value = PhyUtil::uint8tostr(obj_val_switch->oper_status);
    }
    if (operation_type == UNC_OP_UPDATE) {
      prev_db_val =
          PhyUtil::uint8touint(
              val_switch_valid_st->switch_val.valid[kIdxSwitchOperStatus]);
    }
  } else {
    valid_val = UPPL_NO_VAL_STRUCT;
  }
  PhyUtil::FillDbSchema(SWITCH_OPER_STATUS, value,
                        value.length(), DATATYPE_UINT16,
                        operation_type, valid_val, prev_db_val,
                        vect_table_attr_schema, vect_prim_keys, valid);
  value.clear();
  // Manufacturer
  if (obj_val_switch != NULL) {
    valid_val = PhyUtil::uint8touint(obj_val_switch->
                                     valid[kIdxSwitchManufacturer]);
    if (valid_val == UNC_VF_VALID) {
      value = (const char*)obj_val_switch->manufacturer;
    }
    if (operation_type == UNC_OP_UPDATE) {
      prev_db_val =
          PhyUtil::uint8touint(
              val_switch_valid_st->switch_val.valid[kIdxSwitchManufacturer]);
    }
  } else {
    valid_val = UPPL_NO_VAL_STRUCT;
  }
  PhyUtil::FillDbSchema(SWITCH_MANUFACTURER, value,
                        value.length(), DATATYPE_UINT8_ARRAY_256,
                        operation_type, valid_val, prev_db_val,
                        vect_table_attr_schema, vect_prim_keys, valid);
  value.clear();
  // Hardware
  if (obj_val_switch != NULL) {
    valid_val = PhyUtil::uint8touint(obj_val_switch->
                                     valid[kIdxSwitchHardware]);
    if (valid_val == UNC_VF_VALID) {
      value = (const char*)obj_val_switch->hardware;
    }
    if (operation_type == UNC_OP_UPDATE) {
      prev_db_val =
          PhyUtil::uint8touint(
              val_switch_valid_st->switch_val.valid[kIdxSwitchHardware]);
    }
  } else {
    valid_val = UPPL_NO_VAL_STRUCT;
  }
  PhyUtil::FillDbSchema(SWITCH_HARDWARE, value,
                        value.length(), DATATYPE_UINT8_ARRAY_256,
                        operation_type, valid_val, prev_db_val,
                        vect_table_attr_schema, vect_prim_keys, valid);
  value.clear();
  // Software
  if (obj_val_switch != NULL) {
    valid_val = PhyUtil::uint8touint(obj_val_switch->
                                     valid[kIdxSwitchSoftware]);
    if (valid_val == UNC_VF_VALID) {
      value = (const char*)obj_val_switch->software;
    }
    if (operation_type == UNC_OP_UPDATE) {
      prev_db_val =
          PhyUtil::uint8touint(
              val_switch_valid_st->switch_val.valid[kIdxSwitchSoftware]);
    }
  } else {
    valid_val = UPPL_NO_VAL_STRUCT;
  }
  PhyUtil::FillDbSchema(SWITCH_SOFTWARE, value,
                        value.length(), DATATYPE_UINT8_ARRAY_256,
                        operation_type, valid_val, prev_db_val,
                        vect_table_attr_schema, vect_prim_keys, valid);
  value.clear();
  // alarms status
  if (obj_val_switch != NULL) {
    valid_val = PhyUtil::uint8touint(obj_val_switch->
                                     valid[kIdxSwitchAlarmStatus]);
    if (valid_val == UNC_VF_VALID) {
      value = PhyUtil::uint64tostr(obj_val_switch->alarms_status);
    }
    if (operation_type == UNC_OP_UPDATE) {
      prev_db_val =
          PhyUtil::uint8touint(
              val_switch_valid_st->switch_val.valid[kIdxSwitchAlarmStatus]);
    }
  } else {
    valid_val = UPPL_NO_VAL_STRUCT;
  }
  if (operation_type == UNC_OP_CREATE) {
    valid_val = UNC_VF_VALID;
  }
  PhyUtil::FillDbSchema(SWITCH_ALARM_STATUS, value,
                        value.length(), DATATYPE_UINT64,
                        operation_type, valid_val, prev_db_val,
                        vect_table_attr_schema, vect_prim_keys, valid);
  // valid
  valid_val = UPPL_NO_VAL_STRUCT;
  stringstream dummy_valid;
  PhyUtil::FillDbSchema(SWITCH_VALID, valid.str(),
                        valid.str().length(), DATATYPE_UINT8_ARRAY_11,
                        operation_type, valid_val, prev_db_val,
                        vect_table_attr_schema, vect_prim_keys, dummy_valid);
  return;
}
