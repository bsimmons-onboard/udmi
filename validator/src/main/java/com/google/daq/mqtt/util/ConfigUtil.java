package com.google.daq.mqtt.util;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import java.io.File;
import udmi.schema.ExecutionConfiguration;

/**
 * Collection of utilities for managing configuration.
 */
public abstract class ConfigUtil {

  public static final String UDMI_VERSION = "1.4.0";
  public static final String UDMI_TOOLS = System.getenv("UDMI_TOOLS");

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .enable(Feature.ALLOW_COMMENTS)
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .setDateFormat(new ISO8601DateFormat());

  /**
   * Read cloud configuration from a file.
   *
   * @param configFile file ot parse
   * @return cloud configuration information
   */
  public static ExecutionConfiguration readExecutionConfiguration(File configFile) {
    try {
      return OBJECT_MAPPER.readValue(configFile, ExecutionConfiguration.class);
    } catch (Exception e) {
      throw new RuntimeException("While reading config file " + configFile.getAbsolutePath(), e);
    }
  }

  /**
   * Read a validator configuration file.
   *
   * @param configFile file to read
   * @return parsed validator config
   */
  public static ExecutionConfiguration readValidatorConfig(File configFile) {
    try {
      return OBJECT_MAPPER.readValue(configFile, ExecutionConfiguration.class);
    } catch (Exception e) {
      throw new RuntimeException("While reading config file " + configFile.getAbsolutePath(), e);
    }
  }

}
