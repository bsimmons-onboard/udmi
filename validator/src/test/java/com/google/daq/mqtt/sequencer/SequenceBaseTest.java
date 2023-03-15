package com.google.daq.mqtt.sequencer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.daq.mqtt.TestCommon;
import com.google.daq.mqtt.validator.Validator.MessageBundle;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Supplier;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.Mnemonic;
import org.checkerframework.checker.units.qual.C;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;

/**
 * Unit tests for the SequenceBaseTest class.
 */
public class SequenceBaseTest {

  private static final String TEST_TOPIC = "mock/topic";
  private Integer constant1;

  /**
   * Reset the state of the underlying infrastructure for each test.
   */
  @Before
  public void resetForTest() {
    SequenceBase.resetState();
    SequenceRunner.executionConfiguration = TestCommon.testConfiguration();
    SequenceRunner.executionConfiguration.device_id = TestCommon.DEVICE_ID;
  }

  @Test
  public void messageInterrupted() {
    final SequenceBase base1 = new SequenceBase();
    base1.testWatcher.starting(makeTestDescription("test_one"));

    MessageBundle bundle1 = base1.nextMessageBundle();
    Map<?, ?> features1 = (Map<?, ?>) bundle1.message.get("features");
    assertEquals("first message contents", 0, features1.size());

    final SequenceBase base2 = new SequenceBase();
    base2.testWatcher.starting(makeTestDescription("test_two"));

    try {
      base1.nextMessageBundle();
      fail("shouldn't be a next message bundle to get!");
    } catch (RuntimeException e) {
      // This is expected, but then also preserve the message for the next call.
    }

    MessageBundle bundle2 = base2.nextMessageBundle();
    Map<?, ?> features2 = (Map<?, ?>) bundle2.message.get("features");
    assertEquals("second message contents", 1, features2.size());
  }

  private Description makeTestDescription(String testName) {
    return Description.createTestDescription(SequenceBase.class, testName);
  }

  @Test
  public void bytecode_check() {
    Integer constant2 = 4;
    indirect(() -> constant1 + constant2);
    indirect(() -> Boolean.TRUE);
  }

  private void indirect(Supplier<Object> supplier) {
    Supplier newSupplier = new Supplier<Object>() {
      @Override
      public Object get() {
        return supplier.get();
      }
    };
    decompile(newSupplier);
  }

  private void decompile(Supplier<Object> supplier) {
    try {
      ClassPool pool = new ClassPool();
      Class<?> targetClass = supplier.getClass();
      pool.appendClassPath(new LoaderClassPath(targetClass.getClassLoader()));
      Method[] methods1 = targetClass.getMethods();
      Method method = methods1[0];
      CtClass ctClass = pool.get(method.getDeclaringClass().getName());
      CtMethod ctMethod = ctClass.getMethod("get", "()Ljava/lang/Object;");
      System.out.println(ctClass.getName() + " " + ctMethod.getName());
      CodeIterator iterator = ctMethod.getMethodInfo().getCodeAttribute().iterator();
      while (iterator.hasNext()) {
        int index = iterator.next();
        int opCode = iterator.byteAt(index);
        System.out.println(index + " " + opCode + " " + Mnemonic.OPCODE[opCode]);
        switch (opCode) {
          case 180:  // getfield
          {
            int fieldIndex = iterator.u16bitAt(index + 1);
            String fieldrefClassName = ctMethod.getMethodInfo().getConstPool()
                .getFieldrefClassName(fieldIndex);
            String fieldrefName = ctMethod.getMethodInfo().getConstPool()
                .getFieldrefName(fieldIndex);
            String fieldrefType = ctMethod.getMethodInfo().getConstPool()
                .getFieldrefType(fieldIndex);
            int getFieldrefClass = ctMethod.getMethodInfo().getConstPool()
                .getFieldrefClass(fieldIndex);
            System.out.println("  field class " + fieldrefClassName);
            break;
          }
          case 185:  // invokeinterface
          {
            int fieldIndex = iterator.u16bitAt(index + 1);
            int count = iterator.byteAt(index + 3);
            int zero = iterator.byteAt(index + 4);
            String name = ctMethod.getMethodInfo().getConstPool()
                .getInterfaceMethodrefName(fieldIndex);
            System.out.println("  method name " + name);
          }
          default:
            // Nothing, just go to next.
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("While handling method", e);
    }
  }

}
