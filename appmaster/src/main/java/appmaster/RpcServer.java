package appmaster;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.ipc.RPC;
import proto.EchoPb;
import service.EchoImpl;
import service.IEcho;

import java.io.IOException;

/**
 * Created by zhangkui.zk on 2017/6/22.
 */
public class RpcServer{
    private static final Log LOG = LogFactory.getLog(RpcServer.class);

    private volatile boolean isRunning;

    private Configuration conf;
    private RPC.Server server;
    public RpcServer(Configuration conf){
        this.conf = conf;
    }
    public void run() throws IOException, InterruptedException{
        server = new RPC.Builder(conf).setBindAddress("localhost").setPort(9000)
                .setProtocol(IEcho.class)
                .setInstance(EchoPb.EchoService.newReflectiveBlockingService(new EchoImpl(this)))
                .setNumHandlers(1)
                .build();
        server.start();
        while (!isRunning) {
            Thread.sleep(1000);
        }
        LOG.info("rpc service stopped");
    }

    public void cancel(String why) {
        LOG.info("rpc service stop because: " + why);
        isRunning = false;
    }

    public void stop(){
        server.stop();
    }
}
