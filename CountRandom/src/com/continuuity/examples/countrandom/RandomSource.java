/*
 * Copyright (c) 2013, Continuuity Inc
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms,
 * with or without modification, are not permitted
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.continuuity.examples.countrandom;

import com.continuuity.api.flow.flowlet.AbstractGeneratorFlowlet;
import com.continuuity.api.flow.flowlet.OutputEmitter;

import java.util.Random;

/**
 * Random Source Flowlet {@code RandomSource}.
 */
public class RandomSource extends AbstractGeneratorFlowlet {
  private OutputEmitter<Integer> randomOutput;

  private final Random random = new Random();
  private long millis = 1;
  private int direction = 1;

  public void generate() throws InterruptedException {
    Integer randomNumber = new Integer(this.random.nextInt(10000));
    Thread.sleep(millis);
    millis += direction;

    if (millis > 100 || millis < 1) {
      direction = direction * -1;
    }
    randomOutput.emit(randomNumber);
  }
}
