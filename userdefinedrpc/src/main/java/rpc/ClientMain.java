package rpc;

import com.google.protobuf.ServiceException;
import proto.CalculatorProxyMsg;

/**
 * Created by zhangkui.zk on 2017/6/21.
 */
public class ClientMain {
    private static Calculator calculator;

    public static void main(String[] args) throws ServiceException {
        calculator = new CalculatorClientImpl(9000);
        System.out.println(add(1, 2));
        System.out.println(minus(1, 2));
    }

    public static int add(int num1, int num2) throws ServiceException {
        CalculatorProxyMsg.ProxyRequestProto request = CalculatorProxyMsg.ProxyRequestProto
                .newBuilder()
                .setMethodName("add")
                .setNum1(num1)
                .setNum2(num2)
                .build();
        CalculatorProxyMsg.ProxyResponseProto result = calculator.add(null, request);
        return result.getResult();

    }

    public static int minus(int num1, int num2) throws ServiceException {
        CalculatorProxyMsg.ProxyRequestProto request = CalculatorProxyMsg.ProxyRequestProto
                .newBuilder()
                .setMethodName("minus")
                .setNum1(num1)
                .setNum2(num2)
                .build();
        CalculatorProxyMsg.ProxyResponseProto result = calculator.add(null, request);
        return result.getResult();

    }
}
