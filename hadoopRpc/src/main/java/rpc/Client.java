package rpc;

import com.google.protobuf.ServiceException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.ipc.ProtobufRpcEngine;
import org.apache.hadoop.ipc.RPC;
import proto.CalculatorMsg;
import service.Calculator;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Created by zhangkui.zk on 2017/6/21.
 */
public class Client {
    public static void main(String[] args) throws IOException, ServiceException {
        InetSocketAddress addr = new InetSocketAddress("127.0.0.1",9000);
        Configuration conf = new Configuration();
        RPC.setProtocolEngine(conf, Calculator.class, ProtobufRpcEngine.class);
        Calculator calculator = RPC.getProxy(
                Calculator.class, RPC.getProtocolVersion(Calculator.class)
                , addr,conf);
        CalculatorMsg.RequestProto request = CalculatorMsg.RequestProto
                .newBuilder()
                .setNum1(1)
                .setNum2(2)
                .build();
        CalculatorMsg.ResponseProto responseProto = calculator.add(null, request);
        System.out.println("result is:"+responseProto.getResult());
        responseProto = calculator.minus(null, request);
        System.out.println("result is:"+responseProto.getResult());
    }
}
