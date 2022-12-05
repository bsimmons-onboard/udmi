package com.google.daq.mqtt.util;

import com.google.common.collect.ImmutableList;
import com.google.udmi.util.JsonUtil;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Manage validation exceptions for a device. Specifically, handle pattered exclusions.
 */
public class SiteExceptionManager {

  public static final String EXCEPTIONS_JSON = "exceptions.json";

  private final AllDeviceExceptions allDeviceExceptions;

  /**
   * Create a manager for the given site.
   *
   * @param siteConfig site to use for index of allowed exceptions
   */
  public SiteExceptionManager(File siteConfig) {
    allDeviceExceptions = SiteExceptionManager.loadExceptions(siteConfig);
  }

  static AllDeviceExceptions loadExceptions(File siteConfig) {
    File exceptionsFile = new File(siteConfig, EXCEPTIONS_JSON);
    if (!exceptionsFile.exists()) {
      return null;
    }
    try {
      return JsonUtil.loadFile(AllDeviceExceptions.class, exceptionsFile);
    } catch (Exception e) {
      throw new RuntimeException(
          "While reading exceptions file " + exceptionsFile.getAbsolutePath(), e);
    }
  }

  public boolean purgeException(String message) {
    return false;
  }

  /**
   * Get a list of allowed exception patterns for a given device.
   *
   * @param deviceId device id
   * @return list of exception patterns
   */
  public DevicePatterns forDevice(String deviceId) {
    if (allDeviceExceptions == null) {
      return new DevicePatterns(ImmutableList.of());
    }
    Optional<Entry<String, DeviceExceptions>> first = allDeviceExceptions.entrySet()
        .stream().filter(devices -> deviceId.startsWith(devices.getKey())).findFirst();
    if (!first.isPresent()) {
      return new DevicePatterns(ImmutableList.of());
    }
    Entry<String, DeviceExceptions> deviceExceptions = first.get();
    List<Pattern> patterns = deviceExceptions.getValue().keySet().stream()
        .map(Pattern::compile).collect(Collectors.toList());
    return new DevicePatterns(patterns);
  }

  public static class AllDeviceExceptions extends HashMap<String, DeviceExceptions> {

  }

  public static class DeviceExceptions extends HashMap<String, Object> {
  }

  public static class DevicePatterns {

    public final List<Pattern> patterns;

    public DevicePatterns(List<Pattern> patterns) {
      this.patterns = ImmutableList.copyOf(patterns);
    }

    public boolean shouldPurge(String message) {
      int matches =
          patterns.stream().filter(pattern -> pattern.matcher(message).find())
              .collect(Collectors.toList()).size();
      return matches > 0;
    }
  }
}
