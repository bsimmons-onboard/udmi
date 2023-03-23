package com.google.bos.udmi.service.messaging;

import static com.google.udmi.util.JsonUtil.convertTo;
import static com.google.udmi.util.JsonUtil.fromString;

import com.google.bos.udmi.service.pod.ComponentBase;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.udmi.util.Common;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jetbrains.annotations.NotNull;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.SystemState;

public abstract class MessageBase extends ComponentBase implements MessagePipe {

  private static final Envelope LOOP_EXIT_MARK = null;
  private static final String DEFAULT_HANDLER = "default_handler";
  private static final String EXCEPTION_HANDLER = "exception_handler";
  private static final Map<SimpleEntry<SubType, SubFolder>, Class<?>> SPECIAL_TYPES = ImmutableMap.of(
      getTypeFolderEntry(SubType.STATE, SubFolder.UPDATE), StateUpdate.class);
  private static final Map<Class<?>, SimpleEntry<SubType, SubFolder>> CLASS_TYPES = new HashMap<>();
  private static final BiMap<String, Class<?>> typeClasses = HashBiMap.create();

  {
    initializeHandlerTypes();
  }

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Map<String, MessageHandler<Object>> handlers = new HashMap<>();
  private final Map<Object, Envelope> messageEnvelopes = new ConcurrentHashMap<>();
  private final Map<Class<?>, String> specialClasses = ImmutableMap.of(
      Object.class, DEFAULT_HANDLER,
      Exception.class, EXCEPTION_HANDLER
  );
  private BlockingQueue<String> loopQueue;

  public MessageBase() {
    initializeHandlerTypes();
  }

  public static Bundle makeMessageBundle(Object message) {
    Bundle bundle = new Bundle();
    bundle.message = message;
    bundle.envelope = new Envelope();
    bundle.envelope.subType = CLASS_TYPES.get(message.getClass()).getKey();
    bundle.envelope.subFolder = CLASS_TYPES.get(message.getClass()).getValue();
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
        try {
          Bundle bundle = fromString(Bundle.class, loopQueue.take());
          // Lack of envelope can only happen intentionally as a signal to exist the loop.
          if (bundle.envelope == LOOP_EXIT_MARK) {
            return;
          }
          processMessage(bundle);
        } catch (Exception e) {
          processMessage(makeExceptionEnvelope(), EXCEPTION_HANDLER, e);
        }
      }
    } catch (Exception loopException) {
      info("Message loop exception: " + Common.getExceptionMessage(loopException));
    }
  }

  private Envelope makeExceptionEnvelope() {
    return new Envelope();
  }

  private void ignoreMessage(Object message) {
    info("Ignoring messages " + message);
  }

  void processMessage(Bundle bundle) {
    Envelope envelope = bundle.envelope;
    String mapKey = getMapKey(envelope.subType, envelope.subFolder);
    try {
      handlers.computeIfAbsent(mapKey, key -> {
        info("Defaulting messages of type/folder " + mapKey);
        return handlers.getOrDefault(DEFAULT_HANDLER, this::ignoreMessage);
      });
      Class<?> handlerType = typeClasses.getOrDefault(mapKey, Object.class);
      Object messageObject = convertTo(handlerType, bundle.message);
      processMessage(envelope, mapKey, messageObject);
    } catch (Exception e) {
      throw new RuntimeException("While processing message key " + mapKey, e);
    }
  }

  private void processMessage(Envelope envelope, String mapKey, Object messageObject) {
    MessageHandler<Object> handlerConsumer = handlers.get(mapKey);
    messageEnvelopes.put(messageObject, envelope);
    try {
      handlerConsumer.accept(messageObject);
    } finally {
      messageEnvelopes.remove(messageObject);
    }
  }

  public MessageContinuation getContinuation(Object message) {
    return new MessageContinuation(this, messageEnvelopes.get(message), message);
  }

  public abstract void publishBundle(Bundle bundle);

  private static void initializeHandlerTypes() {
    Arrays.stream(SubType.values()).forEach(type -> Arrays.stream(SubFolder.values())
        .forEach(folder -> registerHandlerType(type, folder)));
    SPECIAL_TYPES.forEach(
        (entry, clazz) -> registerMessageClass(entry.getKey(), entry.getValue(), clazz));
  }

  private static String getMapKey(SubType subType, SubFolder subFolder) {
    SubType useType = Optional.ofNullable(subType).orElse(SubType.EVENT);
    return String.format("%s/%s", useType, subFolder);
  }

  private static void registerHandlerType(SubType type, SubFolder folder) {
    Class<?> messageClass = getMessageClass(type, folder);
    registerMessageClass(type, folder, messageClass);
  }

  private static void registerMessageClass(SubType type, SubFolder folder, Class<?> messageClass) {
    String mapKey = getMapKey(type, folder);
    if (messageClass != null) {
      typeClasses.put(mapKey, messageClass);
      CLASS_TYPES.put(messageClass, getTypeFolderEntry(type, folder));
    }
  }

  @NotNull
  private static SimpleEntry<SubType, SubFolder> getTypeFolderEntry(SubType type,
      SubFolder folder) {
    return new SimpleEntry<>(type, folder);
  }

  private static Class<?> getMessageClass(SubType type, SubFolder folder) {
    String typeName = Common.capitalize(folder.value()) + Common.capitalize(type.value());
    String className = SystemState.class.getPackageName() + "." + typeName;
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

}
