/*=========================================================================
 * Copyright (c) 2010-2014 Pivotal Software, Inc. All Rights Reserved.
 * This product is protected by U.S. and international copyright
 * and intellectual property laws. Pivotal products are covered by
 * one or more patents listed at http://www.pivotal.io/patents.
 *=========================================================================
 */
package com.gemstone.gemfire.internal.cache.partitioned;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

import com.gemstone.gemfire.cache.partition.PartitionMemberInfo;

public class PartitionRegionInfoImpl 
implements InternalPRInfo, Serializable {

  private static final long serialVersionUID = 6462414089469761476L;
  private final String regionPath;
  private final int configuredBucketCount;
  private final int createdBucketCount;
  private final int lowRedundancyBucketCount;
  private final int configuredRedundantCopies;
  private final int actualRedundantCopies;
  private final Set<InternalPartitionDetails> memberDetails;
  private final String colocatedWith;
  private final OfflineMemberDetails offlineMembers;
  
  public PartitionRegionInfoImpl(String regionPath,
                                      int configuredBucketCount,
                                      int createdBucketCount,
                                      int lowRedundancyBucketCount,
                                      int configuredRedundantCopies,
                                      int actualRedundantCopies,
                                      Set<InternalPartitionDetails> memberDetails,
                                      String colocatedPath,
                                      OfflineMemberDetails offlineMembers) {
    this.regionPath = regionPath;
    this.configuredBucketCount = configuredBucketCount;
    this.createdBucketCount = createdBucketCount;
    this.lowRedundancyBucketCount = lowRedundancyBucketCount;
    this.configuredRedundantCopies = configuredRedundantCopies;
    this.actualRedundantCopies = actualRedundantCopies;
    this.memberDetails = memberDetails;
    this.colocatedWith = colocatedPath;
    this.offlineMembers = offlineMembers;
  }
  
  public int getActualRedundantCopies() {
    return this.actualRedundantCopies;
  }

  public String getColocatedWith() {
    return this.colocatedWith;
  }

  public int getConfiguredBucketCount() {
    return this.configuredBucketCount;
  }

  public int getConfiguredRedundantCopies() {
    return this.configuredRedundantCopies;
  }

  public int getCreatedBucketCount() {
    return this.createdBucketCount;
  }

  public int getLowRedundancyBucketCount() {
    return this.lowRedundancyBucketCount;
  }

  public Set<PartitionMemberInfo> getPartitionMemberInfo() {
    return Collections.unmodifiableSet((Set<? extends PartitionMemberInfo>)this.memberDetails);
  }
  
  public Set<InternalPartitionDetails> getInternalPartitionDetails() {
    return Collections.unmodifiableSet((Set<? extends InternalPartitionDetails>)this.memberDetails);
  }


  public String getRegionPath() {
    return this.regionPath;
  }
  
  public OfflineMemberDetails getOfflineMembers() {
    return offlineMembers;
  }

  @Override
  public String toString() {
    final StringBuffer sb = new StringBuffer("[PartitionRegionInfoImpl: ");
    sb.append("regionPath=").append(this.regionPath);
    sb.append(", configuredBucketCount=").append(this.configuredBucketCount);
    sb.append(", createdBucketCount=").append(this.createdBucketCount);
    sb.append(", lowRedundancyBucketCount=").append(this.lowRedundancyBucketCount);
    sb.append(", configuredRedundantCopies=").append(this.configuredRedundantCopies);
    sb.append(", actualRedundantCopies=").append(this.actualRedundantCopies);
    sb.append(", memberDetails=").append(this.memberDetails);
    sb.append(", colocatedWith=").append(this.colocatedWith);
    sb.append(", offlineMembers=").append(this.offlineMembers);
    sb.append("]");
    return sb.toString();
  }

  /**
   * hashCode is defined for this class to make
   * sure that the before details and after details for
   * RebalanceResults are in the same order. This makes
   * debugging printouts easier, and it also removes
   * discrepancies due to rounding errors when calculating
   * the stddev in tests.
   */
  @Override
  public int hashCode() {
    return regionPath.hashCode();
  }
  @Override
  public boolean equals(Object other) {
    if (!(other instanceof PartitionRegionInfoImpl)) {
      return false;
    }
    PartitionRegionInfoImpl o = (PartitionRegionInfoImpl)other;
    return this.regionPath.equals(o.regionPath);
  }
  public int compareTo(InternalPRInfo other) {
    return this.regionPath.compareTo(other.getRegionPath());
  }
}
