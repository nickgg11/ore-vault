package com.orevault.orevault.client;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.core.key.DimensionTitleKeys;
import java.util.List;
import java.util.Map;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.resources.VanillaClientListeners;
import org.jspecify.annotations.Nullable;

/**
 * Wraps the client {@link Language} so a Vault's per-team title key reads {@link DimensionTitleKeys#TITLE} (#414).
 * Every other key goes to the language underneath unchanged. {@code LanguageManager} injects a fresh language on each
 * resource reload, so the wrap is redone by a reload listener ordered after vanilla's language listener.
 */
final class VaultTitleLanguage extends Language {
    private static final Identifier LISTENER = Identifier.fromNamespaceAndPath(OreVault.MOD_ID, "vault_title_language");

    private final Language delegate;

    private VaultTitleLanguage(Language delegate) {
        this.delegate = delegate;
    }

    static void register(AddClientReloadListenersEvent event) {
        event.addListener(LISTENER, (ResourceManagerReloadListener) VaultTitleLanguage::wrap);
        event.addDependency(VanillaClientListeners.LANGUAGE, LISTENER);
    }

    private static void wrap(ResourceManager resourceManager) {
        Language current = Language.getInstance();
        if (!(current instanceof VaultTitleLanguage)) {
            Language.inject(new VaultTitleLanguage(current));
        }
    }

    private String resolve(String key) {
        String fallback = DimensionTitleKeys.fallback(key);
        return fallback != null && !delegate.has(key) ? fallback : key;
    }

    @Override
    public String getOrDefault(String elementId, String defaultValue) {
        return delegate.getOrDefault(resolve(elementId), defaultValue);
    }

    @Override
    public boolean has(String elementId) {
        return delegate.has(resolve(elementId));
    }

    @Override
    public boolean isDefaultRightToLeft() {
        return delegate.isDefaultRightToLeft();
    }

    @Override
    public FormattedCharSequence getVisualOrder(FormattedText logicalOrderText) {
        return delegate.getVisualOrder(logicalOrderText);
    }

    @Override
    public List<FormattedCharSequence> getVisualOrder(List<FormattedText> lines) {
        return delegate.getVisualOrder(lines);
    }

    @Override
    public Map<String, String> getLanguageData() {
        return delegate.getLanguageData();
    }

    @Override
    public @Nullable Component getComponent(String key) {
        return delegate.getComponent(resolve(key));
    }
}
