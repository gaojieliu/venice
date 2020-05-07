package com.linkedin.venice.pushmonitor;

import java.util.List;
import org.apache.helix.zookeeper.zkclient.IZkChildListener;


/**
 * Accessor to execute the CURD operations for offline push and its replicas statuses. Based on different
 * implementation, statuses could be recorded on Zookeeper or other persistent storage.
 */
public interface OfflinePushAccessor {
  /**
   * Load entire database of offline pushes including offline pushes statues and their partitions statuses from
   * persistent storage.
   */
  List<OfflinePushStatus> loadOfflinePushStatusesAndPartitionStatuses();

  /**
   * Read one offline push and its partitions status from persistent storage.
   */
  OfflinePushStatus getOfflinePushStatusAndItsPartitionStatuses(String kafkaTopic);

  /**
   * Update status of the given offline push to persistent storage.
   */
  void updateOfflinePushStatus(OfflinePushStatus pushStatus);

  /**
   * Create offline push and its partition statues on persistent storage.
   */
  void createOfflinePushStatusAndItsPartitionStatuses(OfflinePushStatus pushStatus);

  /**
   * Delete offline push and its partition statuses from persistent storage.
   * @param pushStatus
   */
  void deleteOfflinePushStatusAndItsPartitionStatuses(OfflinePushStatus pushStatus);
  /**
   * Update one particular replica status and progress by given topic, partition and instanceId to the persistent storage.
   */
  void updateReplicaStatus(String kafkaTopic, int partitionId, String instanceId, ExecutionStatus status,
      long progress, String message);
  /**
   * Update one particular replica status only by given topic, partition and instanceId to the persistent storage.
   */
  void updateReplicaStatus(String kafkaTopic, int partitionId, String instanceId, ExecutionStatus status, String message);

  /**
   * Subscribe the data change of partition status.
   */
  void subscribePartitionStatusChange(OfflinePushStatus pushStatus, PartitionStatusListener listener);

  /**
   * Unsubscribe the data change of partition status.
   * @param pushStatus
   * @param listener
   */
  void unsubscribePartitionsStatusChange(OfflinePushStatus pushStatus, PartitionStatusListener listener);

  /**
   * Unsubscribe the data change of partition status.
   */
  void unsubscribePartitionsStatusChange(String topicName, int partitionCount, PartitionStatusListener listener);

  /**
   * Subscribe a child listener that listens to OfflinePushStatus creation/deleted.
   */
  void subscribePushStatusCreationChange(IZkChildListener childListener);

  /**
   * Unsubscribe a child listener
   */
  void unsubscribePushStatusCreationChange(IZkChildListener childListener);

  /**
   * Listener used to listen the data change of partition status.
   */
  interface PartitionStatusListener {
    void onPartitionStatusChange(String topic, ReadOnlyPartitionStatus partitionStatus);
  }
}
