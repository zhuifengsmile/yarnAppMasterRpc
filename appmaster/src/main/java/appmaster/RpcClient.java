package appmaster;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.ipc.ProtobufRpcEngine;
import org.apache.hadoop.ipc.RPC;
import proto.EchoMsg.*;
import service.IEcho;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;

import static utils.Constants.*;

/**
 * Created by zhangkui.zk on 2017/6/21.
 */
public class RpcClient {
    private static final Log LOG = LogFactory.getLog(RpcServer.class);
    public static void main(String[] args) throws Exception {
        LOG.info("start RpcClient");
        InetSocketAddress addr = getRpcAddr();
        Configuration conf = new Configuration();
        RPC.setProtocolEngine(conf, IEcho.class, ProtobufRpcEngine.class);
        IEcho iEcho = RPC.getProxy(
                IEcho.class, RPC.getProtocolVersion(IEcho.class)
                , addr,conf);
        EchoRequestProto echoRequestProto = EchoRequestProto.newBuilder()
                .setInput("hello world")
                .build();
        EchoResponseProto echoResponseProto = iEcho.echo(null, echoRequestProto);
        System.out.println("result is:"+echoResponseProto.getResult());

        CloseRequestProto closeRequestProto = CloseRequestProto.newBuilder().build();
        iEcho.close(null, closeRequestProto);
        LOG.info("stop RpcClient");
    }

    private static CuratorFramework getZkClient(String zkAddress) {
        CuratorFramework zkClient = CuratorFrameworkFactory.newClient(zkAddress,
                DEFAULT_ZK_SESSION_TIMEOUT,
                DEFAULT_ZK_CONNECT_TIMEOUT,
                DEFAULT_RETRY_POLICY);
        return zkClient;
    }

    private static InetSocketAddress getRpcAddr() throws Exception {
        //need use  target/test-classes/rpc_service.xml rpc.service.zk.addr
        String zkAddress = "127.0.0.1:51676";
        CuratorFramework zkClient = getZkClient(zkAddress);
        zkClient.start();
        String rpcAddr = new String(zkClient.getData().forPath("/rpc/master"), Charset.forName("UTF-8"));
        String masterHost;
        int masterPort;
        try {
            String[] split = rpcAddr.split(":");
            masterHost = split[0];
            masterPort = Integer.parseInt(split[1]);
        } catch (Exception e) {
            throw new IOException("invalid master address:" + rpcAddr, e);
        }
        InetSocketAddress serverAddress = new InetSocketAddress(masterHost, masterPort);
        return serverAddress;
    }
}
