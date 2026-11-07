package com.orevault.orevault.core.key;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * One title for every Vault in mods that name a dimension by a translation key built from its id. Traveler's Titles,
 * in FTB Evolution 2, looks up {@code travelerstitles.<namespace>.<path>} and draws {@code ???} when it is missing
 * (#414). A Vault's path is {@link VaultKeys#dimensionPath}, different for every team, so no lang file can hold it;
 * the client answers those keys with {@link #TITLE} instead. Only the title is answered: the mod falls back to its
 * own colour when no {@code .color} key exists. No compile-time reference to the other mod (§9).
 */
public final class DimensionTitleKeys {
    /** The one key in our lang file that every Vault's title reads. */
    public static final String TITLE = "travelerstitles.orevault.vault";

    private static final Pattern VAULT_TITLE = Pattern.compile("travelerstitles\\.orevault\\.vault_[0-9a-f]{32}");

    private DimensionTitleKeys() {
    }

    /** {@link #TITLE} for a Vault's per-team title key, otherwise null. */
    public static @Nullable String fallback(String key) {
        return VAULT_TITLE.matcher(key).matches() ? TITLE : null;
    }
}
