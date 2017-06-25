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

import org.apache.commons.cli.*;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceStability;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.io.DataOutputBuffer;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.yarn.api.ApplicationClientProtocol;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.api.ApplicationConstants.Environment;
import org.apache.hadoop.yarn.api.protocolrecords.GetNewApplicationResponse;
import org.apache.hadoop.yarn.api.protocolrecords.KillApplicationRequest;
import org.apache.hadoop.yarn.api.records.*;
import org.apache.hadoop.yarn.api.records.timeline.TimelineDomain;
import org.apache.hadoop.yarn.client.api.TimelineClient;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.client.api.YarnClientApplication;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.util.ConverterUtils;
import org.apache.hadoop.yarn.util.timeline.TimelineUtils;
import utils.Preconditions;
import utils.StringUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

public class Client {

  private static final Log LOG = LogFactory.getLog(Client.class);
  
  // Configuration
  private Configuration conf;
  private YarnClient yarnClient;
  // Application master specific info to register a new Application with RM/ASM
  private String appName = "";
  // App master priority
  private int amPriority = 0;
  // Queue for App master
  private String amQueue = "";
  // Amt. of memory resource to request for to run the App Master
  private int amMemory = 10; 
  // Amt. of virtual core resource to request for to run the App Master
  private int amVCores = 1;

  // Application master jar file
  private String appMasterJar = ""; 
  // Main class to invoke application master
  private final String appMasterMainClass;

  //rpc server.xml file
  private String files = "";
  private static final  String serverConfPath = "server.xml";

  // Debug flag
  boolean debugFlag = false;

  // Command line options
  private Options opts;

  private static final String appMasterJarPath = "AppMaster.jar";

  /**
   * @param args Command line arguments 
   */
  public static void main(String[] args) {
    boolean result = false;
    try {
      Client client = new Client();
      LOG.info("Initializing Client");
      try {
        boolean doRun = client.init(args);
        if (!doRun) {
          System.exit(0);
        }
      } catch (IllegalArgumentException e) {
        System.err.println(e.getLocalizedMessage());
        client.printUsage();
        System.exit(-1);
      }
      result = client.run();
    } catch (Throwable t) {
      LOG.fatal("Error running Client", t);
      System.exit(1);
    }
    if (result) {
      LOG.info("Application completed successfully");
      System.exit(0);			
    } 
    LOG.error("Application failed to complete successfully");
    System.exit(2);
  }

  /**
   */
  public Client(Configuration conf) throws Exception  {
    this(
      ApplicationMaster.class.getName(),
      conf);
  }

  Client(String appMasterMainClass, Configuration conf) {
    this.conf = conf;
    this.appMasterMainClass = appMasterMainClass;
    yarnClient = YarnClient.createYarnClient();
    yarnClient.init(conf);
    opts = new Options();
    opts.addOption("appname", true, "Application Name. Default value - DistributedShell");
    opts.addOption("priority", true, "Application Priority. Default 0");
    opts.addOption("queue", true, "RM Queue in which this application is to be submitted");
    opts.addOption("master_memory", true, "Amount of memory in MB to be requested to run the application master");
    opts.addOption("master_vcores", true, "Amount of virtual cores to be requested to run the application master");
    opts.addOption("jar", true, "Jar file containing the application master");
    opts.addOption("files", true, "rpc service config files");
    opts.addOption("debug", false, "Dump out debug information");
    opts.addOption("help", false, "Print usage");
  }

  /**
   */
  public Client() throws Exception  {
    this(new YarnConfiguration());
  }

  /**
   * Helper function to print out usage
   */
  private void printUsage() {
    new HelpFormatter().printHelp("Client", opts);
  }

  /**
   * Parse command line options
   * @param args Parsed command line options 
   * @return Whether the init was successful to run the client
   * @throws ParseException
   */
  public boolean init(String[] args) throws ParseException {

    CommandLine cliParser = new GnuParser().parse(opts, args);

    if (args.length == 0) {
      throw new IllegalArgumentException("No args specified for client to initialize");
    }

    if (cliParser.hasOption("help")) {
      printUsage();
      return false;
    }

    if (cliParser.hasOption("debug")) {
      debugFlag = true;

    }

    appName = cliParser.getOptionValue("appname", "DistributedShell");
    amPriority = Integer.parseInt(cliParser.getOptionValue("priority", "0"));
    amQueue = cliParser.getOptionValue("queue", "default");
    amMemory = Integer.parseInt(cliParser.getOptionValue("master_memory", "10"));		
    amVCores = Integer.parseInt(cliParser.getOptionValue("master_vcores", "1"));

    if (amMemory < 0) {
      throw new IllegalArgumentException("Invalid memory specified for application master, exiting."
          + " Specified memory=" + amMemory);
    }
    if (amVCores < 0) {
      throw new IllegalArgumentException("Invalid virtual cores specified for application master, exiting."
          + " Specified virtual cores=" + amVCores);
    }

    if (!cliParser.hasOption("jar")) {
      throw new IllegalArgumentException("No jar file specified for application master");
    }

    appMasterJar = cliParser.getOptionValue("jar");

    if (!cliParser.hasOption("files")) {
      throw new IllegalArgumentException("No files specified for application master");
    }
    files = cliParser.getOptionValue("files");

    return true;
  }

  /**
   * Main run function for the client
   * @return true if application completed successfully
   * @throws IOException
   * @throws YarnException
   */
  public boolean run() throws IOException, YarnException {

    LOG.info("Running Client");
    yarnClient.start();

    printMetricsInfo();

    // Get a new application id
    YarnClientApplication app = yarnClient.createApplication();

    setResource(app);

    // set the application name
    ApplicationSubmissionContext appContext = app.getApplicationSubmissionContext();
    ApplicationId appId = appContext.getApplicationId();

    appContext.setApplicationName(appName);

    // Set up the container launch context for the application master
    ContainerLaunchContext amContainer = ContainerLaunchContext.newInstance(
            getLocalResource(appId), getAppMasterEnv(), buildCommands(), null, getToken(), null);

    appContext.setAMContainerSpec(amContainer);

    // Set the priority for the application master
    // TODO - what is the range for priority? how to decide?
    appContext.setPriority(Priority.newInstance(amPriority));

    // Set the queue to which this application is to be submitted in the RM
    appContext.setQueue(amQueue);

    // Submit the application to the applications manager
    // SubmitApplicationResponse submitResp = applicationsManager.submitApplication(appRequest);
    // Ignore the response as either a valid response object is returned on success 
    // or an exception thrown to denote some form of a failure
    LOG.info("Submitting application to ASM");

    yarnClient.submitApplication(appContext);

    // Monitor the application
    return monitorApplication(appId);

  }

  /**
   * Monitor the submitted application for completion. 
   * Kill application if time expires. 
   * @param appId Application Id of application to be monitored
   * @return true if application completed successfully
   * @throws YarnException
   * @throws IOException
   */
  private boolean monitorApplication(ApplicationId appId)
      throws YarnException, IOException {

    while (true) {

      // Check app status every 1 second.
      try {
        Thread.sleep(10000);
      } catch (InterruptedException e) {
        LOG.debug("Thread sleep in monitoring loop interrupted");
      }

      // Get application report for the appId we are interested in 
      ApplicationReport report = yarnClient.getApplicationReport(appId);

      LOG.info("Got application report from ASM for"
          + ", appId=" + appId.getId()
          + ", clientToAMToken=" + report.getClientToAMToken()
          + ", appDiagnostics=" + report.getDiagnostics()
          + ", appMasterHost=" + report.getHost()
          + ", appQueue=" + report.getQueue()
          + ", appMasterRpcPort=" + report.getRpcPort()
          + ", appStartTime=" + report.getStartTime()
          + ", yarnAppState=" + report.getYarnApplicationState().toString()
          + ", distributedFinalState=" + report.getFinalApplicationStatus().toString()
          + ", appTrackingUrl=" + report.getTrackingUrl()
          + ", appUser=" + report.getUser());

      YarnApplicationState state = report.getYarnApplicationState();
      FinalApplicationStatus dsStatus = report.getFinalApplicationStatus();
      if (YarnApplicationState.FINISHED == state) {
        if (FinalApplicationStatus.SUCCEEDED == dsStatus) {
          LOG.info("Application has completed successfully. Breaking monitoring loop");
          return true;        
        }
        else {
          LOG.info("Application did finished unsuccessfully."
              + " YarnState=" + state.toString() + ", DSFinalStatus=" + dsStatus.toString()
              + ". Breaking monitoring loop");
          return false;
        }			  
      }
      else if (YarnApplicationState.KILLED == state
          || YarnApplicationState.FAILED == state) {
        LOG.info("Application did not finish."
            + " YarnState=" + state.toString() + ", DSFinalStatus=" + dsStatus.toString()
            + ". Breaking monitoring loop");
        return false;
      }			
    }

  }

  /**
   * Kill a submitted application by sending a call to the ASM
   * @param appId Application Id to be killed. 
   * @throws YarnException
   * @throws IOException
   */
  private void forceKillApplication(ApplicationId appId)
      throws YarnException, IOException {
    // TODO clarify whether multiple jobs with the same app id can be submitted and be running at 
    // the same time. 
    // If yes, can we kill a particular attempt only?

    // Response can be ignored as it is non-null on success or 
    // throws an exception in case of failures
    yarnClient.killApplication(appId);	
  }

  private void addToLocalResources(FileSystem fs, String fileSrcPath,
                                   String fileDstPath, String appId, Map<String, LocalResource> localResources,
                                   String resources) throws IOException {
    if (StringUtils.isEmpty(fileSrcPath) || StringUtils.isEmpty(fileDstPath)) {
      return;
    }
    Preconditions.checkArgument(StringUtil.isNotEmpty(fileSrcPath) && StringUtil.isNotEmpty(fileDstPath),
            "");
    String resourceName = fileDstPath.substring(fileDstPath.lastIndexOf("/") + 1);
    Preconditions.checkArgument(StringUtil.isNotEmpty(resourceName), "");
    Preconditions.checkArgument(!localResources.containsKey(resourceName),
            "resource:" + resourceName + " already exists");
    String suffix =
        appName + "/" + appId + "/" + resourceName;
    Path dst =
        new Path(fs.getHomeDirectory(), suffix);
    if (fileSrcPath == null) {
      FSDataOutputStream ostream = null;
      try {
        ostream = FileSystem
            .create(fs, dst, new FsPermission((short) 0710));
        ostream.writeUTF(resources);
      } finally {
        IOUtils.closeQuietly(ostream);
      }
    } else {
      fs.copyFromLocalFile(new Path(fileSrcPath), dst);
    }
    FileStatus scFileStatus = fs.getFileStatus(dst);
    LocalResource scRsrc =
        LocalResource.newInstance(
            ConverterUtils.getYarnUrlFromURI(dst.toUri()),
            LocalResourceType.FILE, LocalResourceVisibility.APPLICATION,
            scFileStatus.getLen(), scFileStatus.getModificationTime());
    localResources.put(resourceName, scRsrc);
  }

  private void printMetricsInfo() throws YarnException, IOException {
    YarnClusterMetrics clusterMetrics = yarnClient.getYarnClusterMetrics();
    LOG.info("Got Cluster metric info from ASM"
            + ", numNodeManagers=" + clusterMetrics.getNumNodeManagers());

    List<NodeReport> clusterNodeReports = yarnClient.getNodeReports(
            NodeState.RUNNING);
    LOG.info("Got Cluster node info from ASM");
    for (NodeReport node : clusterNodeReports) {
      LOG.info("Got node report from ASM for"
              + ", nodeId=" + node.getNodeId()
              + ", nodeAddress" + node.getHttpAddress()
              + ", nodeRackName" + node.getRackName()
              + ", nodeNumContainers" + node.getNumContainers());
    }

    QueueInfo queueInfo = yarnClient.getQueueInfo(this.amQueue);
    LOG.info("Queue info"
            + ", queueName=" + queueInfo.getQueueName()
            + ", queueCurrentCapacity=" + queueInfo.getCurrentCapacity()
            + ", queueMaxCapacity=" + queueInfo.getMaximumCapacity()
            + ", queueApplicationCount=" + queueInfo.getApplications().size()
            + ", queueChildQueueCount=" + queueInfo.getChildQueues().size());

    List<QueueUserACLInfo> listAclInfo = yarnClient.getQueueAclsInfo();
    for (QueueUserACLInfo aclInfo : listAclInfo) {
      for (QueueACL userAcl : aclInfo.getUserAcls()) {
        LOG.info("User ACL Info for Queue"
                + ", queueName=" + aclInfo.getQueueName()
                + ", userAcl=" + userAcl.name());
      }
    }
  }

  private void setResource(YarnClientApplication app) {
    GetNewApplicationResponse appResponse = app.getNewApplicationResponse();

    int maxMem = appResponse.getMaximumResourceCapability().getMemory();
    LOG.info("Max mem capabililty of resources in this cluster " + maxMem);

    // A resource ask cannot exceed the max.
    if (amMemory > maxMem) {
      LOG.info("AM memory specified above max threshold of cluster. Using max value."
              + ", specified=" + amMemory
              + ", max=" + maxMem);
      amMemory = maxMem;
    }

    int maxVCores = appResponse.getMaximumResourceCapability().getVirtualCores();
    LOG.info("Max virtual cores capabililty of resources in this cluster " + maxVCores);

    if (amVCores > maxVCores) {
      LOG.info("AM virtual cores specified above max threshold of cluster. "
              + "Using max value." + ", specified=" + amVCores
              + ", max=" + maxVCores);
      amVCores = maxVCores;
    }

    ApplicationSubmissionContext appContext = app.getApplicationSubmissionContext();
    Resource capability = Resource.newInstance(amMemory, amVCores);
    appContext.setResource(capability);
  }

  private Map<String, String> getAppMasterEnv() {
    // Set the env variables to be setup in the env where the application master will be run
    LOG.info("Set the environment for the application master");
    Map<String, String> env = new HashMap<String, String>();

    // Add AppMaster.jar location to classpath
    // At some point we should not be required to add
    // the hadoop specific classpaths to the env.
    // It should be provided out of the box.
    // For now setting all required classpaths including
    // the classpath to "." for the application jar
    StringBuilder classPathEnv = new StringBuilder(Environment.CLASSPATH.$$())
            .append(ApplicationConstants.CLASS_PATH_SEPARATOR).append("./*");
    for (String c : conf.getStrings(
            YarnConfiguration.YARN_APPLICATION_CLASSPATH,
            YarnConfiguration.DEFAULT_YARN_CROSS_PLATFORM_APPLICATION_CLASSPATH)) {
      classPathEnv.append(ApplicationConstants.CLASS_PATH_SEPARATOR);
      classPathEnv.append(c.trim());
    }

    // add the runtime classpath needed for tests to work
    if (conf.getBoolean(YarnConfiguration.IS_MINI_YARN_CLUSTER, false)) {
      classPathEnv.append(':');
      classPathEnv.append(System.getProperty("java.class.path"));
    }

    env.put("CLASSPATH", classPathEnv.toString());
    LOG.info("Set CLASSPATH=" + classPathEnv.toString());
    return env;
  }

  private List<String> buildCommands() {
    // Set the necessary command to execute the application master
    Vector<CharSequence> vargs = new Vector<CharSequence>(30);

    // Set java executable command
    LOG.info("Setting up app master command");
    vargs.add(Environment.JAVA_HOME.$$() + "/bin/java");
    // Set Xmx based on am memory size
    vargs.add("-Xmx" + amMemory + "m");
    // Set class name
    vargs.add(appMasterMainClass);
    vargs.add("1>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/AppMaster.stdout");
    vargs.add("2>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/AppMaster.stderr");

    // Get final commmand
    StringBuilder command = new StringBuilder();
    for (CharSequence str : vargs) {
      command.append(str).append(" ");
    }

    LOG.info("Completed setting up app master command " + command.toString());
    List<String> commands = new ArrayList<String>();
    commands.add(command.toString());
    return commands;
  }

  private ByteBuffer getToken() throws IOException{
    // Setup security tokens
    if (UserGroupInformation.isSecurityEnabled()) {
      // Note: Credentials class is marked as LimitedPrivate for HDFS and MapReduce
      Credentials credentials = new Credentials();
      String tokenRenewer = conf.get(YarnConfiguration.RM_PRINCIPAL);
      if (tokenRenewer == null || tokenRenewer.length() == 0) {
        throw new IOException(
                "Can't get Master Kerberos principal for the RM to use as renewer");
      }

      FileSystem fs = FileSystem.get(conf);

      // For now, only getting tokens for the default file-system.
      final Token<?> tokens[] =
              fs.addDelegationTokens(tokenRenewer, credentials);
      if (tokens != null) {
        for (Token<?> token : tokens) {
          LOG.info("Got dt for " + fs.getUri() + "; " + token);
        }
      }
      DataOutputBuffer dob = new DataOutputBuffer();
      credentials.writeTokenStorageToStream(dob);
      ByteBuffer fsTokens = ByteBuffer.wrap(dob.getData(), 0, dob.getLength());
      return fsTokens;
    }else{
      return null;
    }
  }

  private Map<String, LocalResource> getLocalResource(ApplicationId appId) throws IOException{
    // set local resources for the application master
    // local files or archives as needed
    // In this scenario, the jar file for the application master is part of the local resources
    Map<String, LocalResource> localResources = new HashMap<String, LocalResource>();

    LOG.info("Copy App Master jar from local filesystem and add to local environment");
    // Copy the application master jar to the filesystem
    // Create a local resource to point to the destination jar path
    FileSystem fs = FileSystem.get(conf);
    addToLocalResources(fs, appMasterJar, appMasterJarPath, appId.toString(),
            localResources, null);

    String[] localFiles = files.split(",");
    for (String file : localFiles) {
      addToLocalResources(fs, file, file, appId.toString(), localResources, null);
    }
    return localResources;
  }
}
