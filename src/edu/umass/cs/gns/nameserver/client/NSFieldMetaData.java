/*
 * Copyright (C) 2013
 * University of Massachusetts
 * All Rights Reserved 
 */
package edu.umass.cs.gns.nameserver.client;

import edu.umass.cs.gns.client.*;
import edu.umass.cs.gns.exceptions.FieldNotFoundException;
import edu.umass.cs.gns.exceptions.RecordNotFoundException;
import edu.umass.cs.gns.nameserver.NameRecord;
import edu.umass.cs.gns.nameserver.NameServer;
import edu.umass.cs.gns.nameserver.ResultValue;
import java.util.HashSet;
import java.util.Set;

/**
 * Implements metadata on fields.
 *
 * @author westy
 */
public class NSFieldMetaData {

  /**
   * Grabs the metadata indexed by type from the field from the guid.
   * 
   * @param type
   * @param guidInfo
   * @param key
   * @return 
   */
  public static Set<String> lookup(MetaDataTypeName type, GuidInfo guidInfo, String key) throws RecordNotFoundException, FieldNotFoundException {
    return lookup(type, guidInfo.getGuid(), key);
  }

  /**
   * Grabs the metadata indexed by type from the field from the guid.
   * 
   * @param type
   * @param guid
   * @param key
   * @return 
   */
  public static Set<String> lookup(MetaDataTypeName type, String guid, String key) throws RecordNotFoundException, FieldNotFoundException {
    String metaDateFieldName = FieldMetaData.makeFieldMetaDataKey(type, key);
    NameRecord nameRecord = NameServer.getNameRecordMultiField(guid, null, metaDateFieldName);
    ResultValue result = nameRecord.getKey(metaDateFieldName);
    return new HashSet(result);

  }
}
