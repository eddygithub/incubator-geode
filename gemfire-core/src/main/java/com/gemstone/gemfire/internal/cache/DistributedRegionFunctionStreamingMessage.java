/*=========================================================================
 * Copyright (c) 2010-2014 Pivotal Software, Inc. All Rights Reserved.
 * This product is protected by U.S. and international copyright
 * and intellectual property laws. Pivotal products are covered by
 * one or more patents listed at http://www.pivotal.io/patents.
 *=========================================================================
 */
package com.gemstone.gemfire.internal.cache;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.Logger;

import com.gemstone.gemfire.CancelException;
import com.gemstone.gemfire.DataSerializer;
import com.gemstone.gemfire.SystemFailure;
import com.gemstone.gemfire.cache.CacheClosedException;
import com.gemstone.gemfire.cache.CacheException;
import com.gemstone.gemfire.cache.RegionDestroyedException;
import com.gemstone.gemfire.cache.execute.Function;
import com.gemstone.gemfire.cache.execute.FunctionException;
import com.gemstone.gemfire.cache.execute.FunctionService;
import com.gemstone.gemfire.distributed.internal.DM;
import com.gemstone.gemfire.distributed.internal.DistributionManager;
import com.gemstone.gemfire.distributed.internal.DistributionMessage;
import com.gemstone.gemfire.distributed.internal.InternalDistributedSystem;
import com.gemstone.gemfire.distributed.internal.ReplyException;
import com.gemstone.gemfire.distributed.internal.ReplyMessage;
import com.gemstone.gemfire.distributed.internal.ReplyProcessor21;
import com.gemstone.gemfire.distributed.internal.membership.InternalDistributedMember;
import com.gemstone.gemfire.internal.i18n.LocalizedStrings;
import com.gemstone.gemfire.internal.logging.LogService;
import com.gemstone.gemfire.internal.logging.log4j.LogMarker;

public class DistributedRegionFunctionStreamingMessage extends DistributionMessage implements TransactionMessage {

  private static final Logger logger = LogService.getLogger();
  
  private Object result;
  
  transient int replyMsgNum = 0;

  transient boolean replyLastMsg;

  transient int numObjectsInChunk = 0;

  private Function functionObject;
  
  private String functionName; 
  
  Object args;

  private String regionPath;

  private Set filter;
  
  private int processorId;
  
  private boolean isReExecute;
  
  private boolean isFnSerializationReqd;
  
  private int txUniqId = TXManagerImpl.NOTX;

  private InternalDistributedMember txMemberId = null;

  private static final short IS_REEXECUTE = UNRESERVED_FLAGS_START;

  /** default exception to ensure a false-positive response is never returned */
  static final ForceReattemptException UNHANDLED_EXCEPTION = (ForceReattemptException)new ForceReattemptException(
      "Unknown exception").fillInStackTrace();

  public DistributedRegionFunctionStreamingMessage() {
  }

  public DistributedRegionFunctionStreamingMessage(final String regionPath,
      Function function, int procId, final Set filter, Object args,
      boolean isReExecute, boolean isFnSerializationReqd) {
    this.functionObject = function;
    this.processorId = procId;
    this.args = args;
    this.regionPath = regionPath;
    this.filter = filter;
    this.isReExecute = isReExecute;
    this.isFnSerializationReqd = isFnSerializationReqd;
    this.txUniqId = TXManagerImpl.getCurrentTXUniqueId();
    TXStateProxy txState = TXManagerImpl.getCurrentTXState();
    if(txState!=null && txState.isMemberIdForwardingRequired()) {
      this.txMemberId = txState.getOriginatingMember();
    }
  }

  private TXStateProxy prepForTransaction() throws InterruptedException {
    if (this.txUniqId == TXManagerImpl.NOTX) {
      return null;
    } else {
      GemFireCacheImpl cache = GemFireCacheImpl.getInstance();
      if(cache==null) {
        // ignore and return, we are shutting down!
        return null;
      }
      TXManagerImpl mgr = cache.getTXMgr();
      return mgr.masqueradeAs(this);
    }
  }

  private void cleanupTransasction(TXStateProxy tx) {
    if (this.txUniqId != TXManagerImpl.NOTX) {
      GemFireCacheImpl cache = GemFireCacheImpl.getInstance();
      if(cache==null) {
        // ignore and return, we are shutting down!
        return;
      }
      TXManagerImpl mgr = cache.getTXMgr();
      mgr.unmasquerade(tx);
    }
  }

  @Override
  protected void process(final DistributionManager dm) {

    Throwable thr = null;
    boolean sendReply = true;
    DistributedRegion dr = null;
    TXStateProxy tx = null;

    try {
      if (checkCacheClosing(dm) || checkDSClosing(dm)) {
        thr = new CacheClosedException(LocalizedStrings.PartitionMessage_REMOTE_CACHE_IS_CLOSED_0.toLocalizedString(dm.getId()));
        return;
      }
      dr = (DistributedRegion)GemFireCacheImpl.getInstance().getRegion(
          this.regionPath);
      if (dr == null) {
        // if the distributed system is disconnecting, don't send a reply saying
        // the partitioned region can't be found (bug 36585)
        thr = new ForceReattemptException(dm.getDistributionManagerId()
            .toString()
            + ": could not find Distributed region " + regionPath);
        return; // reply sent in finally block below
      }
      thr = UNHANDLED_EXCEPTION;
      tx = prepForTransaction();
      sendReply = operateOnDistributedRegion(dm, dr); // need to take care of
                                                      // it...
      thr = null;
    }
    catch (CancelException se) {
      // bug 37026: this is too noisy...
      // throw new CacheClosedException("remote system shutting down");
      // thr = se; cache is closed, no point trying to send a reply
      thr = null;
      sendReply = false;
      if (logger.isDebugEnabled()) {
        logger.debug("shutdown caught, abandoning message: {}", se.getMessage(), se);
      }
    }
    catch (RegionDestroyedException rde) {
      // [bruce] RDE does not always mean that the sender's region is also
      // destroyed, so we must send back an exception. If the sender's
      // region is also destroyed, who cares if we send it an exception
      if (dr != null && dr.isClosed()) {
        thr = new ForceReattemptException("Region is destroyed in "
            + dm.getDistributionManagerId(), rde);
      }
    }
    catch (VirtualMachineError err) {
      SystemFailure.initiateFailure(err);
      // If this ever returns, rethrow the error. We're poisoned
      // now, so don't let this thread continue.
      throw err;
    }
    catch (Throwable t) {
      logger.debug("{} exception occured while processing message: {}", this, t.getMessage(), t);
      // Whenever you catch Error or Throwable, you must also
      // catch VirtualMachineError (see above). However, there is
      // _still_ a possibility that you are dealing with a cascading
      // error condition, so you also need to check to see if the JVM
      // is still usable:
      SystemFailure.checkFailure();
      // log the exception at fine level if there is no reply to the message
      thr = null;
      if (sendReply && this.processorId != 0) {
        if (!checkDSClosing(dm)) {
          thr = t;
        }
        else {
          // don't pass arbitrary runtime exceptions and errors back if this
          // cache/vm is closing
          thr = new ForceReattemptException(
              "Distributed system is disconnecting");
        }
      }
      if (this.processorId == 0) {
        logger.debug("{} exception while processing message: {}", this, t.getMessage(), t);
      }
      else if (logger.isTraceEnabled(LogMarker.DM) && (t instanceof RuntimeException)) {
        logger.trace(LogMarker.DM, "Exception caught while processing message", t);
      }
    }
    finally {
      cleanupTransasction(tx);
      if (sendReply && this.processorId != 0) {
        ReplyException rex = null;
        if (thr != null) {
          // don't transmit the exception if this message was to a listener
          // and this listener is shutting down
          boolean excludeException = false;
          if (!this.functionObject.isHA()) {
            excludeException = (thr instanceof CacheClosedException || (thr instanceof ForceReattemptException));
          }
          else {
            excludeException = thr instanceof ForceReattemptException;
          }
          if (!excludeException) {
            rex = new ReplyException(thr);
          }
        }
        // Send the reply if the operateOnPartitionedRegion returned true
        // Fix for hang in dunits on sqlfabric after merge.
        //ReplyMessage.send(getSender(), this.processorId, rex, dm);
        sendReply(getSender(), this.processorId, dm, rex, null, 0, true, false);
      }
    }
  }

  protected final boolean operateOnDistributedRegion(
      final DistributionManager dm, DistributedRegion r)
      throws ForceReattemptException {
    if (this.functionObject == null) {
      ReplyMessage.send(getSender(), this.processorId, new ReplyException(
          new FunctionException(LocalizedStrings.ExecuteFunction_FUNCTION_NAMED_0_IS_NOT_REGISTERED
            .toLocalizedString(this.functionName))), dm, r.isInternalRegion());

      return false;
    }
    
    
    if (logger.isTraceEnabled(LogMarker.DM)) {
      logger.trace(LogMarker.DM, "FunctionMessage operateOnRegion: {}", r.getFullPath());
    }
    try {
      r.executeOnRegion(this, this.functionObject, this.args, this.processorId,
          this.filter, this.isReExecute);
      if (!this.replyLastMsg && this.functionObject.hasResult()) {
        ReplyMessage
            .send(
                getSender(),
                this.processorId,
                new ReplyException(
                    new FunctionException(
                        LocalizedStrings.ExecuteFunction_THE_FUNCTION_0_DID_NOT_SENT_LAST_RESULT
                            .toString(functionObject.getId()))), dm, r.isInternalRegion());
        return false;
      }
    } catch (IOException e) {
      ReplyMessage.send(getSender(), this.processorId, new ReplyException(
          "Operation got interrupted due to shutdown in progress on remote VM",
          e), dm, r.isInternalRegion());
      return false;
    } catch (CancelException sde) {
      ReplyMessage
          .send(
              getSender(),
              this.processorId,
              new ReplyException(
                  new ForceReattemptException(
                      "Operation got interrupted due to shutdown in progress on remote VM",
                      sde)), dm, r.isInternalRegion());
      return false;
    }

    // Unless there was an exception thrown, this message handles sending the
    // response
    return false;
  }

  /**
   * check to see if the cache is closing
   */
  final public boolean checkCacheClosing(DistributionManager dm) {
    GemFireCacheImpl cache = GemFireCacheImpl.getInstance();
    return (cache == null || cache.getCancelCriterion().cancelInProgress() != null);
  }

  /**
   * check to see if the distributed system is closing
   * 
   * @return true if the distributed system is closing
   */
  final public boolean checkDSClosing(DistributionManager dm) {
    InternalDistributedSystem ds = dm.getSystem();
    return (ds == null || ds.isDisconnecting());
  }

  @Override
  public int getProcessorId() {
    return this.processorId;
  }

  public int getDSFID() {
    return DR_FUNCTION_STREAMING_MESSAGE;
  }

  @Override
  public void fromData(DataInput in) throws IOException, ClassNotFoundException {
    super.fromData(in);

    short flags = in.readShort();
    if ((flags & HAS_PROCESSOR_ID) != 0) {
      this.processorId = in.readInt();
      ReplyProcessor21.setMessageRPId(this.processorId);
    }
    if ((flags & HAS_TX_ID) != 0) this.txUniqId = in.readInt();
    if ((flags & HAS_TX_MEMBERID) != 0) {
      this.txMemberId = DataSerializer.readObject(in);
    }

    Object object = DataSerializer.readObject(in);
    if (object instanceof String) {
      this.isFnSerializationReqd = false; 
      this.functionObject = FunctionService.getFunction((String)object);
      if (this.functionObject == null) {
       this.functionName = (String)object;
      }
    }
    else {
      this.functionObject = (Function)object;
      this.isFnSerializationReqd = true;
    }
    this.args = (Serializable)DataSerializer.readObject(in);
    this.filter = (HashSet)DataSerializer.readHashSet(in);
    this.regionPath = DataSerializer.readString(in);
    this.isReExecute = (flags & IS_REEXECUTE) != 0;
  }

  @Override
  public void toData(DataOutput out) throws IOException {
    super.toData(out);

    short flags = 0;
    if (this.processorId != 0) flags |= HAS_PROCESSOR_ID;
    if (this.txUniqId != TXManagerImpl.NOTX) flags |= HAS_TX_ID;
    if (this.txMemberId != null) flags |= HAS_TX_MEMBERID;
    if (this.isReExecute) flags |= IS_REEXECUTE;
    out.writeShort(flags);
    if (this.processorId != 0) out.writeInt(this.processorId);
    if (this.txUniqId != TXManagerImpl.NOTX) out.writeInt(this.txUniqId);
    if (this.txMemberId != null) {
      DataSerializer.writeObject(this.txMemberId, out);
    }

    if(this.isFnSerializationReqd){
      DataSerializer.writeObject(this.functionObject, out);
    }
    else{
      DataSerializer.writeObject(functionObject.getId(),out);
    }
    DataSerializer.writeObject(this.args, out);
    DataSerializer.writeHashSet((HashSet)this.filter, out);
    DataSerializer.writeString(this.regionPath, out);
  }

  public synchronized boolean sendReplyForOneResult(DM dm,
      Object oneResult, boolean lastResult, boolean sendResultsInOrder)
      throws CacheException, ForceReattemptException,
      InterruptedException {
    if(this.replyLastMsg) {
      return false;
    }
    if (Thread.interrupted())
      throw new InterruptedException();
    int msgNum = this.replyMsgNum;
    this.replyLastMsg = lastResult;

    sendReply(getSender(), this.processorId, dm, null, oneResult,
        msgNum, lastResult, sendResultsInOrder);
    
    if (logger.isDebugEnabled()) {
      logger.debug("Sending reply message count: {} to co-ordinating node", replyMsgNum);
    }
    this.replyMsgNum++;
    return false;
  }

  protected void sendReply(InternalDistributedMember member, int procId, DM dm,
      ReplyException ex, Object result, int msgNum, boolean lastResult,
      boolean sendResultsInOrder) {
    // if there was an exception, then throw out any data
    if (ex != null) {
      this.result = null;
      this.replyMsgNum = 0;
      this.replyLastMsg = true;
    }
    if (sendResultsInOrder) {
      FunctionStreamingOrderedReplyMessage.send(member, procId, ex, dm, result,
          msgNum, lastResult);
    }
    else {
      FunctionStreamingReplyMessage.send(member, procId, ex, dm, result,
          msgNum, lastResult);
    }
  }
  
  @Override
  final public int getProcessorType() {
    return DistributionManager.REGION_FUNCTION_EXECUTION_EXECUTOR;
  }

  /* (non-Javadoc)
   * @see com.gemstone.gemfire.internal.cache.TransactionMessage#canStartRemoteTransaction()
   */
  public boolean canStartRemoteTransaction() {
    return true;
  }

  /* (non-Javadoc)
   * @see com.gemstone.gemfire.internal.cache.TransactionMessage#getTXUniqId()
   */
  public int getTXUniqId() {
    return txUniqId;
  }

  public final InternalDistributedMember getMemberToMasqueradeAs() {
	  if(txMemberId==null) {
		  return getSender();
	  } else {
		  return txMemberId;
	  }
  }

  public InternalDistributedMember getTXOriginatorClient() {
    // TODO Auto-generated method stub
    return null;
  }
  
  @Override
  public boolean canParticipateInTransaction() {
    return true;
  }
  
  @Override
  public boolean isTransactionDistributed() {
    return false;
  }
}
