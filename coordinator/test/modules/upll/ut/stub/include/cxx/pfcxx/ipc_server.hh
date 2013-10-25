/*
 * Copyright (c) 2012-2013 NEC Corporation
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */

#include <pfc/ipc.h>
#include <netinet/in.h>
#include <string>
#include <map>
#include <list>
#include <vector>
#include <iostream>

#ifndef _PFCXX_IPC_SERVER_HH
#define _PFCXX_IPC_SERVER_HH

namespace pfc {
namespace core {
namespace ipc {

class ClientSession;
class ServerCallback;
class ServerEvent;

/*
 * C++ wrapper for IPC server session instance.
 */
class ServerSession
{
    friend class  ::pfc::core::ipc::ClientSession;
    friend class  ::pfc::core::ipc::ServerEvent;

public:
    /*
     * Constructor.
     */
    ServerSession();

    /*
     * Destructor.
     */
    virtual ~ServerSession(){

    }

    /*
     * Instance methods.
     */
    int   setTimeout(const pfc_timespec_t *timeout=NULL);
    int   getClientAddress(pfc_ipccladdr_t &claddr);

    int   addOutput(int8_t data);
    int   addOutputInt8(int8_t data);
    int   addOutput(uint8_t data);
    int   addOutputUint8(uint8_t data);
    int   addOutput(int16_t data);
    int   addOutputInt16(int16_t data);
    int   addOutput(uint16_t data);
    int   addOutputUint16(uint16_t data);
    int   addOutput(int32_t data);
    int   addOutputInt32(int32_t data);
    int   addOutput(uint32_t data);
    int   addOutputUint32(uint32_t data);
    int   addOutput(int64_t data);
    int   addOutputInt64(int64_t data);
    int   addOutput(uint64_t data);
    int   addOutputUint64(uint64_t data);
    int   addOutput(float data);
    int   addOutputFloat(float data);
    int   addOutput(double data);
    int   addOutputDouble(double data);
    int   addOutput(struct in_addr &data);
    int   addOutput(struct in6_addr &data);
    int   addOutput(const char *data);
    int   addOutput(const std::string &data);
    int   addOutput(const uint8_t *data, uint32_t length);
    int   addOutput(const pfc_ipcstdef_t &def, pfc_cptr_t data);
    int   addOutput(void);


    int   getArgument(uint32_t index, int8_t &data);
    int   getArgument(uint32_t index, uint8_t &data);
    int   getArgument(uint32_t index, int16_t &data);
    int   getArgument(uint32_t index, uint16_t &data);
    int   getArgument(uint32_t index, int32_t &data);
    int   getArgument(uint32_t index, uint32_t &data);
    int   getArgument(uint32_t index, int64_t &data);
    int   getArgument(uint32_t index, uint64_t &data);
    int   getArgument(uint32_t index, float &data);
    int   getArgument(uint32_t index, double &data);
    int   getArgument(uint32_t index, struct in_addr &data);
    int   getArgument(uint32_t index, struct in6_addr &data);
    int   getArgument(uint32_t index, const char *&data);
    int   getArgument(uint32_t index, const uint8_t *&data, uint32_t &length);
    int   getArgument(uint32_t index, const pfc_ipcstdef_t &def,
                      pfc_ptr_t datap);

    uint32_t getArgCount(void);
    int   getArgType(uint32_t index, pfc_ipctype_t &type);
    int   getArgStructName(uint32_t index, const char *&name);
    int   getArgStructName(uint32_t index, std::string &name);

    int   setCallback(pfc_ipcsrvcb_type_t type, ServerCallback *cbp);
    void  unsetCallback(pfc_ipcsrvcb_type_t type);
    void  clearCallbacks(void);

    //stub functions
    static void stub_setArgCount(uint32_t argCount);
    static void stub_setArgStructName(uint32_t index, std::string &name);
    static void stub_setArgument(int result);
    static void stub_setArgument( uint32_t index,uint32_t value );
    static void clearStubData();
    static void stub_setArgType(uint32_t index, pfc_ipctype_t ipctype);
    static void stub_setAddOutput(int result);
    static void stub_setAddOutput(uint32_t value);

private:
    static std::map<uint32_t,pfc_ipctype_t > arg_parameters;
    static std::map<uint32_t,std::string> structNameMap;
    static std::map<uint32_t,uint32_t> arg_map;
    static std::vector<uint32_t> add_output_list;
    static bool addOutPut_;
    static int result_;
    static uint32_t argCount_;
};

class ServerEvent
    : public ServerSession
{
public:
    ServerEvent(const char *name, pfc_ipcevtype_t type, int &err)
        : ServerSession()
    {
    	std::cout<<" ServerEvent(const char *name, pfc_ipcevtype_t type, int &err) "<<std::endl;
        err = serverEventErr_;
    }

    ServerEvent(std::string &name, pfc_ipcevtype_t type, int &err)
        : ServerSession()
    {
    	std::cout<<" ServerEvent(std::string &name, pfc_ipcevtype_t type, int &err)"<<std::endl;
        err = serverEventErr_;
    }

    ServerEvent(uint8_t type, int &err)
           : ServerSession()
    {
    	err = serverEventErr_;
    }

    ~ServerEvent()
    {

    }

    inline int
    post(void)
    {
    	std::cout<<" post(void) "<<std::endl;
        return postResult_;
    }

    inline int
    postTo(pfc_ipccladdr_t &claddr)
    {

        return 0;
    }

    static void clearStubData();
    static void stub_setserverEventErr(int err);
    static void stub_setPostResult(int result);
private:
    static int serverEventErr_;
    static int postResult_;
};

class ServerCallback
{
public:

    ServerCallback(ServerSession &sess) : _sess(&sess), _refcnt(0) {}
    virtual ~ServerCallback();
    virtual void  callback(pfc_ipcsrvcb_type_t type) = 0;
    inline ServerSession &
    getSession(void)
    {
        return *_sess;
    }

private:
    ServerSession  *_sess;
    uint32_t  _refcnt;
};

}
}
}

#endif  /* !_PFCXX_IPC_SERVER_HH */
