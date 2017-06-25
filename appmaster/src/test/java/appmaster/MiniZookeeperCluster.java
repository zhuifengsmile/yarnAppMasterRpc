package appmaster;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.zookeeper.MiniZooKeeperCluster;

import java.io.File;
import java.io.IOException;
import java.util.Random;

public class MiniZookeeperCluster {
    private static final Log LOG = LogFactory.getLog(MiniZookeeperCluster.class);

    private MiniZooKeeperCluster zkCluster;
    private File baseDir;
    private int port;

    public MiniZookeeperCluster() throws IOException {
        String randomString = String.valueOf(new Random().nextInt(65536));
        baseDir = new File(randomString);
        if (!baseDir.mkdir()) {
            throw new IOException("make dir failed, dir:" + baseDir.getAbsolutePath());
        }
        Configuration conf = HBaseConfiguration.create();
        zkCluster = new MiniZooKeeperCluster(conf);
        try {
            port = zkCluster.startup(baseDir);
            LOG.info("start mini zookeeper cluster, port:" + port);
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    public void shutdown() {
        LOG.info("stop mini zookeeper cluster");
        try {
            zkCluster.shutdown();
        } catch (IOException e) {
            LOG.error("shutdown failed", e);
        }
        try {
            FileUtils.deleteDirectory(baseDir);
        } catch (IOException e) {
            LOG.error("delete dir failed, dir:" + baseDir.getAbsolutePath(), e);
        }
    }

    public String getSpec() {
        return getSpec("");
    }

    public String getSpec(String root) {
        if (root.isEmpty()) {
            return "127.0.0.1:" + port;
        } else {
            return "127.0.0.1:" + port + "/" + root;
        }
    }

}