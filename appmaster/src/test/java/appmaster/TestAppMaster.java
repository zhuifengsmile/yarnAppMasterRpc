/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package appmaster;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.util.JarFinder;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.server.MiniYARNCluster;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.capacity.CapacityScheduler;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import utils.Constants;

import java.io.*;
import java.net.InetAddress;
import java.net.URL;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;


public class TestAppMaster {

  private static final Log LOG = LogFactory.getLog(TestAppMaster.class);

  MiniZookeeperCluster zkCluster;
  protected MiniYARNCluster yarnCluster = null;
  protected YarnConfiguration conf = null;
  private static final int NUM_NMS = 1;
  private String rpcConfig;

  protected final static String APPMASTER_JAR = JarFinder.getJar(ApplicationMaster.class);

  @Before
  public void setup() throws Exception {
    setupZk();
    setupInternal(NUM_NMS);
  }

  protected void setupZk() throws Exception {
    zkCluster = new MiniZookeeperCluster();
    URL url = Thread.currentThread().getContextClassLoader().getResource("rpc_service.xml");
    if (url == null) {
      throw new RuntimeException("Could not find 'rpc_service.xml' dummy file in classpath");
    }
    rpcConfig = url.getPath();
    Configuration conf = new Configuration();
    conf.clear();
    conf.set(Constants.RPC_SERVICE_ZK_ADDR, zkCluster.getSpec());
    conf.set(Constants.RPC_SERVICE_ZK_PATH, "/rpc/master");
    conf.setInt(Constants.RPC_SERVICE_PORT, 9010);
    //write the document to a buffer (not directly to the file, as that
    //can cause the file being written to get read -which will then fail.
    ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
    conf.writeXml(bytesOut);
    bytesOut.close();
    //write the bytes to the file in the classpath
    OutputStream os = new FileOutputStream(new File(url.getPath()));
    os.write(bytesOut.toByteArray());
    os.close();
    System.out.println("zk init succeed");
  }

  protected void setupInternal(int numNodeManager) throws Exception {

    LOG.info("Starting up YARN cluster");
    
    conf = new YarnConfiguration();
    conf.setInt(YarnConfiguration.RM_SCHEDULER_MINIMUM_ALLOCATION_MB, 128);
    conf.set("yarn.log.dir", "target");
    conf.set(YarnConfiguration.RM_SCHEDULER, CapacityScheduler.class.getName());
    
    if (yarnCluster == null) {
      yarnCluster =
          new MiniYARNCluster(TestAppMaster.class.getSimpleName(), 1,
              numNodeManager, 1, 1, true);
      yarnCluster.init(conf);
      
      yarnCluster.start();
      
      waitForNMsToRegister();
      
      URL url = Thread.currentThread().getContextClassLoader().getResource("yarn-site.xml");
      if (url == null) {
        throw new RuntimeException("Could not find 'yarn-site.xml' dummy file in classpath");
      }
      Configuration yarnClusterConfig = yarnCluster.getConfig();
      yarnClusterConfig.set("yarn.application.classpath", new File(url.getPath()).getParent());
      //write the document to a buffer (not directly to the file, as that
      //can cause the file being written to get read -which will then fail.
      ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
      yarnClusterConfig.writeXml(bytesOut);
      bytesOut.close();
      //write the bytes to the file in the classpath
      OutputStream os = new FileOutputStream(new File(url.getPath()));
      os.write(bytesOut.toByteArray());
      os.close();
    }
  }

  @After
  public void tearDown() throws IOException {
    if (zkCluster != null) {
      try {
        zkCluster.shutdown();
      } finally {
        zkCluster = null;
      }
    }

    if (yarnCluster != null) {
      try {
        yarnCluster.stop();
      } finally {
        yarnCluster = null;
      }
    }
  }

  @Test
  public void testDSShell() throws Exception {
    String[] args = {
        "--jar",
        APPMASTER_JAR,
        "--master_memory",
        "512",
        "--master_vcores",
        "2",
            "-files",
            rpcConfig
    };

    LOG.info("Initializing DS Client");
    final Client client = new Client(new Configuration(yarnCluster.getConfig()));
    boolean initSuccess = client.init(args);
    Assert.assertTrue(initSuccess);
    LOG.info("Running DS Client");
    final AtomicBoolean result = new AtomicBoolean(false);
    Thread t = new Thread() {
      public void run() {
        try {
          result.set(client.run());
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    };
    t.start();

    YarnClient yarnClient = YarnClient.createYarnClient();
    yarnClient.init(new Configuration(yarnCluster.getConfig()));
    yarnClient.start();
    String hostName = NetUtils.getHostname();

    boolean verified = false;
    String errorMessage = "";
    while(!verified) {
      List<ApplicationReport> apps = yarnClient.getApplications();
      if (apps.size() == 0 ) {
        Thread.sleep(10);
        continue;
      }
      ApplicationReport appReport = apps.get(0);
      if(appReport.getHost().equals("N/A")) {
        Thread.sleep(10);
        continue;
      }
      errorMessage =
          "Expected host name to start with '" + hostName + "', was '"
              + appReport.getHost() + "'. Expected rpc port to be '-1', was '"
              + appReport.getRpcPort() + "'.";
      if (checkHostname(appReport.getHost()) && appReport.getRpcPort() == -1) {
        verified = true;
      }
      if (appReport.getYarnApplicationState() == YarnApplicationState.FINISHED) {
        break;
      }
    }
    Assert.assertTrue(errorMessage, verified);
    t.join();
    LOG.info("Client run completed. Result=" + result);
    Assert.assertTrue(result.get());
  }

  /*
   * NetUtils.getHostname() returns a string in the form "hostname/ip".
   * Sometimes the hostname we get is the FQDN and sometimes the short name. In
   * addition, on machines with multiple network interfaces, it runs any one of
   * the ips. The function below compares the returns values for
   * NetUtils.getHostname() accounting for the conditions mentioned.
   */
  private boolean checkHostname(String appHostname) throws Exception {

    String hostname = NetUtils.getHostname();
    if (hostname.equals(appHostname)) {
      return true;
    }

    Assert.assertTrue("Unknown format for hostname " + appHostname,
      appHostname.contains("/"));
    Assert.assertTrue("Unknown format for hostname " + hostname,
      hostname.contains("/"));

    String[] appHostnameParts = appHostname.split("/");
    String[] hostnameParts = hostname.split("/");

    return (compareFQDNs(appHostnameParts[0], hostnameParts[0]) && checkIPs(
      hostnameParts[0], hostnameParts[1], appHostnameParts[1]));
  }

  private boolean compareFQDNs(String appHostname, String hostname)
      throws Exception {
    if (appHostname.equals(hostname)) {
      return true;
    }
    String appFQDN = InetAddress.getByName(appHostname).getCanonicalHostName();
    String localFQDN = InetAddress.getByName(hostname).getCanonicalHostName();
    return appFQDN.equals(localFQDN);
  }

  private boolean checkIPs(String hostname, String localIP, String appIP)
      throws Exception {

    if (localIP.equals(appIP)) {
      return true;
    }
    boolean appIPCheck = false;
    boolean localIPCheck = false;
    InetAddress[] addresses = InetAddress.getAllByName(hostname);
    for (InetAddress ia : addresses) {
      if (ia.getHostAddress().equals(appIP)) {
        appIPCheck = true;
        continue;
      }
      if (ia.getHostAddress().equals(localIP)) {
        localIPCheck = true;
      }
    }
    return (appIPCheck && localIPCheck);

  }



  protected void waitForNMsToRegister() throws Exception {
    int sec = 60;
    while (sec >= 0) {
      if (yarnCluster.getResourceManager().getRMContext().getRMNodes().size() 
          >= NUM_NMS) {
        break;
      }
      Thread.sleep(1000);
      sec--;
    }
  }

  @Test(timeout=90000)
  public void testDebugFlag() throws Exception {
    String[] args = {
        "--jar",
        APPMASTER_JAR,
        "--master_memory",
        "512",
        "--master_vcores",
        "2",
        "--debug"
    };

    LOG.info("Initializing DS Client");
    Client client = new Client(new Configuration(yarnCluster.getConfig()));
    Assert.assertTrue(client.init(args));
    LOG.info("Running DS Client");
    Assert.assertTrue(client.run());
  }
}

