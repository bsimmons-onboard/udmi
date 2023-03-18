package com.google.daq.mqtt.mapping;

import static org.junit.Assert.assertEquals;

import java.util.AbstractMap.SimpleEntry;
import org.junit.Test;
import udmi.schema.BuildingTranslation;
import udmi.schema.PointEnumerationEvent;

public class MappingEngineTest {

  public static final String POINT_UNIQ = "sup_flow_actual_avo_1";
  public static final String POINT_NAME = "supplicant_flow";
  public static final String POINT_REF = "analog-value_29";

  @Test
  public void discoveryRefTranslation() {
    PointEnumerationEvent point = new PointEnumerationEvent();
    SimpleEntry<String, PointEnumerationEvent> entry = new SimpleEntry<>(POINT_UNIQ, point);
    point.ref = POINT_REF;

    MappingEngine mappingEngine = new MappingEngine();
    SimpleEntry<String, BuildingTranslation> translation = mappingEngine.makeTranslation(entry);

    assertEquals("point name", POINT_NAME, translation.getKey());
    BuildingTranslation value = translation.getValue();
    assertEquals("point ref", POINT_REF, value.ref);
  }
}