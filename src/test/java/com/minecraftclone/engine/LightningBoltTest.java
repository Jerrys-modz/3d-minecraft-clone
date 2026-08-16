package com.minecraftclone.engine;

import com.minecraftclone.util.FloatArray;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightningBoltTest {

    @Test
    void boltWritesLineEndpointsAndBurnsOut() {
        LightningBolt bolt = new LightningBolt(0f, 60f, 0f, 1f, 10f, 1f, new Random(1));
        FloatArray out = new FloatArray(16);
        bolt.write(out);
        assertTrue(out.size() >= 6, "a bolt has at least one segment (two endpoints)");
        assertEquals(0, out.size() % 6, "GL_LINES requires vertex pairs (each segment = 2 vertices = 6 floats)");
        assertTrue(bolt.isAlive());
        bolt.update(10f);
        assertFalse(bolt.isAlive(), "a bolt flares out after its short lifetime");
    }
}
