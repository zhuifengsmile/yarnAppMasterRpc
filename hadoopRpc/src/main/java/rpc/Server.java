package rpc;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.ipc.ProtobufRpcEngine;
import org.apache.hadoop.ipc.RPC;
import proto.CalculatorPb;
import service.Calculator;
import service.CalculatorImpl;

import java.io.IOException;

/**
 * Created by zhangkui.zk on 2017/6/21.
 */
public class Server {
    public static void main(String[] args) throws IOException {
        Configuration conf = new Configuration();
        RPC.setProtocolEngine(conf, Calculator.class, ProtobufRpcEngine.class);
        RPC.Server server = new RPC.Builder(conf)
                .setBindAddress("localhost").setPort(9000)
                .setProtocol(Calculator.class)
                .setInstance(
                        CalculatorPb.CalculatorService.newReflectiveBlockingService(new CalculatorImpl()))
                .setNumHandlers(1)
                .build();
        server.start();
        System.out.println("server has been started");
    }
}
