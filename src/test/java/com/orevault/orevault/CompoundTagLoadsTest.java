package com.orevault.orevault;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

/** Decides whether save-shape tests can be unit tests (S17, ADR 0005). */
class CompoundTagLoadsTest {
    @Test
    void roundTripsThroughBytes() throws IOException {
        CompoundTag nested = new CompoundTag();
        nested.putLong("resonance", 1234L);
        CompoundTag tag = new CompoundTag();
        tag.putInt("data_version", 1);
        tag.putString("name", "vault");
        tag.put("nested", nested);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.write(tag, new DataOutputStream(bytes));
        CompoundTag read = NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));

        assertEquals(tag, read);
        assertEquals(1, read.getIntOr("data_version", 0));
        assertEquals(1234L, read.getCompoundOrEmpty("nested").getLongOr("resonance", 0L));
    }
}
