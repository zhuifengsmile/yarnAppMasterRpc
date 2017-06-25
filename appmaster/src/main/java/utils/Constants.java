package utils;

import org.apache.curator.RetryPolicy;
import org.apache.curator.retry.ExponentialBackoffRetry;

/**
 * Created by zhangkui.zk on 2017/6/22.
 */
public class Constants {
    public static final String RPC_SERVICE_FILE = "rpc_service.xml";
    public static final String RPC_SERVICE_PORT = "rpc.service.listen.port";
    public static final String RPC_SERVICE_ZK_ADDR = "rpc.service.zk.addr";
    public static final String RPC_SERVICE_ZK_PATH = "rpc.service.zk.path";

    //zk
    private final static int DEFAULT_ZK_RETRY_NUM = 10;
    private final static int DEFAULT_ZK_SLEEP_MS = 1000;
    public final static int DEFAULT_ZK_SESSION_TIMEOUT = 120000; // in ms
    public final static int DEFAULT_ZK_CONNECT_TIMEOUT = 3000;  // in ms
    public final static RetryPolicy DEFAULT_RETRY_POLICY = new ExponentialBackoffRetry(
            DEFAULT_ZK_SLEEP_MS,
            DEFAULT_ZK_RETRY_NUM);
}
