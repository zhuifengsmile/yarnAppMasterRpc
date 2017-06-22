import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Created by zhangkui.zk on 2017/6/21.
 */
public class CaluculatorInvocationHandler implements InvocationHandler {
    private Caluculator caluculator;

    public CaluculatorInvocationHandler(Caluculator caluculator){
        this.caluculator = caluculator;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        return method.invoke(caluculator, args);
    }

    public Caluculator getProxy(){
        return (Caluculator)Proxy.newProxyInstance(caluculator.getClass().getClassLoader(),
                caluculator.getClass().getInterfaces(), this);
    }
}
