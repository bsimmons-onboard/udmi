package com.google.daq.mqtt.util;

import com.google.udmi.util.JsonUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.regex.Pattern;

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

  public boolean purgeException(String message) {
    return false;
  }

  static AllDeviceExceptions loadExceptions(File siteConfig) {
    File exceptionsFile = new File(siteConfig, EXCEPTIONS_JSON);
    if (!exceptionsFile.exists()) {
      return null;
    }
    try {
      AllDeviceExceptions all = JsonUtil.loadFile(AllDeviceExceptions.class, exceptionsFile);
      all.forEach((prefix, device) ->
          device.forEach((pattern, target) ->
              device.patterns.add(Pattern.compile(pattern))));
      return all;
    } catch (Exception e) {
      throw new RuntimeException(
          "While reading exceptions file " + exceptionsFile.getAbsolutePath(), e);
    }
  }

  /**
   * Get a list of allowed exception patterns for a given device.
   *
   * @param deviceId device id
   * @return list of exception patterns
   */
  public DeviceExceptions forDevice(String deviceId) {
    if (allDeviceExceptions == null) {
      return new DeviceExceptions();
    }
    Optional<Entry<String, DeviceExceptions>> first = allDeviceExceptions.entrySet()
        .stream().filter(devices -> deviceId.startsWith(devices.getKey())).findFirst();
    List<Pattern> patterns = first.map(entry -> entry.getValue().patterns).orElse(null);
    return new DeviceExceptions();
  }

  static class AllDeviceExceptions extends HashMap<String, DeviceExceptions> {

  }

  public static class DeviceExceptions extends HashMap<String, Object> {

    public List<Pattern> patterns = new ArrayList<>();

    public boolean shouldPurge(String message) {
      return false;
    }
  }
}
