package appmaster;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.ipc.ProtobufRpcEngine;
import org.apache.hadoop.ipc.RPC;
import org.apache.hadoop.net.NetUtils;
import proto.EchoPb;
import service.EchoImpl;
import service.IEcho;
import utils.Preconditions;
import utils.StringUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import static utils.Constants.*;

/**
 * Created by zhangkui.zk on 2017/6/22.
 */
public class RpcServer{
    private static final Log LOG = LogFactory.getLog(RpcServer.class);

    private volatile boolean isRunning = true;

    private Configuration conf;
    private RPC.Server server;
    private int port;
    private String zkAddr;
    private String zkPath;
    private CuratorFramework zkClient;
    private InterProcessMutex zkLock;

    public RpcServer(Configuration conf){
        this.conf = conf;
        port = conf.getInt(RPC_SERVICE_PORT, -1);
        zkAddr = conf.get(RPC_SERVICE_ZK_ADDR, "");
        zkPath = conf.get(RPC_SERVICE_ZK_PATH, "");
        Preconditions.checkArgument(port != -1,
                "rpc_service.xml missing rpc.service.listen.port");
        Preconditions.checkArgument(StringUtil.isNotEmpty(zkAddr),
                "rpc_service.xml missing rpc.service.zk.addr");
        Preconditions.checkArgument(StringUtil.isNotEmpty(zkPath),
                "rpc_service.xml missing rpc.service.zk.path");
        zkClient = getZkClient(zkAddr);
    }
    public void run() throws IOException, InterruptedException{
        zkClient.start();
        RPC.setProtocolEngine(conf, IEcho.class, ProtobufRpcEngine.class);
        server = new RPC.Builder(conf).setBindAddress("localhost").setPort(port)
                .setProtocol(IEcho.class)
                .setInstance(EchoPb.EchoService.newReflectiveBlockingService(new EchoImpl(this)))
                .setNumHandlers(1)
                .setnumReaders(1)
                .build();
        registerRpcAddressToZk(NetUtils.getConnectAddress(server));
        server.start();
        while (isRunning) {
            Thread.sleep(5000);
            LOG.info("rcp server is running, sleep 5 secends");
        }
    }

    public void cancel(String why) {
        LOG.info("rpc service stop because: " + why);
        isRunning = false;
    }

    public void stop() throws Exception {
        if(null != server){
            server.stop();
            server = null;
        }


        if(null != zkLock){
            zkLock.release();
            zkLock = null;
        }
        if(null != zkClient){
            zkClient.close();
            zkClient = null;
        }
        LOG.info("rpc service stopped");
    }

    private CuratorFramework getZkClient(String zkAddress) {
        CuratorFramework zkClient = CuratorFrameworkFactory.newClient(zkAddress,
                DEFAULT_ZK_SESSION_TIMEOUT,
                DEFAULT_ZK_CONNECT_TIMEOUT,
                DEFAULT_RETRY_POLICY);
        zkClient.getConnectionStateListenable().addListener(new ZKConnectionStateListener());
        return zkClient;
    }

    class ZKConnectionStateListener implements ConnectionStateListener {

        @Override public void stateChanged(CuratorFramework client, ConnectionState newState) {
            if (newState == ConnectionState.LOST) {
                LOG.error("zk connection lost.");
                cancel("zk lock lost.");
            }
        }
    }

    protected void registerRpcAddressToZk(InetSocketAddress inetSocketAddress){
        try {
            zkLock = new InterProcessMutex(zkClient, zkPath);
            if (!zkLock.acquire(DEFAULT_ZK_CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException(" could not acquire the lock");
            }
            String hostSpec = inetSocketAddress.getHostName() + ":" + inetSocketAddress.getPort();
            zkClient.setData().forPath(zkPath, hostSpec.getBytes());
        } catch (Exception ex) {
            throw new IllegalStateException("register server address to zk failed.", ex);
        }
    }
}
