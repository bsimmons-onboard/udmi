package com.google.daq.mqtt.util;

import com.google.daq.mqtt.validator.Validator.MessageBundle;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;

/**
 * Interface for publishing messages as raw maps.
 */
public interface MessagePublisher {
  String TYPE_FOLDER_FORMAT = "%s/%s";

  String publish(String deviceId, String typeFolder, String data);

  default String publish(String deviceId, SubType type, SubFolder folder, String data) {
    return publish(deviceId, String.format(TYPE_FOLDER_FORMAT, type.value(), folder.value()), data);
  }

  void close();

  String getSubscriptionId();

  boolean isActive();

  MessageBundle takeNextMessage();
}
