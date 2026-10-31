package com.orevault.orevault;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Proves the unit test source set runs, on the toolchain JDK and not on whatever launched Gradle. */
class ToolchainSmokeTest {
    @Test
    void runsOnJava25() {
        assertEquals(25, Runtime.version().feature());
    }
}
