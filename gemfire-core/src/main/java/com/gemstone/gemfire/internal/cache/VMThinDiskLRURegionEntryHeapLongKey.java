/*=========================================================================
 * Copyright (c) 2010-2014 Pivotal Software, Inc. All Rights Reserved.
 * This product is protected by U.S. and international copyright
 * and intellectual property laws. Pivotal products are covered by
 * one or more patents listed at http://www.pivotal.io/patents.
 *=========================================================================
 */
package com.gemstone.gemfire.internal.cache;
// DO NOT modify this class. It was generated from LeafRegionEntry.cpp
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import com.gemstone.gemfire.internal.cache.lru.EnableLRU;
import com.gemstone.gemfire.internal.cache.persistence.DiskRecoveryStore;
import com.gemstone.gemfire.internal.cache.lru.LRUClockNode;
import com.gemstone.gemfire.internal.cache.lru.NewLRUClockHand;
import com.gemstone.gemfire.internal.util.concurrent.CustomEntryConcurrentHashMap.HashEntry;
// macros whose definition changes this class:
// disk: DISK
// lru: LRU
// stats: STATS
// versioned: VERSIONED
// offheap: OFFHEAP
// One of the following key macros must be defined:
// key object: KEY_OBJECT
// key int: KEY_INT
// key long: KEY_LONG
// key uuid: KEY_UUID
// key string1: KEY_STRING1
// key string2: KEY_STRING2
/**
 * Do not modify this class. It was generated.
 * Instead modify LeafRegionEntry.cpp and then run
 * bin/generateRegionEntryClasses.sh from the directory
 * that contains your build.xml.
 */
public class VMThinDiskLRURegionEntryHeapLongKey extends VMThinDiskLRURegionEntryHeap {
  public VMThinDiskLRURegionEntryHeapLongKey (RegionEntryContext context, long key,
      Object value
      ) {
    super(context,
          (value instanceof RecoveredEntry ? null : value)
        );
    // DO NOT modify this class. It was generated from LeafRegionEntry.cpp
    initialize(context, value);
    this.key = key;
  }
  // DO NOT modify this class. It was generated from LeafRegionEntry.cpp
  // common code
  protected int hash;
  private HashEntry<Object, Object> next;
  @SuppressWarnings("unused")
  private volatile long lastModified;
  private static final AtomicLongFieldUpdater<VMThinDiskLRURegionEntryHeapLongKey> lastModifiedUpdater
    = AtomicLongFieldUpdater.newUpdater(VMThinDiskLRURegionEntryHeapLongKey.class, "lastModified");
  private volatile Object value;
  @Override
  protected final Object getValueField() {
    return this.value;
  }
  @Override
  protected void setValueField(Object v) {
    this.value = v;
  }
  protected long getlastModifiedField() {
    return lastModifiedUpdater.get(this);
  }
  protected boolean compareAndSetLastModifiedField(long expectedValue, long newValue) {
    return lastModifiedUpdater.compareAndSet(this, expectedValue, newValue);
  }
  /**
   * @see HashEntry#getEntryHash()
   */
  public final int getEntryHash() {
    return this.hash;
  }
  protected void setEntryHash(int v) {
    this.hash = v;
  }
  /**
   * @see HashEntry#getNextEntry()
   */
  public final HashEntry<Object, Object> getNextEntry() {
    return this.next;
  }
  /**
   * @see HashEntry#setNextEntry
   */
  public final void setNextEntry(final HashEntry<Object, Object> n) {
    this.next = n;
  }
  // DO NOT modify this class. It was generated from LeafRegionEntry.cpp
  // disk code
  protected void initialize(RegionEntryContext drs, Object value) {
    boolean isBackup;
    if (drs instanceof LocalRegion) {
      isBackup = ((LocalRegion)drs).getDiskRegion().isBackup();
    } else if (drs instanceof PlaceHolderDiskRegion) {
      isBackup = true;
    } else {
      throw new IllegalArgumentException("expected a LocalRegion or PlaceHolderDiskRegion");
    }
    // Delay the initialization of DiskID if overflow only
    if (isBackup) {
      diskInitialize(drs, value);
    }
  }
  @Override
  public final synchronized int updateAsyncEntrySize(EnableLRU capacityController) {
    int oldSize = getEntrySize();
    int newSize = capacityController.entrySize( getKeyForSizing(), null);
    setEntrySize(newSize);
    int delta = newSize - oldSize;
    return delta;
  }
  // DO NOT modify this class. It was generated from LeafRegionEntry.cpp
  private void diskInitialize(RegionEntryContext context, Object value) {
    DiskRecoveryStore drs = (DiskRecoveryStore)context;
    DiskStoreImpl ds = drs.getDiskStore();
    long maxOplogSize = ds.getMaxOplogSize();
    //get appropriate instance of DiskId implementation based on maxOplogSize
    this.id = DiskId.createDiskId(maxOplogSize, true/* is persistence */, ds.needsLinkedList());
    Helper.initialize(this, drs, value);
  }
  /**
   * DiskId
   * 
   * @since 5.1
   */
  protected DiskId id;//= new DiskId();
  public DiskId getDiskId() {
    return this.id;
  }
  @Override
  void setDiskId(RegionEntry old) {
    this.id = ((AbstractDiskRegionEntry)old).getDiskId();
  }
//  // inlining DiskId
//  // always have these fields
//  /**
//   * id consists of
//   * most significant
//   * 1 byte = users bits
//   * 2-8 bytes = oplog id
//   * least significant.
//   * 
//   * The highest bit in the oplog id part is set to 1 if the oplog id
//   * is negative.
//   * @todo this field could be an int for an overflow only region
//   */
//  private long id;
//  /**
//   * Length of the bytes on disk.
//   * This is always set. If the value is invalid then it will be set to 0.
//   * The most significant bit is used by overflow to mark it as needing to be written.
//   */
//  protected int valueLength = 0;
//  // have intOffset or longOffset
//  // intOffset
//  /**
//   * The position in the oplog (the oplog offset) where this entry's value is
//   * stored
//   */
//  private volatile int offsetInOplog;
//  // longOffset
//  /**
//   * The position in the oplog (the oplog offset) where this entry's value is
//   * stored
//   */
//  private volatile long offsetInOplog;
//  // have overflowOnly or persistence
//  // overflowOnly
//  // no fields
//  // persistent
//  /** unique entry identifier * */
//  private long keyId;
  // DO NOT modify this class. It was generated from LeafRegionEntry.cpp
  // lru code
  @Override
  public void setDelayedDiskId(LocalRegion r) {
    DiskStoreImpl ds = r.getDiskStore();
    long maxOplogSize = ds.getMaxOplogSize();
    this.id = DiskId.createDiskId(maxOplogSize, false /* over flow only */, ds.needsLinkedList());
  }
  public final synchronized int updateEntrySize(EnableLRU capacityController) {
    return updateEntrySize(capacityController, _getValue()); // OFHEAP: _getValue ok w/o incing refcount because we are synced and only getting the size
  }
  // DO NOT modify this class. It was generated from LeafRegionEntry.cpp
  public final synchronized int updateEntrySize(EnableLRU capacityController,
                                                Object value) {
    int oldSize = getEntrySize();
    int newSize = capacityController.entrySize( getKeyForSizing(), value);
    setEntrySize(newSize);
    int delta = newSize - oldSize;
  //   if ( debug ) log( "updateEntrySize key=" + getKey()
  //                     + (_getValue() == Token.INVALID ? " invalid" :
  //                        (_getValue() == Token.LOCAL_INVALID ? "local_invalid" :
  //                         (_getValue()==null ? " evicted" : " valid")))
  //                     + " oldSize=" + oldSize
  //                     + " newSize=" + this.size );
    return delta;
  }
  public final boolean testRecentlyUsed() {
    return areAnyBitsSet(RECENTLY_USED);
  }
  @Override
  public final void setRecentlyUsed() {
    setBits(RECENTLY_USED);
  }
  public final void unsetRecentlyUsed() {
    clearBits(~RECENTLY_USED);
  }
  public final boolean testEvicted() {
    return areAnyBitsSet(EVICTED);
  }
  public final void setEvicted() {
    setBits(EVICTED);
  }
  public final void unsetEvicted() {
    clearBits(~EVICTED);
  }
  // DO NOT modify this class. It was generated from LeafRegionEntry.cpp
  private LRUClockNode nextLRU;
  private LRUClockNode prevLRU;
  private int size;
  public final void setNextLRUNode( LRUClockNode next ) {
    this.nextLRU = next;
  }
  public final LRUClockNode nextLRUNode() {
    return this.nextLRU;
  }
  public final void setPrevLRUNode( LRUClockNode prev ) {
    this.prevLRU = prev;
  }
  public final LRUClockNode prevLRUNode() {
    return this.prevLRU;
  }
  public final int getEntrySize() {
    return this.size;
  }
  protected final void setEntrySize(int size) {
    this.size = size;
  }
  // DO NOT modify this class. It was generated from LeafRegionEntry.cpp
//@Override
//public StringBuilder appendFieldsToString(final StringBuilder sb) {
//  StringBuilder result = super.appendFieldsToString(sb);
//  result.append("; prev=").append(this.prevLRU==null?"null":"not null");
//  result.append("; next=").append(this.nextLRU==null?"null":"not null");
//  return result;
//}
  @Override
  public Object getKeyForSizing() {
    // inline keys always report null for sizing since the size comes from the entry size
    return null;
  }
  // DO NOT modify this class. It was generated from LeafRegionEntry.cpp
  // key code
  private final long key;
  @Override
  public final Object getKey() {
    return this.key;
  }
  @Override
  public boolean isKeyEqual(Object k) {
    if (k instanceof Long) {
      return ((Long) k).longValue() == this.key;
    }
    return false;
  }
  // DO NOT modify this class. It was generated from LeafRegionEntry.cpp
}
