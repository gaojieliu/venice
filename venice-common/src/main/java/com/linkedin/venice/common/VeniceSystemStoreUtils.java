package com.linkedin.venice.common;

import com.linkedin.venice.meta.Store;


public class VeniceSystemStoreUtils {
  public static final String PARTICIPANT_STORE = "participant_store";
  public static final String PUSH_JOB_DETAILS_STORE = "push_job_details_store";

  private static final String PARTICIPANT_STORE_PREFIX = String.format(Store.SYSTEM_STORE_FORMAT, PARTICIPANT_STORE);
  private static final String PARTICIPANT_STORE_FORMAT = PARTICIPANT_STORE_PREFIX + "_cluster_%s";
  private static final String PUSH_JOB_DETAILS_STORE_NAME = String.format(Store.SYSTEM_STORE_FORMAT, PUSH_JOB_DETAILS_STORE);
  public static final String SEPARATOR = "_";

  public static String getParticipantStoreNameForCluster(String clusterName) {
    return String.format(PARTICIPANT_STORE_FORMAT, clusterName);
  }

  public static String getPushJobDetailsStoreName() {
    return PUSH_JOB_DETAILS_STORE_NAME;
  }

  public static boolean isSystemStore(String storeName) {
    return storeName.startsWith(Store.SYSTEM_STORE_NAME_PREFIX);
  }

  /**
   * @param storeName
   * @return the corresponding store name for shared Zookeeper if applicable. The same store name is returned otherwise.
   */
  public static String getZkStoreName(String storeName) {
    return storeName;
  }


  public static String getDaVinciPushStatusStoreName(String storeName) {
    return VeniceSystemStoreType.DAVINCI_PUSH_STATUS_STORE.getSystemStoreName(storeName);
  }

  public static String getMetaStoreName(String storeName) {
    return VeniceSystemStoreType.META_STORE.getSystemStoreName(storeName);
  }
}
