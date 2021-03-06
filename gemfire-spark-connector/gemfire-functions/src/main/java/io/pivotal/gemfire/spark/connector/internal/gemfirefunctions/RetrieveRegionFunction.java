package io.pivotal.gemfire.spark.connector.internal.gemfirefunctions;

import java.util.Iterator;
import org.apache.logging.log4j.Logger;

import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.CacheFactory;
import com.gemstone.gemfire.cache.execute.FunctionException;
import com.gemstone.gemfire.cache.query.Query;
import com.gemstone.gemfire.cache.query.QueryService;
import com.gemstone.gemfire.cache.query.SelectResults;
import com.gemstone.gemfire.cache.query.Struct;
import com.gemstone.gemfire.internal.cache.*;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.execute.Function;
import com.gemstone.gemfire.cache.execute.FunctionContext;
import com.gemstone.gemfire.cache.partition.PartitionRegionHelper;
import com.gemstone.gemfire.internal.cache.execute.InternalRegionFunctionContext;
import com.gemstone.gemfire.internal.cache.execute.InternalResultSender;
import com.gemstone.gemfire.internal.cache.partitioned.PREntriesIterator;
import com.gemstone.gemfire.internal.logging.LogService;

/**
 * GemFire function that is used by `SparkContext.gemfireRegion(regionPath, whereClause)`
 * to retrieve region data set for the given bucket set as a RDD partition 
 **/
public class RetrieveRegionFunction implements Function {

  public final static String ID = "spark-gemfire-retrieve-region";
  private static final Logger logger = LogService.getLogger();
  private static final RetrieveRegionFunction instance = new RetrieveRegionFunction();

  public RetrieveRegionFunction() {
  }

  /** ------------------------------------------ */
  /**     interface Function implementation      */
  /** ------------------------------------------ */

  public static RetrieveRegionFunction getInstance() {
    return instance;
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public boolean hasResult() {
    return true;
  }

  @Override
  public boolean optimizeForWrite() {
    return true;
  }

  @Override
  public boolean isHA() {
    return true;
  }

  @Override
  public void execute(FunctionContext context) {
    String[] args = (String[]) context.getArguments();
    String where = args[0];
    String taskDesc = args[1];
    InternalRegionFunctionContext irfc = (InternalRegionFunctionContext) context;
    LocalRegion localRegion = (LocalRegion) irfc.getDataSet();
    boolean partitioned = localRegion.getDataPolicy().withPartitioning();
    if (where.trim().isEmpty())
      retrieveFullRegion(irfc, partitioned, taskDesc);
    else
      retrieveRegionWithWhereClause(irfc, localRegion, partitioned, where, taskDesc);
  }

  /** ------------------------------------------ */
  /**    Retrieve region data with where clause  */
  /** ------------------------------------------ */

  private void retrieveRegionWithWhereClause(
    InternalRegionFunctionContext context, LocalRegion localRegion, boolean partitioned, String where, String desc) {
    String regionPath = localRegion.getFullPath();
    String qstr = "select key, value from " + regionPath + ".entries where " + where;
    logger.info(desc + ": " + qstr);

    try {
      Cache cache = CacheFactory.getAnyInstance();
      QueryService queryService = cache.getQueryService();
      Query query = queryService.newQuery(qstr);
      SelectResults<Struct> results =
        (SelectResults<Struct>) (partitioned ?  query.execute(context) : query.execute());

      Iterator<Object[]> entries = getStructIteratorWrapper(results.asList().iterator());
      InternalResultSender irs = (InternalResultSender) context.getResultSender();
      StructStreamingResultSender sender = new StructStreamingResultSender(irs, null, entries, desc);
      sender.send();
    } catch (Exception e) {
      throw new FunctionException(e);
    }
  }

  private Iterator<Object[]> getStructIteratorWrapper(Iterator<Struct> entries) {
    return new WrapperIterator<Struct, Iterator<Struct>>(entries) {
      @Override public Object[] next() {
        return  delegate.next().getFieldValues();
      }
    };
  }

  /** ------------------------------------------ */
  /**         Retrieve full region data          */
  /** ------------------------------------------ */

  private void retrieveFullRegion(InternalRegionFunctionContext context, boolean partitioned, String desc) {
    Iterator<Object[]> entries;
    if (partitioned) {
      PREntriesIterator<Region.Entry> iter = (PREntriesIterator<Region.Entry>)
              ((LocalDataSet) PartitionRegionHelper.getLocalDataForContext(context)).entrySet().iterator();
      // entries = getPREntryIterator(iter);
      entries = getSimpleEntryIterator(iter);
    } else {
      LocalRegion owner = (LocalRegion) context.getDataSet();
      Iterator<Region.Entry> iter = (Iterator<Region.Entry>) owner.entrySet().iterator();
      // entries = getRREntryIterator(iter, owner);
      entries = getSimpleEntryIterator(iter);
    }
    InternalResultSender irs = (InternalResultSender) context.getResultSender();
    StructStreamingResultSender sender = new StructStreamingResultSender(irs, null, entries, desc);
    sender.send();
  }

//  /** An iterator for partitioned region that uses internal API to get serialized value */
//  private Iterator<Object[]> getPREntryIterator(PREntriesIterator<Region.Entry> iterator) {
//    return new WrapperIterator<Region.Entry, PREntriesIterator<Region.Entry>>(iterator) {
//      @Override public Object[] next() {
//        Region.Entry entry = delegate.next();
//        int bucketId = delegate.getBucketId();
//        KeyInfo keyInfo = new KeyInfo(entry.getKey(), null, bucketId);
//        // owner needs to be the bucket region not the enclosing partition region
//        LocalRegion owner = ((PartitionedRegion) entry.getRegion()).getDataStore().getLocalBucketById(bucketId);
//        Object value = owner.getDeserializedValue(keyInfo, false, true, true, null, false);
//        return new Object[] {keyInfo.getKey(), value};
//      }
//    };
//  }
//
//  /** An iterator for replicated region that uses internal API to get serialized value */
//  private Iterator<Object[]> getRREntryIterator(Iterator<Region.Entry> iterator, LocalRegion region) {
//    final LocalRegion owner = region;
//    return new WrapperIterator<Region.Entry, Iterator<Region.Entry>>(iterator) {
//      @Override public Object[] next() {
//        Region.Entry entry =  delegate.next();
//        KeyInfo keyInfo = new KeyInfo(entry.getKey(), null, null);
//        Object value = owner.getDeserializedValue(keyInfo, false, true, true, null, false);
//        return new Object[] {keyInfo.getKey(), value};
//      }
//    };
//  }

  // todo. compare performance of regular and simple iterator
  /** An general iterator for both partitioned and replicated region that returns un-serialized value */
  private Iterator<Object[]> getSimpleEntryIterator(Iterator<Region.Entry> iterator) {
    return new WrapperIterator<Region.Entry, Iterator<Region.Entry>>(iterator) {
      @Override public Object[] next() {
        Region.Entry entry = delegate.next();
        return new Object[] {entry.getKey(), entry.getValue()};
      }
    };
  }

  /** ------------------------------------------ */
  /**        abstract wrapper iterator           */
  /** ------------------------------------------ */

  /** An abstract wrapper iterator to reduce duplicated code of anonymous iterators */
  abstract class WrapperIterator<T, S extends Iterator<T>> implements Iterator<Object[]> {

    final S delegate;

    protected WrapperIterator(S delegate) {
      this.delegate = delegate;
    }

    @Override public boolean hasNext() {
      return delegate.hasNext();
    }

    @Override public void remove() { }
  }
}
