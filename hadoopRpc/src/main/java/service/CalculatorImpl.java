package service;

import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;
import proto.CalculatorMsg;

/**
 * Created by zhangkui.zk on 2017/6/21.
 */
public class CalculatorImpl implements Calculator {
    @Override
    public CalculatorMsg.ResponseProto add(RpcController controller, CalculatorMsg.RequestProto request) throws ServiceException {
        int num1 = request.getNum1();
        int num2 = request.getNum2();
        int result = num1 + num2;
        return  CalculatorMsg.ResponseProto.newBuilder().setResult(result).build();
    }

    @Override
    public CalculatorMsg.ResponseProto minus(RpcController controller, CalculatorMsg.RequestProto request) throws ServiceException {
        int num1 = request.getNum1();
        int num2 = request.getNum2();
        int result = num1 - num2;
        return  CalculatorMsg.ResponseProto.newBuilder().setResult(result).build();
    }
}
