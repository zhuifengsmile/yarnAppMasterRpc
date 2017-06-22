/**
 * Created by zhangkui.zk on 2017/6/21.
 */
public class CaluculatorImpl implements Caluculator {
    @Override
    public int add(int num1, int num2) {
        return num1 + num2;
    }

    @Override
    public int minus(int num1, int num2) {
        return num1 - num2;
    }
}
