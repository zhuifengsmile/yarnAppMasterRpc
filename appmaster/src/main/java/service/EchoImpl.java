package service;

import appmaster.RpcServer;
import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;
import proto.EchoMsg;

/**
 * Created by zhangkui.zk on 2017/6/21.
 */
public class EchoImpl implements IEcho {
    private RpcServer rpcServer;
    public EchoImpl(RpcServer server){
        this.rpcServer = server;
    }
    @Override
    public EchoMsg.EchoResponseProto echo(RpcController controller, EchoMsg.EchoRequestProto request) throws ServiceException {
        String input = request.getInput();
        String output = "echo back: " + input;
        return EchoMsg.EchoResponseProto.newBuilder().setResult(output).build();
    }

    @Override
    public EchoMsg.CloseResponseProto close(RpcController controller, EchoMsg.CloseRequestProto request) throws ServiceException {
        rpcServer.cancel("client send close request");
        return EchoMsg.CloseResponseProto.newBuilder().build();
    }
}
