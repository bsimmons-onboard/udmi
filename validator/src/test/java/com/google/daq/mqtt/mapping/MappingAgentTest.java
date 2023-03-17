package com.google.daq.mqtt.mapping;

import static com.google.daq.mqtt.TestCommon.DEVICE_ID;
import static com.google.daq.mqtt.TestCommon.SITE_DIR;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.daq.mqtt.TestCommon;
import com.google.daq.mqtt.util.MessageReadingClient;
import com.google.daq.mqtt.util.MessageReadingClient.OutputBundle;
import com.google.daq.mqtt.validator.TestBase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Basic tests for the mapping agent.
 */
public class MappingAgentTest extends TestBase {

  public static final String TRACE_DIR = TestCommon.TOOL_ROOT + "/tests/mapping.trace";

  @Test
  public void simpleTraceReport() {
    List<String> simpleArgs = ImmutableList.of("-s", SITE_DIR, "-d", DEVICE_ID, "-r", TRACE_DIR);
    MappingAgent mappingAgent = new MappingAgent();
    mappingAgent.activate(simpleArgs.toArray(new String[0]));
    List<OutputBundle> outputMessages =
        ((MessageReadingClient) mappingAgent.client).getOutputMessages();
    Map<String, List<OutputBundle>> topics = new HashMap<>();
    outputMessages.forEach(
        bundle -> topics.computeIfAbsent(bundle.topic, thing -> new ArrayList<>()).add(bundle));
    assertEquals("output topics", 2, topics.size());
    assertEquals("discovery configs", 1, topics.get("config/discovery").size());
    assertEquals("mapping configs", 196, topics.get("config/mapping").size());
  }
}