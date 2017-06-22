package rpc;


import com.google.protobuf.BlockingService;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import proto.CalculatorProxyMsg;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by zhangkui.zk on 2016/10/22.
 */
public class RpcServer implements Runnable {
    private BlockingService impl;
    private int port;

    public RpcServer(BlockingService impl, int port){
        this.impl = impl;
        this.port = port;
    }

    @Override
    public void run(){
        Socket clientSocket = null;
        ServerSocket ss = null;
        DataOutputStream outputStream = null;
        DataInputStream inputStream = null;
        try {
            ss = new ServerSocket(port);
        }catch(IOException e){
        }
        int testCount = 10; //进行10次计算后就退出

        while(testCount-- > 0){
            try {
                clientSocket = ss.accept();
                outputStream = new DataOutputStream(clientSocket.getOutputStream());
                inputStream = new DataInputStream(clientSocket.getInputStream());
                int dataLen = inputStream.readInt();
                byte[] dataBuffer = new byte[dataLen];
                int readCount = inputStream.read(dataBuffer);
                byte[] result = processOneRpc(dataBuffer);

                outputStream.writeInt(result.length);
                outputStream.write(result);
                outputStream.flush();
            }catch(Exception e){
            }
        }
        try {
            outputStream.close();
            inputStream.close();
            ss.close();
        }catch(Exception e){
        }

    }

    public byte[] processOneRpc (byte[] data) throws Exception {
        CalculatorProxyMsg.ProxyRequestProto requestProto = CalculatorProxyMsg.ProxyRequestProto.parseFrom(data);
        String methodName = requestProto.getMethodName();
        Descriptors.MethodDescriptor methodDescriptor = impl.getDescriptorForType().findMethodByName(methodName);
        Message message = impl.callBlockingMethod(methodDescriptor, null, requestProto);
        return message.toByteArray();
    }
}
