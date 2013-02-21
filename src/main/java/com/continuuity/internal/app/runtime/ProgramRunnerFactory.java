package com.continuuity.internal.app.runtime;

import com.continuuity.app.runtime.ProgramRunner;

/**
 *
 */
public interface ProgramRunnerFactory {

  public enum Type {
    FLOW,
    FLOWLET,
    PROCEDURE,
    BATCH
  }

  ProgramRunner create(Type programType);
}
