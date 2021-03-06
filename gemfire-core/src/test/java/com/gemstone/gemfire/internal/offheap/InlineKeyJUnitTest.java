package com.gemstone.gemfire.internal.offheap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Properties;
import java.util.UUID;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.gemstone.gemfire.cache.CacheFactory;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionShortcut;
import com.gemstone.gemfire.cache.util.ObjectSizer;
import com.gemstone.gemfire.internal.cache.GemFireCacheImpl;
import com.gemstone.gemfire.internal.cache.LocalRegion;
import com.gemstone.gemfire.internal.cache.VMThinRegionEntryOffHeapIntKey;
import com.gemstone.gemfire.internal.cache.VMThinRegionEntryOffHeapLongKey;
import com.gemstone.gemfire.internal.cache.VMThinRegionEntryOffHeapObjectKey;
import com.gemstone.gemfire.internal.cache.VMThinRegionEntryOffHeapStringKey1;
import com.gemstone.gemfire.internal.cache.VMThinRegionEntryOffHeapStringKey2;
import com.gemstone.gemfire.internal.cache.VMThinRegionEntryOffHeapUUIDKey;
import com.gemstone.gemfire.test.junit.categories.IntegrationTest;

@Category(IntegrationTest.class)
public class InlineKeyJUnitTest {
  private GemFireCacheImpl createCache() {
    Properties props = new Properties();
    props.setProperty("locators", "");
    props.setProperty("mcast-port", "0");
    props.setProperty("off-heap-memory-size", "1m");
    GemFireCacheImpl result = (GemFireCacheImpl) new CacheFactory(props).create();
    return result;
  }
  private void closeCache(GemFireCacheImpl gfc) {
    gfc.close();
  }
  
  @Test
  public void testInlineKeys() {
    GemFireCacheImpl gfc = createCache();
    try {
      Region r = gfc.createRegionFactory(RegionShortcut.LOCAL).setConcurrencyChecksEnabled(false).setOffHeap(true).create("inlineKeyRegion");
      LocalRegion lr = (LocalRegion) r;
      Object key = Integer.valueOf(1);
      r.create(key, null);
      assertEquals(true, r.containsKey(key));
      assertTrue("expected int entry but was " + lr.getRegionEntry(key).getClass(), lr.getRegionEntry(key) instanceof VMThinRegionEntryOffHeapIntKey);
      key = Long.valueOf(2);
      r.create(key, null);
      assertEquals(true, r.containsKey(key));
      assertTrue("expected long entry but was " + lr.getRegionEntry(key).getClass(), lr.getRegionEntry(key) instanceof VMThinRegionEntryOffHeapLongKey);
      key = new UUID(1L, 2L);
      r.create(key, null);
      assertEquals(true, r.containsKey(key));
      assertTrue("expected uuid entry but was " + lr.getRegionEntry(key).getClass(), lr.getRegionEntry(key) instanceof VMThinRegionEntryOffHeapUUIDKey);
      key = "";
      r.create(key, null);
      assertEquals(true, r.containsKey(key));
      assertTrue("expected string entry but was " + lr.getRegionEntry(key).getClass(), lr.getRegionEntry(key) instanceof VMThinRegionEntryOffHeapStringKey1);
      key = "1";
      r.create(key, null);
      assertEquals(true, r.containsKey(key));
      assertTrue("expected string entry but was " + lr.getRegionEntry(key).getClass(), lr.getRegionEntry(key) instanceof VMThinRegionEntryOffHeapStringKey1);
      key = "12";
      r.create(key, null);
      assertEquals(true, r.containsKey(key));
      assertTrue("expected string entry but was " + lr.getRegionEntry(key).getClass(), lr.getRegionEntry(key) instanceof VMThinRegionEntryOffHeapStringKey1);
      key = "123";
      r.create(key, null);
      assertEquals(true, r.containsKey(key));
      assertTrue("expected string entry but was " + lr.getRegionEntry(key).getClass(), lr.getRegionEntry(key) instanceof VMThinRegionEntryOffHeapStringKey1);
      key = "1234";
      r.create(key, null);
      assertEquals(true, r.containsKey(key));
      assertTrue("expected string entry but was " + lr.getRegionEntry(key).getClass(), lr.getRegionEntry(key) instanceof VMThinRegionEntryOffHeapStringKey1);
      key = "12345";
      r.create(key, null);
      assertEquals(true, r.containsKey(key));
      assertTrue("expected string entry but was " + lr.getRegionEntry(key).getClass(), lr.getRegionEntry(key) instanceof VMThinRegionEntryOffHeapStringKey1);
      key = "123456";
      r.create(key, null);
      assertEquals(true, r.containsKey(key));
      assertTrue("expected string entry but was " + lr.getRegionEntry(key).getClass(), lr.getRegionEntry(key) instanceof VMThinRegionEntryOffHeapStringKey1);
      key = "1234567";
      r.create(key, null);
      assertEquals(true, r.containsKey(key));
      assertTrue("expected string entry but was " + lr.getRegionEntry(key).getClass(), lr.getRegionEntry(key) instanceof VMThinRegionEntryOffHeapStringKey1);
      key = "12345678";
      r.create(key, null);
      assertEquals(true, r.containsKey(key));
      assertTrue("expected string entry but was " + lr.getRegionEntry(key).getClass(), lr.getRegionEntry(key) instanceof VMThinRegionEntryOffHeapStringKey2);
      key = "123456789";
      r.create(key, null);
      assertEquals(true, r.containsKey(key));
      assertTrue("expected string entry but was " + lr.getRegionEntry(key).getClass(), lr.getRegionEntry(key) instanceof VMThinRegionEntryOffHeapStringKey2);
      key = "1234567890";
      r.create(key, null);
      assertEquals(true, r.containsKey(key));
      assertTrue("expected string entry but was " + lr.getRegionEntry(key).getClass(), lr.getRegionEntry(key) instanceof VMThinRegionEntryOffHeapStringKey2);
      key = "12345678901";
      r.create(key, null);
      assertEquals(true, r.containsKey(key));
      assertTrue("expected string entry but was " + lr.getRegionEntry(key).getClass(), lr.getRegionEntry(key) instanceof VMThinRegionEntryOffHeapStringKey2);
      key = "123456789012";
      r.create(key, null);
      assertEquals(true, r.containsKey(key));
      assertTrue("expected string entry but was " + lr.getRegionEntry(key).getClass(), lr.getRegionEntry(key) instanceof VMThinRegionEntryOffHeapStringKey2);
      key = "1234567890123";
      r.create(key, null);
      assertEquals(true, r.containsKey(key));
      assertTrue("expected string entry but was " + lr.getRegionEntry(key).getClass(), lr.getRegionEntry(key) instanceof VMThinRegionEntryOffHeapStringKey2);
      key = "12345678901234";
      r.create(key, null);
      assertEquals(true, r.containsKey(key));
      assertTrue("expected string entry but was " + lr.getRegionEntry(key).getClass(), lr.getRegionEntry(key) instanceof VMThinRegionEntryOffHeapStringKey2);
      key = "123456789012345";
      r.create(key, null);
      assertEquals(true, r.containsKey(key));
      assertTrue("expected string entry but was " + lr.getRegionEntry(key).getClass(), lr.getRegionEntry(key) instanceof VMThinRegionEntryOffHeapStringKey2);
      
      key = "1234567890123456";
      r.create(key, null);
      assertEquals(true, r.containsKey(key));
      assertTrue("expected string object but was " + lr.getRegionEntry(key).getClass(), lr.getRegionEntry(key) instanceof VMThinRegionEntryOffHeapObjectKey);
     
    } finally {
      closeCache(gfc);
    }
  }
  
  private static int getMemSize(Object o) {
    return ObjectSizer.REFLECTION_SIZE.sizeof(o);
  }
  
  @Test
  public void testMemoryOverhead() {
    Object re = new VMThinRegionEntryOffHeapIntKey(null, 1, null);
    //System.out.println("VMThinRegionEntryIntKey=" + getMemSize(re));
    Object re2 = new VMThinRegionEntryOffHeapObjectKey(null, 1, null);
    //System.out.println("VMThinRegionEntryObjectKey=" + getMemSize(re2));
    assertTrue(getMemSize(re) < getMemSize(re2));
    
    re = new VMThinRegionEntryOffHeapLongKey(null, 1L, null);
    //System.out.println("VMThinRegionEntryLongKey=" + getMemSize(re));
    re2 = new VMThinRegionEntryOffHeapObjectKey(null, 1L, null);
    //System.out.println("VMThinRegionEntryObjectKey=" + getMemSize(re2));
    assertTrue(getMemSize(re) < getMemSize(re2));
    
    re = new VMThinRegionEntryOffHeapUUIDKey(null, new UUID(1L, 2L), null);
    //System.out.println("VMThinRegionEntryUUIDKey=" + getMemSize(re));
    re2 = new VMThinRegionEntryOffHeapObjectKey(null, new UUID(1L, 2L), null);
    //System.out.println("VMThinRegionEntryObjectKey=" + getMemSize(re2));
    assertTrue(getMemSize(re) < getMemSize(re2));
    
    re = new VMThinRegionEntryOffHeapStringKey1(null, "1234567", null, true);
    //System.out.println("VMThinRegionEntryStringKey1=" + getMemSize(re));
    re2 = new VMThinRegionEntryOffHeapObjectKey(null, "1234567", null);
    //System.out.println("VMThinRegionEntryObjectKey=" + getMemSize(re2));
    assertTrue(getMemSize(re) < getMemSize(re2));

    re = new VMThinRegionEntryOffHeapStringKey2(null, "123456789012345", null, true);
    //System.out.println("VMThinRegionEntryStringKey2=" + getMemSize(re));
    re2 = new VMThinRegionEntryOffHeapObjectKey(null, "123456789012345", null);
    //System.out.println("VMThinRegionEntryObjectKey=" + getMemSize(re2));
    assertTrue(getMemSize(re) < getMemSize(re2));
  }
}
