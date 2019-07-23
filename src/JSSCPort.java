/*
 * Encapsulates JSSC functionality into an easy to use class
 *  See: https://code.google.com/p/java-simple-serial-connector/
 *  And: https://github.com/scream3r/java-simple-serial-connector/releases
 */

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;

import jssc.*;

import javax.swing.*;

public class JSSCPort implements SerialPortEventListener {
  private ArrayBlockingQueue<Integer>  queue = new ArrayBlockingQueue<>(1000);
  private Pattern             osPat;
  private Preferences         prefs;
  private String              portName;
  private int                 baudRate, dataBits = 8, stopBits = 1, parity = 0;
  private int                 eventMasks = SerialPort.MASK_RXCHAR;           // Also, SerialPort.MASK_CTS, SerialPort.MASK_DSR
  private int                 flowCtrl = SerialPort.FLOWCONTROL_NONE;
  private SerialPort          serialPort;
  private final ArrayList<RXEvent>  rxHandlers = new ArrayList<>();

  interface RXEvent {
    void rxChar (byte cc);
  }

  JSSCPort (Preferences prefs) {
    this.prefs = prefs;
    // Determine OS Type
    switch (SerialNativeInterface.getOsType()) {
      case SerialNativeInterface.OS_LINUX:
        osPat = Pattern.compile("(ttyS|ttyUSB|ttyACM|ttyAMA|rfcomm)[0-9]{1,3}");
        break;
      case SerialNativeInterface.OS_MAC_OS_X:
        osPat = Pattern.compile("cu.");
        break;
      case SerialNativeInterface.OS_WINDOWS:
        osPat = Pattern.compile("");
        break;
      default:
        osPat = Pattern.compile("tty.*");
        break;
    }
    portName = prefs.get("serial.port", null);
    baudRate = prefs.getInt("serial.baud", 9600);
    for (String name : SerialPortList.getPortNames(osPat)) {
      if (name.equals(portName)) {
        serialPort = new SerialPort(portName);
        try {
          serialPort.openPort();
          serialPort.setParams(baudRate, dataBits, stopBits, parity, false, false);  // baud, 8 bits, 1 stop bit, no parity
          serialPort.setEventsMask(eventMasks);
          serialPort.setFlowControlMode(flowCtrl);
          serialPort.addEventListener(this);
        } catch (SerialPortException ex) {
          prefs.remove("serial.port");
        }
      }
    }
  }

  void close () {
    if (serialPort != null) {
      try {
        serialPort.closePort();
      } catch (SerialPortException ex) {
        ex.printStackTrace();
      }
    }
  }

  public void serialEvent (SerialPortEvent se) {
    try {
      if (se.getEventType() == SerialPortEvent.RXCHAR) {
        int rxCount = se.getEventValue();
        byte[] inChars = serialPort.readBytes(rxCount);
        if (rxHandlers.size() > 0) {
          for (byte cc : inChars) {
            for (RXEvent handler : rxHandlers) {
              handler.rxChar(cc);
            }
          }
        } else {
          for (byte cc : inChars) {
            if (queue.remainingCapacity() > 0) {
              queue.add((int) cc);
            }
          }
        }
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  void setRXHandler (RXEvent handler) {
    synchronized (rxHandlers) {
      rxHandlers.add(handler);
    }
  }

  JMenu getPortMenu () {
    JMenu menu = new JMenu("Port");
    ButtonGroup group = new ButtonGroup();
    for (String pName : SerialPortList.getPortNames(osPat)) {
      JRadioButtonMenuItem item = new JRadioButtonMenuItem(pName, pName.equals(portName));
      menu.setVisible(true);
      menu.add(item);
      group.add(item);
      item.addActionListener((ev) -> {
        portName = ev.getActionCommand();
        try {
          if (serialPort != null && serialPort.isOpened()) {
            serialPort.removeEventListener();
            serialPort.closePort();
          }
          serialPort = new SerialPort(portName);
          serialPort.openPort();
          serialPort.setParams(baudRate, dataBits, stopBits, parity, false, false);  // baud, 8 bits, 1 stop bit, no parity
          serialPort.setEventsMask(eventMasks);
          serialPort.setFlowControlMode(flowCtrl);
          serialPort.addEventListener(JSSCPort.this);
          prefs.put("serial.port", portName);
        } catch (Exception ex) {
          ex.printStackTrace(System.out);
        }
      });
    }
    return menu;
  }
}
