package com.orevault.orevault.core.gen;

/**
 * The two mineralizable bands of a Vault (§3.1): stone, in both dimension types, and deepslate, in the expanded type
 * only. The order is fixed, stone then deepslate, because the placer draws in it (ADR 0003: a fixed draw order is part of
 * what makes a chunk come out the same whenever it is first visited). Top-level rather than nested in
 * {@link OreBaseline}, since the variant, the placer, the vein extents and later {@code core.vein} all name it.
 */
public enum Band {
    STONE,
    DEEPSLATE
}
