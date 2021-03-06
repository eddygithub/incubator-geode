package com.gemstone.gemfire.internal.process;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.gemstone.gemfire.internal.util.IOUtils;
import com.gemstone.gemfire.internal.util.StopWatch;

/**
 * Manages which implementation of {@link ProcessController} will be used and
 * constructs the instance.
 * 
 * @author Kirk Lund
 * @since 8.0
 */
public final class ProcessControllerFactory {

  public static final String PROPERTY_DISABLE_ATTACH_API = "gemfire.test.ProcessControllerFactory.DisableAttachApi";
  
  private final boolean disableAttachApi;
  
  public ProcessControllerFactory() {
    this.disableAttachApi = Boolean.getBoolean(PROPERTY_DISABLE_ATTACH_API);
  }
  
  public ProcessController createProcessController(final ProcessControllerParameters arguments, final int pid) {
    if (arguments == null) {
      throw new NullPointerException("ProcessControllerParameters must not be null");
    }
    if (pid < 1) {
      throw new IllegalArgumentException("Invalid pid '" + pid + "' specified");
    }
    try {
      if (isAttachAPIFound()) {
        return new MBeanProcessController((MBeanControllerParameters)arguments, pid);
      } else {
        return new FileProcessController((FileControllerParameters)arguments, pid);
      }
    } catch (final ExceptionInInitializerError e) {
      //LOGGER.warn("Attach API class not found", e);
    }
    return null;
  }
  
  public ProcessController createProcessController(final ProcessControllerParameters arguments, final File pidFile, final long timeout, final TimeUnit unit) throws IOException, InterruptedException, TimeoutException {
    if (arguments == null) {
      throw new NullPointerException("ProcessControllerParameters must not be null");
    }
    if (pidFile == null) {
      throw new NullPointerException("Pid file must not be null");
    }
    return createProcessController(arguments, new PidFile(pidFile).readPid(timeout, unit));
  }
  
  public ProcessController createProcessController(final ProcessControllerParameters arguments, final File directory, final String pidFilename, final long timeout, final TimeUnit unit) throws IOException, InterruptedException, TimeoutException {
    if (arguments == null) {
      throw new NullPointerException("ProcessControllerParameters must not be null");
    }
    if (directory == null) {
      throw new NullPointerException("Directory must not be null");
    }
    if (pidFilename == null) {
      throw new NullPointerException("Pid file name must not be null");
    }
    return createProcessController(arguments, new PidFile(directory, pidFilename).readPid(timeout, unit));
  }

  public boolean isAttachAPIFound() {
    if (this.disableAttachApi) {
      return false;
    }
    boolean found = false;
    try {
      final Class<?> virtualMachineClass = Class.forName("com.sun.tools.attach.VirtualMachine");
      found = virtualMachineClass != null;
    } catch (ClassNotFoundException e) {
    }
    return found;
  }
}
