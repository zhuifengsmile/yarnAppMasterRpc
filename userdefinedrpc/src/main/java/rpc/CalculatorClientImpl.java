package rpc;

import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;
import proto.CalculatorProxyMsg;
import proxy.CalculatorClientProxy;

import java.lang.reflect.Proxy;

/**
 * Created by zhangkui.zk on 2017/6/21.
 */
public class CalculatorClientImpl implements Calculator {
    private Calculator proxy;
    public CalculatorClientImpl(int port){
        proxy = (Calculator)Proxy.newProxyInstance(
                this.getClass().getClassLoader(),
                this.getClass().getInterfaces(),
                new CalculatorClientProxy(port));
    }

    @Override
    public CalculatorProxyMsg.ProxyResponseProto add(RpcController controller, CalculatorProxyMsg.ProxyRequestProto request) throws ServiceException {
        return proxy.add(controller, request);
    }

    @Override
    public CalculatorProxyMsg.ProxyResponseProto minus(RpcController controller, CalculatorProxyMsg.ProxyRequestProto request) throws ServiceException {
        return proxy.minus(controller, request);
    }
}
