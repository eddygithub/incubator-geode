/*=========================================================================
 * Copyright (c) 2010-2014 Pivotal Software, Inc. All Rights Reserved.
 * This product is protected by U.S. and international copyright
 * and intellectual property laws. Pivotal products are covered by
 * one or more patents listed at http://www.pivotal.io/patents.
 *=========================================================================
 */
package com.gemstone.gemfire.internal.cache.tier.sockets;

import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import com.gemstone.gemfire.cache.AttributesFactory;
import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.CacheFactory;
import com.gemstone.gemfire.cache.DataPolicy;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionAttributes;
import com.gemstone.gemfire.cache.Scope;
import com.gemstone.gemfire.cache.client.PoolManager;
import com.gemstone.gemfire.cache.client.internal.PoolImpl;
import com.gemstone.gemfire.cache.client.internal.QueueStateImpl.SequenceIdAndExpirationObject;
import com.gemstone.gemfire.cache.util.BridgeServer;
import com.gemstone.gemfire.distributed.DistributedSystem;
import com.gemstone.gemfire.distributed.internal.DistributionConfig;
import com.gemstone.gemfire.internal.AvailablePort;
import com.gemstone.gemfire.internal.cache.BridgeObserver;
import com.gemstone.gemfire.internal.cache.BridgeObserverAdapter;
import com.gemstone.gemfire.internal.cache.BridgeObserverHolder;
import com.gemstone.gemfire.internal.cache.ha.HAHelper;
import com.gemstone.gemfire.internal.cache.ha.HARegionQueue;
import com.gemstone.gemfire.internal.cache.ha.ThreadIdentifier;

import dunit.DistributedTestCase;
import dunit.Host;
import dunit.VM;

/**
 * 
 * @author Suyog Bhokare
 * 
 * Tests the reliable messaging functionality - Client sends a periodic
 * ack to the primary server for the messages received.
 * 
 */
public class ReliableMessagingDUnitTest extends DistributedTestCase
{

  static VM server1 = null;

  static VM server2 = null;

  /** the cache */
  private static Cache cache = null;

  /** port for the cache server */
  private static int PORT1;

  private static int PORT2;

  static PoolImpl pool = null;

  static ThreadIdentifier tid = null;

  static Long seqid = null;

  static long creationTime = 0;
  
  static int CLIENT_ACK_INTERVAL = 5000;

  /** name of the test region */
  private static final String REGION_NAME = "ReliableMessagingDUnitTest_Region";

  /**
   * Constructor
   */
  public ReliableMessagingDUnitTest(String name) {
    super(name);
  }
  
  /*
   * Test verifies that client is sending periodic ack to the primary 
   * server for messages received.
   */  
  public void testPeriodicAckSendByClient() throws Exception
  {
    createEntries();
    server1.invoke(ReliableMessagingDUnitTest.class, "putOnServer");   
    waitForServerUpdate();    
    setCreationTimeTidAndSeq();
    waitForClientAck();        
    server1.invoke(ReliableMessagingDUnitTest.class, "checkTidAndSeq");   
  }

  /*
   * If the primary fails before receiving an ack from the messages it delivered
   * then it should send an ack to the new primary so that new primary can sends
   * QRM to other redundant servers.    
   */
  public void testPeriodicAckSendByClientPrimaryFailover() throws Exception {    
    addExpectedException("java.net.ConnectException");
    createEntries();
    setBridgeObserverForBeforeSendingClientAck();    
    server1.invoke(ReliableMessagingDUnitTest.class, "putOnServer");
    getLogWriter().info("Entering waitForServerUpdate");
    waitForServerUpdate();    
    getLogWriter().info("Entering waitForCallback");
    waitForCallback();
    getLogWriter().info("Entering waitForClientAck");
    waitForClientAck();
    server2.invoke(ReliableMessagingDUnitTest.class, "checkTidAndSeq");
  }
  
  /**
   * Wait for acknowledgment from client, verify creation time is correct
   * 
   * @throws Exception
   */
  public static void waitForClientAck() throws Exception
  {
    final long maxWaitTime = 30000;
    final long start = System.currentTimeMillis();
    Iterator iter = pool.getThreadIdToSequenceIdMap().entrySet().iterator();
    SequenceIdAndExpirationObject seo = null;
    if (!iter.hasNext()) {
      fail("map is empty");
    }
    Map.Entry entry = (Map.Entry)iter.next();
    seo = (SequenceIdAndExpirationObject)entry.getValue();
      
    for (;;) {
      if (seo.getAckSend()) {
        break;
      }
      assertTrue("Waited over " + maxWaitTime + " for client ack ",+ 
          (System.currentTimeMillis() - start) < maxWaitTime);
      sleep(1000);
    }
    getLogWriter().info("seo = " + seo);
    assertTrue("Creation time " + creationTime + " supposed to be same as seo " 
        + seo.getCreationTime(), creationTime == seo.getCreationTime());
  }
  
  public static void setCreationTimeTidAndSeq() 
  {
    final Map map = pool.getThreadIdToSequenceIdMap();
    WaitCriterion ev = new WaitCriterion() {
      public boolean done() {
        synchronized (map) {
          return map.entrySet().size() > 0;
        }
      }
      public String description() {
        return null;
      }
    };
    DistributedTestCase.waitForCriterion(ev, 10 * 1000, 200, true);
    Map.Entry entry;
    synchronized (map) {
      Iterator iter = map.entrySet().iterator();
      entry = (Map.Entry)iter.next();
    }
    
    SequenceIdAndExpirationObject seo = (SequenceIdAndExpirationObject)entry
          .getValue();
      assertFalse(seo.getAckSend());
      creationTime = seo.getCreationTime();
      getLogWriter().info("seo is " + seo.toString());
      assertTrue("Creation time not set", creationTime != 0);
      
      Object args[] =
        new Object[] {
          ((ThreadIdentifier)entry.getKey()).getMembershipID(),
          new Long(((ThreadIdentifier)entry.getKey()).getThreadID()),
          new Long(seo.getSequenceId()) };
      server1.invoke(ReliableMessagingDUnitTest.class, "setTidAndSeq", args);
      server2.invoke(ReliableMessagingDUnitTest.class, "setTidAndSeq", args);
  }
  
  public static void checkEmptyDispatchedMsgs()
  {
    assertEquals(0, HARegionQueue.getDispatchedMessagesMapForTesting().size());
  }

  public static void checkTidAndSeq()
  {
    Map map = HARegionQueue.getDispatchedMessagesMapForTesting();
    assertTrue(map.size() > 0);
    Iterator iter = map.entrySet().iterator();
    if (!iter.hasNext()) {
      fail("Dispatched messages is empty");
    }
    Map.Entry entry = (Map.Entry)iter.next();
    Map dispMap = HAHelper.getDispatchMessageMap(entry.getValue());
    assertEquals(seqid, dispMap.get(tid));
  }

  public static void setTidAndSeq(byte[] membershipId, Long threadId,
      Long sequenceId)
  {
    tid = new ThreadIdentifier(membershipId, threadId.longValue());
    seqid = sequenceId;
  }
  
  public static void createEntries() throws Exception
  {
    creationTime = 0;
    Region r1 = cache.getRegion(Region.SEPARATOR + REGION_NAME);
    String keyPrefix = "server-";
    for (int i = 0; i < 5; i++) {
      r1.create(keyPrefix + i,"val");
    }  
  }
  
  public static void putOnServer() throws Exception
  {
    Region r1 = cache.getRegion(Region.SEPARATOR + REGION_NAME);
    String keyPrefix = "server-";
    for (int i = 0; i < 5; i++) {
      r1.put(keyPrefix + i,  "val-" + i);
    }
  }
  
  static private void sleep(int ms) {
    try {
      Thread.sleep(ms);
    }
    catch (InterruptedException e) {
      fail("Interrupted", e);
    }
  }
  
  public static void checkServerCount(int expectedDeadServers, int expectedLiveServers)
  {
    final long maxWaitTime = 60000;
    long start = System.currentTimeMillis();
    for (;;) {
      if (pool.getConnectedServerCount() == expectedLiveServers) {
        break;  // met
      }
      assertTrue("Waited over " + maxWaitTime
          + "for active servers to become :" + expectedLiveServers,
          (System.currentTimeMillis() - start) < maxWaitTime);
      sleep(2000);
    }
  }  
  
  public static void stopServer()
  {
    try {
      Iterator iter = cache.getBridgeServers().iterator();
      if (iter.hasNext()) {
        BridgeServer server = (BridgeServer)iter.next();
        server.stop();
      }
    }
    catch (Exception e) {
      fail("failed while stopServer()", e);
    }
  }
  
  /**
   * Wait for new value on bridge server to become visible in this cache
   */
  public static void waitForServerUpdate()
  {
    Region r1 = cache.getRegion(Region.SEPARATOR + REGION_NAME);
    assertNotNull(r1);
    final long maxWaitTime = 60000;
    final long start = System.currentTimeMillis();
    for (;;) {
      if (r1.getEntry("server-4").getValue().equals("val-4")) {
        break;
      }
      assertTrue("Waited over " + maxWaitTime + " ms for entry to be refreshed",
          (System.currentTimeMillis() - start) < maxWaitTime);
      sleep(1000);
    }
  }
  
  public static void setBridgeObserverForBeforeSendingClientAck() throws Exception
  {
    PoolImpl.BEFORE_SENDING_CLIENT_ACK_CALLBACK_FLAG = true;
    origObserver = BridgeObserverHolder.setInstance(new BridgeObserverAdapter() {
      public void beforeSendingClientAck()
      {
        getLogWriter().info("beforeSendingClientAck invoked");
        setCreationTimeTidAndSeq();   
        server1.invoke(ReliableMessagingDUnitTest.class, "stopServer");
        checkServerCount(1,1);
        server2.invoke(ReliableMessagingDUnitTest.class, "checkEmptyDispatchedMsgs");        
        PoolImpl.BEFORE_SENDING_CLIENT_ACK_CALLBACK_FLAG = false;       
        getLogWriter().info("end of beforeSendingClientAck");
            }
    });
  }

  /**
   * Wait for magic callback
   */
  public static void waitForCallback()
  {    
    final long maxWaitTime = 60000;
    final long start = System.currentTimeMillis();
    for (;;) {
      if (!PoolImpl.BEFORE_SENDING_CLIENT_ACK_CALLBACK_FLAG) {
        break;
      }
      assertTrue("Waited over " + maxWaitTime + "to send an ack from client : ",
          (System.currentTimeMillis() - start) < maxWaitTime);
      sleep(2000);
    }
  }
  
  public void setUp() throws Exception
  {
    super.setUp();
    final Host host = Host.getHost(0);
    server1 = host.getVM(0);
    server2 = host.getVM(1);

    PORT1 = ((Integer)server1.invoke(ReliableMessagingDUnitTest.class,
        "createServerCache")).intValue();
    PORT2 = ((Integer)server2.invoke(ReliableMessagingDUnitTest.class,
        "createServerCache")).intValue();

    CacheServerTestUtil.disableShufflingOfEndpoints();
    createClientCache(PORT1, PORT2);

  }

  private Cache createCache(Properties props) throws Exception
  {
    DistributedSystem ds = getSystem(props);
    ds.disconnect();
    ds = getSystem(props);
    Cache result = null;
    result = CacheFactory.create(ds);
    if (result == null) {
      throw new Exception("CacheFactory.create() returned null ");
    }
    return result;
  }

  public static Integer createServerCache() throws Exception
  {
    ReliableMessagingDUnitTest test = new ReliableMessagingDUnitTest("temp");
    Properties props = new Properties();
    cache = test.createCache(props);
    AttributesFactory factory = new AttributesFactory();
    factory.setScope(Scope.DISTRIBUTED_ACK);
    factory.setDataPolicy(DataPolicy.REPLICATE);

    RegionAttributes attrs = factory.create();

    cache.setMessageSyncInterval(25);
    cache.createRegion(REGION_NAME, attrs);

    BridgeServer server = cache.addBridgeServer();
    int port = AvailablePort.getRandomAvailablePort(AvailablePort.SOCKET);
    server.setPort(port);
    server.setNotifyBySubscription(true);
    server.start();
    getLogWriter().info("Server started at PORT = " + port);

    return new Integer(server.getPort());
  }

  public static void createClientCache(int port1, int port2) throws Exception
  {
    ReliableMessagingDUnitTest test = new ReliableMessagingDUnitTest("temp");
    Properties props = new Properties();
    props.setProperty(DistributionConfig.MCAST_PORT_NAME, "0");
    props.setProperty(DistributionConfig.LOCATORS_NAME, "");
    cache = test.createCache(props);
    String host = getServerHostName(Host.getHost(0));
    PoolImpl p = (PoolImpl)PoolManager.createFactory()
      .addServer(host, PORT1)
      .addServer(host, PORT2)
      .setSubscriptionEnabled(true)
      .setSubscriptionRedundancy(1)
      .setThreadLocalConnections(true)
      .setMinConnections(6)
      .setReadTimeout(20000)
       .setPingInterval(10000)
       .setRetryAttempts(5)
       .setSubscriptionAckInterval(CLIENT_ACK_INTERVAL)
      .create("ReliableMessagingDUnitTestPool");
    

    AttributesFactory factory = new AttributesFactory();
    factory.setScope(Scope.DISTRIBUTED_ACK);
    factory.setPoolName(p.getName());

    RegionAttributes attrs = factory.create();
    Region region = cache.createRegion(REGION_NAME, attrs);
    region.registerInterest("ALL_KEYS");

    pool = p;
  }

  public void tearDown2() throws Exception
  {
    creationTime = 0;
    super.tearDown2();   
    closeCache();
    server1.invoke(ReliableMessagingDUnitTest.class, "closeCache");
    server2.invoke(ReliableMessagingDUnitTest.class, "closeCache");
    CacheServerTestUtil.resetDisableShufflingOfEndpointsFlag();
  }

  public static void closeCache()
  {
    if (cache != null && !cache.isClosed()) {
      cache.close();
      cache.getDistributedSystem().disconnect();
    }
  }
  
  private static BridgeObserver origObserver;
  
  public static void resetCallBack()  {    
    BridgeObserverHolder.setInstance(origObserver);
  }

}
