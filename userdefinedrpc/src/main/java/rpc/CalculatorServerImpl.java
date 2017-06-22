package rpc;

import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;
import proto.CalculatorProxyMsg;

/**
 * Created by zhangkui.zk on 2017/6/21.
 */
public class CalculatorServerImpl implements Calculator {
    @Override
    public CalculatorProxyMsg.ProxyResponseProto add(RpcController controller, CalculatorProxyMsg.ProxyRequestProto request) throws ServiceException {
        int num1 = request.getNum1();
        int num2 = request.getNum2();
        int result = num1 + num2;
        CalculatorProxyMsg.ProxyResponseProto responseProto =
                CalculatorProxyMsg.ProxyResponseProto.newBuilder()
                .setResult(result)
                .build();
        return responseProto;
    }

    @Override
    public CalculatorProxyMsg.ProxyResponseProto minus(RpcController controller, CalculatorProxyMsg.ProxyRequestProto request) throws ServiceException {
        int num1 = request.getNum1();
        int num2 = request.getNum2();
        int result = num1 - num2;
        CalculatorProxyMsg.ProxyResponseProto responseProto =
                CalculatorProxyMsg.ProxyResponseProto.newBuilder()
                        .setResult(result)
                        .build();
        return responseProto;
    }
}
