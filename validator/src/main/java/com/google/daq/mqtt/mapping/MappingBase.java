package com.google.daq.mqtt.mapping;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.udmi.util.Common.removeNextArg;

import com.google.common.base.Joiner;
import com.google.daq.mqtt.util.MessageHandler;
import com.google.daq.mqtt.util.MessageHandler.HandlerConsumer;
import com.google.daq.mqtt.util.MessageHandler.HandlerSpecification;
import com.google.daq.mqtt.util.MessageReadingClient;
import com.google.daq.mqtt.util.PubSubClient;
import com.google.udmi.util.SiteModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

abstract class MappingBase {

  private String projectId;
  SiteModel siteModel;
  MessageHandler client;
  private String discoveryNodeId;
  String mappingEngineId;
  private String selfId;
  private String updateTopic;
  private String traceFile;
  private String registryId;

  private void processArgs(String[] args) {
    ArrayList<String> argList = new ArrayList<>(List.of(args));
    while (argList.size() > 0) {
      String option = removeNextArg(argList);
      try {
        switch (option) {
          case "-p":
            projectId = removeNextArg(argList);
            break;
          case "-s":
            siteModel = new SiteModel(removeNextArg(argList));
            break;
          case "-u":
            updateTopic = removeNextArg(argList);
            break;
          case "-d":
            discoveryNodeId = removeNextArg(argList);
            break;
          case "-e":
            mappingEngineId = removeNextArg(argList);
            break;
          case "-r":
            traceFile = removeNextArg(argList);
            break;
          case "--":
            remainingArgs(argList);
            return;
          default:
            throw new RuntimeException("Unknown cmdline option " + option);
        }
      } catch (Exception e) {
        throw new RuntimeException("While processing option " + option, e);
      }
    }
  }

  void initialize(String flavor, String[] args, List<HandlerSpecification> handlers) {
    selfId = "_mapping_" + flavor;
    processArgs(args);
    checkNotNull(siteModel, "site model not defined");
    siteModel.initialize();
    registryId = checkNotNull(siteModel.getRegistryId(), "site model registry_id null");
    String pubsubSubscription = "mapping-" + flavor;
    String subscription = checkNotNull(pubsubSubscription, "subscription not defined");
    String useUpdateTopic = checkNotNull(
        Optional.ofNullable(updateTopic).orElseGet(siteModel::getUpdateTopic),
        "site model update_topic null");
    client = getMessageClient(subscription, useUpdateTopic);
    handlers.forEach(this::registerHandler);
  }

  private MessageHandler getMessageClient(String subscription,
      String useUpdateTopic) {
    return traceFile == null
        ? new PubSubClient(projectId, registryId, subscription, useUpdateTopic, false)
        : new MessageReadingClient(registryId, traceFile);
  }

  void remainingArgs(List<String> argList) {
    if (!argList.isEmpty()) {
      throw new RuntimeException("Extra args not supported: " + Joiner.on(" ").join(argList));
    }
  }

  @SuppressWarnings("unchecked")
  private <T> void registerHandler(HandlerSpecification handlerSpecification) {
    client.registerHandler((Class<T>) handlerSpecification.getKey(),
        (HandlerConsumer<T>) handlerSpecification.getValue());
  }

  protected void messageLoop() {
    client.messageLoop();
  }

  protected void discoveryPublish(Object message) {
    client.publishMessage(checkNotNull(discoveryNodeId, "discovery node id undefined"), message);
  }

  protected void enginePublish(Object message) {
    client.publishMessage(checkNotNull(mappingEngineId, "mapping engine id undefined"), message);
  }

  protected void publishMessage(String deviceId, Object message) {
    client.publishMessage(deviceId, message);
  }

  protected void publishMessage(Object message) {
    publishMessage(selfId, message);
  }
}
