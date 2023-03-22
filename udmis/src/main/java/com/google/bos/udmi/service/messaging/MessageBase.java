package com.google.bos.udmi.service.messaging;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Features;
import com.google.bos.udmi.service.pod.ComponentBase;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.udmi.util.Common;
import com.google.udmi.util.JsonUtil;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.SystemState;

public abstract class MessageBase extends ComponentBase implements MessagePipe {

  public static final Envelope LOOP_EXIT_MARK = null;
  public static final String DEFAULT_HANDLER = "default_handler";
  public static final String EXCEPTION_HANDLER = "exception_handler";
  ExecutorService executor = Executors.newSingleThreadExecutor();
  private BlockingQueue<String> loopQueue;
  private final Map<String, MessageHandler<Object>> handlers = new HashMap<>();
  private final BiMap<String, Class<?>> typeClasses = HashBiMap.create();
  private final Map<Class<?>, SimpleEntry<SubType, SubFolder>> classTypes = new HashMap<>();
  private final Map<Object, Envelope> messageEnvelopes = new ConcurrentHashMap<>();
  private final Map<Class<?>, String> specialClasses = ImmutableMap.of(
      Object.class, DEFAULT_HANDLER,
      Exception.class, EXCEPTION_HANDLER
  );

  public MessageBase() {
    initializeHandlerTypes();
  }

  public static Bundle makeBundle(Object message) {
    Bundle bundle = new Bundle();
    bundle.message = message;
    bundle.envelope = new Envelope();
    return bundle;
  }

  public static class Bundle {
    public Envelope envelope;
    public Object message;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> void registerHandler(Class<T> clazz, MessageHandler<T> handler) {
    String mapKey = specialClasses.getOrDefault(clazz, typeClasses.inverse().get(clazz));
    if (handlers.put(mapKey, (MessageHandler<Object>) handler) != null) {
      throw new RuntimeException("Type handler already defined for " + mapKey);
    }
  }

  protected void handleQueue(BlockingQueue<String> queue) {
    loopQueue = queue;
    executor.submit(this::messageLoop);
  }

  private void messageLoop() {
    try {
      while (true) {
        Bundle bundle = JsonUtil.fromString(Bundle.class, loopQueue.take());
        // Lack of envelope can only happen intentionally as a signal to exist the loop.
        if (bundle.envelope == LOOP_EXIT_MARK) {
          return;
        }
        processMessage(bundle);
      }
    } catch (Exception e) {
      throw new RuntimeException("While processing message loop", e);
    }
  }

  private void ignoreMessage(Object message) {
    info("Ignoring messages " + message);
  }

  private void processMessage(Bundle bundle) {
    Envelope envelope = bundle.envelope;
    String mapKey = getMapKey(envelope.subType, envelope.subFolder);
    try {
      Class<?> handlerType = typeClasses.computeIfAbsent(mapKey, key -> {
        info("Defaulting messages of type/folder " + mapKey);
        MessageHandler<Object> handler = handlers.getOrDefault(DEFAULT_HANDLER, this::ignoreMessage);
        handlers.put(mapKey, handler);
        return Object.class;
      });
      Object messageObject = JsonUtil.convertTo(handlerType, bundle.message);
      MessageHandler<Object> handlerConsumer = handlers.get(mapKey);
      messageEnvelopes.put(messageObject, envelope);
      handlerConsumer.accept(messageObject);
    } catch (Exception e) {
      throw new RuntimeException("While processing message key " + mapKey, e);
    }
  }

  public Envelope getEnvelopeFor(Object message) {
    return messageEnvelopes.get(message);
  }

  private void initializeHandlerTypes() {
    Arrays.stream(SubType.values()).forEach(type -> Arrays.stream(SubFolder.values())
        .forEach(folder -> registerHandlerType(type, folder)));
  }

  private String getMapKey(SubType subType, SubFolder subFolder) {
    SubType useType = Optional.ofNullable(subType).orElse(SubType.EVENT);
    return String.format("%s/%s", useType, subFolder);
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

}
