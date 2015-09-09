/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.umass.cs.gns.newApp.clientCommandProcessor.commandSupport;

import edu.umass.cs.gns.util.ByteUtils;

/**
 *
 * @author westy
 */
public abstract class BasicHashFunction implements HashFunction {
  
  /**
   *  Hashes a string to a long.
   * 
   * @param key
   * @return
   */
  @Override
  public long hashToLong(String key) {
    // assumes the first byte is the most significant
    return ByteUtils.byteArrayToLong(hash(key));
  }
  
}
