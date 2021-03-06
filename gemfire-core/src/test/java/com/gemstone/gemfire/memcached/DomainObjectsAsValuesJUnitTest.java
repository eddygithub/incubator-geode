/*=========================================================================
 * Copyright (c) 2010-2014 Pivotal Software, Inc. All Rights Reserved.
 * This product is protected by U.S. and international copyright
 * and intellectual property laws. Pivotal products are covered by
 * one or more patents listed at http://www.pivotal.io/patents.
 *=========================================================================
 */
package com.gemstone.gemfire.memcached;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.gemstone.gemfire.internal.AvailablePortHelper;
import com.gemstone.gemfire.internal.SocketCreator;
import com.gemstone.gemfire.test.junit.categories.IntegrationTest;

import net.spy.memcached.MemcachedClient;
import junit.framework.TestCase;

@Category(IntegrationTest.class)
public class DomainObjectsAsValuesJUnitTest {

  private static final Logger logger = Logger.getLogger(DomainObjectsAsValuesJUnitTest.class.getCanonicalName());
  
  private int PORT;
  
  private GemFireMemcachedServer server;
  
  @Before
  public void setUp() throws Exception {
    System.setProperty("gemfire.mcast-port", "0");
    PORT = AvailablePortHelper.getRandomAvailableTCPPort();
    this.server = new GemFireMemcachedServer(PORT);
    server.start();
  }
  
  @After
  public void tearDown() throws Exception {
    this.server.shutdown();
    System.getProperties().remove("gemfire.mcast-port");
  }

  public static class Customer implements java.io.Serializable {
    private static final long serialVersionUID = 4238572216598708877L;
    private String name;
    private String address;
    public Customer() {
    }
    public Customer(String name, String addr) {
      this.setName(name);
      this.setAddress(addr);
    }
    public void setName(String name) {
      this.name = name;
    }
    public String getName() {
      return name;
    }
    public void setAddress(String address) {
      this.address = address;
    }
    public String getAddress() {
      return address;
    }
    @Override
    public boolean equals(Object obj) {
      if (obj instanceof Customer) {
        Customer other = (Customer) obj;
        return compareStrings(this.name, other.name) && compareStrings(this.address, other.address);
      }
      return false;
    }
    private boolean compareStrings(String str1, String str2) {
      if (str1 == null && str2 == null) {
        return true;
      } else if (str1 == null || str2 == null) {
        return false;
      }
      return str1.equals(str2);
    }
    @Override
    public String toString() {
    	StringBuilder b = new StringBuilder();
    	b.append(getClass()).append("@").append(System.identityHashCode(this));
    	b.append("name:").append(name).append("address:").append(address);
    	return b.toString();
    }
  }
  
  @Test
  public void testGetPutDomainObject() throws Exception {
    MemcachedClient client = new MemcachedClient(
        new InetSocketAddress(InetAddress.getLocalHost(), PORT));
    Customer c = new Customer("name0", "addr0");
    Customer c1 = new Customer("name1", "addr1");
    Future<Boolean> f = client.add("keyObj", 10, c);
    assertTrue(f.get());
    Future<Boolean> f1 = client.add("key1", 10, c1);
    assertTrue(f1.get());
    assertEquals(c, client.get("keyObj"));
    assertEquals(c1, client.get("key1"));
    assertNull(client.get("nonExistentkey"));
  }
}
