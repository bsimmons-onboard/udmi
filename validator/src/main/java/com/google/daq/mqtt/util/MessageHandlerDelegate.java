package com.google.daq.mqtt.util;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.daq.mqtt.validator.Validator.MessageBundle;
import com.google.udmi.util.Common;
import com.google.udmi.util.JsonUtil;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.SystemState;

public class MessageHandlerDelegate implements MessageHandler {
  private final Map<String, HandlerConsumer<Object>> handlers = new HashMap<>();
  private final BiMap<String, Class<?>> typeClasses = HashBiMap.create();
  private final Map<Class<?>, SimpleEntry<SubType, SubFolder>> classTypes = new HashMap<>();
  private final MessagePublisher publisher;

  public MessageHandlerDelegate(MessagePublisher publisher) {
    this.publisher = publisher;
    initializeHandlerTypes();

  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> void registerHandler(Class<T> clazz, HandlerConsumer<T> handler) {
    String mapKey = typeClasses.inverse().get(clazz);
    if (handlers.put(mapKey, (HandlerConsumer<Object>) handler) != null) {
      throw new RuntimeException("Type handler already defined for " + mapKey);
    }
  }

  @Override
  public void messageLoop() {
    while (publisher.isActive()) {
      try {
        handlerHandler(publisher.takeNextMessage());
      } catch (Exception e) {
        System.err.println("Exception processing received message:");
        e.printStackTrace();
      }
    }
  }

  @Override
  public void publishMessage(String deviceId, Object message) {
    SimpleEntry<SubType, SubFolder> typePair = classTypes.get(message.getClass());
    String mqttTopic = getMapKey(typePair.getKey(), typePair.getValue());
    publisher.publish(deviceId, mqttTopic, JsonUtil.stringify(message));
  }

  private void initializeHandlerTypes() {
    Arrays.stream(SubType.values()).forEach(type -> Arrays.stream(SubFolder.values())
        .forEach(folder -> registerHandlerType(type, folder)));
  }

  private void registerHandlerType(SubType type, SubFolder folder) {
    String mapKey = getMapKey(type, folder);
    Class<?> messageClass = getMessageClass(type, folder);
    if (messageClass != null) {
      typeClasses.put(mapKey, messageClass);
      classTypes.put(messageClass, new SimpleEntry<>(type, folder));
    }
  }

  private Class<?> getMessageClass(SubType type, SubFolder folder) {
    String typeName = Common.capitalize(folder.value()) + Common.capitalize(type.value());
    String className = SystemState.class.getPackageName() + "." + typeName;
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  private String getMapKey(SubType subType, SubFolder subFolder) {
    return (subType != null ? subType : SubType.EVENT) + "/" + subFolder;
  }

  private void ignoreMessage(Envelope attributes, Object message) {
  }

  private void handlerHandler(MessageBundle bundle) {
    Envelope envelope = JsonUtil.convertTo(Envelope.class, bundle.attributes);
    String mapKey = getMapKey(envelope.subType, envelope.subFolder);
    try {
      Class<?> handlerType = typeClasses.computeIfAbsent(mapKey, key -> {
        System.err.println("Ignoring messages of type " + mapKey);
        return Object.class;
      });
      Object messageObject = JsonUtil.convertTo(handlerType, bundle.message);
      HandlerConsumer<Object> handlerConsumer = handlers.computeIfAbsent(mapKey,
          key -> this::ignoreMessage);
      handlerConsumer.accept(envelope, messageObject);
    } catch (Exception e) {
      throw new RuntimeException("While processing message key " + mapKey, e);
    }
  }


}
