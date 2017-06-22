package rpc;

import com.google.protobuf.BlockingService;
import proto.CalculatorProxy;

/**
 * Created by zhangkui.zk on 2017/6/21.
 */
public class ServerMain {
    public static void main(String[] args){
        BlockingService bockingService = CalculatorProxy.CalculatorProxyService
                .newReflectiveBlockingService(new CalculatorServerImpl());
        RpcServer server = new RpcServer(bockingService, 9000);
        server.run();
    }
}
