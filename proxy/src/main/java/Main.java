/**
 * Created by zhangkui.zk on 2017/6/21.
 */
public class Main {
    public static void main(String[] args){
        CaluculatorImpl caluculatorImpl = new CaluculatorImpl();
        CaluculatorInvocationHandler invocationHandler = new CaluculatorInvocationHandler(caluculatorImpl);
        Caluculator proxy = invocationHandler.getProxy();
        System.out.println(proxy.add(1, 2));
        System.out.println(proxy.minus(1, 2));

    }
}
