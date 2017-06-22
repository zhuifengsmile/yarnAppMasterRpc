package proxy;

import proto.CalculatorProxyMsg;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.Socket;

/**
 * Created by zhangkui.zk on 2016/10/22.
 */
public class CalculatorClientProxy implements InvocationHandler {
    private int port;
    public CalculatorClientProxy(int port){
        this.port = port;
    }
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Socket s = null;
        DataOutputStream out = null;
        DataInputStream in = null;
        CalculatorProxyMsg.ProxyResponseProto result = null;
        try {
            s= new Socket("localhost", port);
            out = new DataOutputStream(s.getOutputStream());
            in = new DataInputStream(s.getInputStream());
            CalculatorProxyMsg.ProxyRequestProto request = (CalculatorProxyMsg.ProxyRequestProto) args[1];
            byte [] bytes = request.toByteArray();
            out.writeInt(bytes.length);
            out.write(bytes);
            out.flush();

            int dataLen = in.readInt();
            byte[] data = new byte[dataLen];
            int count = in.read(data);
            if(count != dataLen){
                System.err.println("something bad happened!");
            }

            result = CalculatorProxyMsg.ProxyResponseProto.parseFrom(data);

        }catch(Exception e){
            e.printStackTrace();
            System.err.println(e.toString());
        }finally {
            try{
                in.close();
                out.close();
                s.close();
            }catch(IOException e){
                e.printStackTrace();
            }
        }
        return result;
    }
}
