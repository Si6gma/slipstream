package com.si6gma.slipstream.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.si6gma.slipstream.paper.PayloadCodec.DraftValues;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * Pins the wire layout the Fabric client decodes. A width mismatch here corrupts every field after
 * it with no error at either end, so the byte count and the read-back order are both asserted.
 */
class PayloadFormatTest {

  private static final DraftValues DRAFT =
      new DraftValues(true, 0.008, 1.15, 0.25, 35.0, 1.5, 1.2, 60, 2, 0.15, 3);

  @Test
  void payloadIsExactlyOneHundredSeventeenBytes() throws IOException {
    byte[] out = PayloadCodec.serialize(20.0, 0.005, 1.5, 5.0, 0.6, 0.3, DRAFT);
    // 6 leading doubles (48) + boolean (1) + 6 doubles (48) + 2 ints (8) + double (8) + int (4).
    assertEquals(117, out.length);
  }

  @Test
  void fieldsReadBackInTheOrderTheClientExpects() throws IOException {
    byte[] out = PayloadCodec.serialize(20.0, 0.005, 1.5, 5.0, 0.6, 0.3, DRAFT);
    DataInputStream in = new DataInputStream(new ByteArrayInputStream(out));

    assertEquals(20.0, in.readDouble(), 1e-9);
    assertEquals(0.005, in.readDouble(), 1e-9);
    assertEquals(1.5, in.readDouble(), 1e-9);
    assertEquals(5.0, in.readDouble(), 1e-9);
    assertEquals(0.6, in.readDouble(), 1e-9);
    assertEquals(0.3, in.readDouble(), 1e-9);

    assertEquals(true, in.readBoolean());
    assertEquals(0.008, in.readDouble(), 1e-9);
    assertEquals(1.15, in.readDouble(), 1e-9);
    assertEquals(0.25, in.readDouble(), 1e-9);
    assertEquals(35.0, in.readDouble(), 1e-9);
    assertEquals(1.5, in.readDouble(), 1e-9);
    assertEquals(1.2, in.readDouble(), 1e-9);
    assertEquals(60, in.readInt());
    assertEquals(2, in.readInt());
    assertEquals(0.15, in.readDouble(), 1e-9);
    assertEquals(3, in.readInt());

    assertEquals(0, in.available(), "nothing may trail the last field");
  }

  @Test
  void leadingSixKeepTheirPositionsSoOlderClientsStillWork() throws IOException {
    // An older client stops reading after the sixth double. Those six must be unaffected by
    // anything appended after them.
    byte[] withDraft = PayloadCodec.serialize(20.0, 0.005, 1.5, 5.0, 0.6, 0.3, DRAFT);
    DataInputStream in = new DataInputStream(new ByteArrayInputStream(withDraft));
    double[] leading = new double[6];
    for (int i = 0; i < 6; i++) {
      leading[i] = in.readDouble();
    }

    assertEquals(20.0, leading[0], 1e-9);
    assertEquals(0.005, leading[1], 1e-9);
    assertEquals(1.5, leading[2], 1e-9);
    assertEquals(5.0, leading[3], 1e-9);
    assertEquals(0.6, leading[4], 1e-9);
    assertEquals(0.3, leading[5], 1e-9);
  }
}
