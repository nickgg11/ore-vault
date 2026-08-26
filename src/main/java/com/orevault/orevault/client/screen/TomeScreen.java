package com.orevault.orevault.client.screen;

import com.orevault.orevault.data.PlayerStats;
import com.orevault.orevault.network.ModNetwork;
import com.orevault.orevault.skill.NodeDef;
import com.orevault.orevault.skill.NodeDefs;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tome of the Deep Seam GUI (design spec section 8): three tabs — Resonance tree, Animus
 * tree, Ore Memory — plus the Vault Reset button. All state is server-synced.
 */
public class TomeScreen extends Screen {
    private static final int PANEL_W = 430;
    private static final int PANEL_H = 246;

    private CompoundTag data = new CompoundTag();
    private int tab; // 0 resonance, 1 animus, 2 memory
    private String selectedNode;
    private String viewedMember;
    private boolean backupOnReset;

    // node graph layout cache
    private final Map<String, int[]> nodePos = new HashMap<>();
    private int graphX, graphY, graphW, graphH;

    public TomeScreen(CompoundTag data) {
        super(Component.translatable("orevault.screen.tome"));
        this.data = data;
    }

    public void updateData(CompoundTag data) {
        this.data = data;
    }

    public int panelX() {
        return (width - PANEL_W) / 2;
    }

    public int panelY() {
        return (height - PANEL_H) / 2;
    }

    @Override
    protected void init() {
        nodePos.clear();
        clearWidgets();
    }

    @Override
    public void tick() {
        // Rebuild action buttons each tick is overkill; rebuild when selection changes.
    }

    private void rebuildActionButtons() {
        clearWidgets();
        if (tab <= 1 && selectedNode != null) {
            NodeDef def = NodeDefs.get(selectedNode).orElse(null);
            if (def != null) {
                int x = panelX() + PANEL_W - 150;
                int y = panelY() + 90;
                CompoundTag nodes = data.getCompoundOrEmpty("nodes");
                int tier = nodes.getIntOr(selectedNode, 0);
                boolean canBuy = canUnlockClient(def, tier);
                if (!def.tradeoff()) {
                    addRenderableWidget(Button.builder(Component.translatable("orevault.ui.purchase"), b -> purchase(def))
                            .bounds(x, y, 132, 20).build());
                    if (tier > 0) {
                        addRenderableWidget(Button.builder(Component.translatable("orevault.ui.refund"), b -> refund(def))
                                .bounds(x, y + 26, 132, 20).build());
                    }
                } else if (tier > 0) {
                    boolean active = data.getCompoundOrEmpty("tradeoffs").getBooleanOr(def.id(), false);
                    addRenderableWidget(Button.builder(active
                                            ? Component.translatable("orevault.ui.toggle_off")
                                            : Component.translatable("orevault.ui.toggle_on"),
                                    b -> toggleTradeoff(def))
                            .bounds(x, y, 132, 20).build());
                }
            }
        } else if (tab == 2) {
            if (data.getBooleanOr("resetEligible", false)) {
                addRenderableWidget(Button.builder(Component.translatable("orevault.ui.reset_vault"), b -> requestReset())
                        .bounds(panelX() + PANEL_W - 150, panelY() + PANEL_H - 26, 132, 20).build());
            }
            // Member selector buttons.
            CompoundTag members = data.getCompoundOrEmpty("members");
            List<String> memberIds = new ArrayList<>(members.keySet());
            int y = panelY() + 60;
            for (String id : memberIds) {
                String label = shortName(UUID.fromString(id));
                Button button = Button.builder(Component.literal(label), b -> viewedMember = id)
                        .bounds(panelX() + PANEL_W - 150, y, 132, 16).build();
                addRenderableWidget(button);
                y += 18;
                if (y > panelY() + PANEL_H - 40) {
                    break;
                }
            }
            addRenderableWidget(Button.builder(backupOnReset
                                    ? Component.translatable("orevault.ui.backup_yes")
                                    : Component.translatable("orevault.ui.backup_no"),
                            b -> backupOnReset = !backupOnReset)
                    .bounds(panelX() + PANEL_W - 150, panelY() + PANEL_H - 52, 132, 20).build());
        }
    }

    private String shortName(UUID id) {
        // Best effort: fall back to trimmed UUID; the server could send names.
        return id.toString().substring(0, 8);
    }

    private void purchase(NodeDef def) {
        ClientPacketDistributor.sendToServer(new ModNetwork.SpendPointPayload(def.id()));
    }

    private void refund(NodeDef def) {
        ClientPacketDistributor.sendToServer(new ModNetwork.RefundPayload(def.id()));
    }

    private void toggleTradeoff(NodeDef def) {
        boolean current = data.getCompoundOrEmpty("tradeoffs").getBooleanOr(def.id(), false);
        ClientPacketDistributor.sendToServer(new ModNetwork.ToggleTradeoffPayload(def.id(), !current));
    }

    private void requestReset() {
        ClientPacketDistributor.sendToServer(new ModNetwork.ResetRequestPayload(backupOnReset));
    }

    private boolean canUnlockClient(NodeDef def, int tier) {
        String tree = def.treeId();
        CompoundTag treeTag = data.getCompoundOrEmpty(NodeDefs.RESONANCE.equals(tree) ? "resonance" : "animus");
        int points = treeTag.getIntOr("points", 0);
        int level = treeTag.getIntOr("level", 0);
        int nextTier = tier + 1;
        if (!def.hasTier(nextTier)) {
            return false;
        }
        if (points < def.costs()[nextTier - 1] || level < def.levelReqs()[nextTier - 1]) {
            return false;
        }
        CompoundTag nodes = data.getCompoundOrEmpty("nodes");
        for (String prereq : def.prereqs()[nextTier - 1]) {
            String[] parts = prereq.split(":", 2);
            int need = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            if (nodes.getIntOr(parts[0], 0) < need) {
                return false;
            }
        }
        for (String exclusive : def.exclusives()) {
            if (nodes.getIntOr(exclusive, 0) > 0) {
                return false;
            }
        }
        return true;
    }

    // --- rendering ---------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int x0 = panelX();
        int y0 = panelY();
        // Panel
        g.fill(x0, y0, x0 + PANEL_W, y0 + PANEL_H, 0xE8101014);
        g.outline(x0, y0, x0 + PANEL_W, y0 + PANEL_H, 0xFF886633);
        // Header
        g.centeredText(font, Component.translatable("orevault.screen.tome"), x0 + PANEL_W / 2, y0 + 6, 0xFFFFD700);
        String teamName = data.getString("teamName");
        g.text(font, Component.literal(teamName), x0 + 6, y0 + 6, 0xFFAAAAAA);
        // Tabs
        renderTab(g, x0 + 8, y0 + 22, 0, "orevault.tab.resonance");
        renderTab(g, x0 + 128, y0 + 22, 1, "orevault.tab.animus");
        renderTab(g, x0 + 248, y0 + 22, 2, "orevault.tab.memory");
        g.horizontalLine(x0, x0 + PANEL_W, y0 + 40, 0xFF886633);

        if (tab == 2) {
            renderMemoryTab(g, x0, y0, mouseX, mouseY);
        } else {
            renderTreeTab(g, x0, y0, mouseX, mouseY, NodeDefs.RESONANCE.equals(tabTree()) ? NodeDefs.RESONANCE : NodeDefs.ANIMUS);
        }
    }

    private String tabTree() {
        return tab == 0 ? NodeDefs.RESONANCE : NodeDefs.ANIMUS;
    }

    private void renderTab(GuiGraphicsExtractor g, int x, int y, int index, String key) {
        int w = 112;
        int color = tab == index ? 0xFFFFD700 : 0xFF888888;
        g.fill(x, y, x + w, y + 14, tab == index ? 0x44332211 : 0x22332211);
        g.centeredText(font, Component.translatable(key), x + w / 2, y + 3, color);
    }

    private void renderTreeTab(GuiGraphicsExtractor g, int x0, int y0, int mouseX, int mouseY, String tree) {
        // Top bar: level + points + progress.
        CompoundTag treeTag = data.getCompoundOrEmpty(tree);
        int level = treeTag.getIntOr("level", 0);
        int points = treeTag.getIntOr("points", 0);
        long pool = treeTag.getLongOr("pool", 0L);
        long next = treeTag.getLongOr("next", 0L);
        g.text(font, Component.translatable("orevault.ui.level", level), x0 + 8, y0 + 46, 0xFFFFFFFF);
        g.text(font, Component.translatable("orevault.ui.points", points), x0 + 100, y0 + 46, 0xFF66FF66);
        if (next > 0) {
            long prev = next - treeTag.getLongOr("cost", 0L);
            float progress = (float) (pool - prev) / (float) (next - prev);
            progress = Math.max(0, Math.min(1, progress));
            int barW = 180;
            g.fill(x0 + 190, y0 + 48, x0 + 190 + barW, y0 + 56, 0xFF222222);
            g.fill(x0 + 190, y0 + 48, x0 + 190 + (int) (barW * progress), y0 + 56, 0xFF33AAFF);
            g.text(font, Component.literal(pool + " / " + next), x0 + 190 + barW + 4, y0 + 48, 0xFFAAAAAA);
        }

        graphX = x0 + 8;
        graphY = y0 + 64;
        graphW = PANEL_W - 170;
        graphH = PANEL_H - 90;

        List<NodeDef> nodes = visibleNodes(tree);
        Map<String, int[]> positions = layoutNodes(nodes);
        nodePos.clear();
        nodePos.putAll(positions);

        CompoundTag unlocked = data.getCompoundOrEmpty("nodes");
        CompoundTag tradeoffs = data.getCompoundOrEmpty("tradeoffs");

        // Lines to prerequisites.
        for (NodeDef def : nodes) {
            int[] pos = positions.get(def.id());
            if (pos == null) {
                continue;
            }
            int tier = unlocked.getIntOr(def.id(), 0);
            for (String prereq : def.prereqs()[Math.max(0, Math.min(tier, def.prereqs().length - 1))]) {
                String prereqId = prereq.split(":", 2)[0];
                int[] pre = positions.get(prereqId);
                if (pre != null) {
                    int col = unlocked.getIntOr(def.id(), 0) > 0 || tier > 0 ? 0xFF886633 : 0xFF443322;
                    g.verticalLine(pre[0], pre[1], pos[1], col);
                    g.horizontalLine(pre[0], pos[0], pre[1], col);
                }
            }
        }

        // Nodes.
        for (NodeDef def : nodes) {
            int[] pos = positions.get(def.id());
            if (pos == null) {
                continue;
            }
            int tier = unlocked.getIntOr(def.id(), 0);
            boolean tradeoff = def.tradeoff();
            boolean tradeoffActive = tradeoffs.getBooleanOr(def.id(), false);
            boolean available = canUnlockClient(def, tier);
            boolean selected = def.id().equals(selectedNode);
            int color = tier > 0 ? (tradeoff ? (tradeoffActive ? 0xFFFF8833 : 0xFFCC8844) : 0xFF66BB66) : 0xFF555555;
            if (available && tier == 0) {
                color = 0xFFDDDD55;
            }
            if (selected) {
                g.outline(pos[0] - 11, pos[1] - 11, pos[0] + 11, pos[1] + 11, 0xFFFFFFFF);
            }
            g.fill(pos[0] - 8, pos[1] - 8, pos[0] + 8, pos[1] + 8, color | 0xFF000000);
            g.outline(pos[0] - 8, pos[1] - 8, pos[0] + 8, pos[1] + 8, 0xFF000000);
            // tier pips
            for (int i = 0; i < def.visibleTierCount(); i++) {
                int pip = i < tier ? 0xFFFFFFFF : 0xFF444444;
                g.fill(pos[0] - 8 + i * 4, pos[1] + 10, pos[0] - 6 + i * 4, pos[1] + 12, pip | 0xFF000000);
            }
            g.centeredText(font, Component.translatable(def.displayNameKey()), pos[0], pos[1] + 16, tier > 0 ? 0xFFFFFFFF : 0xFF888888);
        }

        // Info panel.
        renderInfoPanel(g, x0, y0, tree);

        // Tooltip for hovered node.
        if (mouseX >= graphX && mouseX <= graphX + graphW && mouseY >= graphY && mouseY <= graphY + graphH) {
            for (NodeDef def : nodes) {
                int[] pos = positions.get(def.id());
                if (pos != null && mouseX >= pos[0] - 8 && mouseX <= pos[0] + 8 && mouseY >= pos[1] - 8 && mouseY <= pos[1] + 12) {
                    renderNodeTooltip(g, def, mouseX, mouseY);
                    break;
                }
            }
        }
    }

    private List<NodeDef> visibleNodes(String tree) {
        List<NodeDef> out = new ArrayList<>();
        for (NodeDef def : NodeDefs.forTree(tree)) {
            if (!def.isHidden()) {
                out.add(def);
            }
        }
        return out;
    }

    private Map<String, int[]> layoutNodes(List<NodeDef> nodes) {
        Map<String, List<NodeDef>> byBranch = new LinkedHashMap<>();
        for (NodeDef def : nodes) {
            byBranch.computeIfAbsent(def.branch(), b -> new ArrayList<>()).add(def);
        }
        Map<String, int[]> positions = new HashMap<>();
        int branchCount = Math.max(1, byBranch.size());
        int colW = graphW / branchCount;
        int branchIndex = 0;
        for (List<NodeDef> branch : byBranch.values()) {
            int cx = graphX + colW * branchIndex + colW / 2;
            int y = graphY + 18;
            for (NodeDef def : branch) {
                positions.put(def.id(), new int[]{cx, y});
                y += 26;
                if (y > graphY + graphH - 10) {
                    break;
                }
            }
            branchIndex++;
        }
        return positions;
    }

    private void renderInfoPanel(GuiGraphicsExtractor g, int x0, int y0, String tree) {
        int x = x0 + PANEL_W - 148;
        int y = y0 + 46;
        g.fill(x, y, x + 140, y0 + PANEL_H - 6, 0x33101010);
        if (selectedNode == null) {
            g.textWithWordWrap(font, Component.translatable("orevault.ui.select_node"), x + 4, y + 4, 136, 0xFFAAAAAA);
            return;
        }
        NodeDef def = NodeDefs.get(selectedNode).orElse(null);
        if (def == null) {
            return;
        }
        CompoundTag nodes = data.getCompoundOrEmpty("nodes");
        int tier = nodes.getIntOr(selectedNode, 0);
        int yCursor = y + 4;
        g.text(font, Component.translatable(def.displayNameKey()), x + 4, yCursor, 0xFFFFD700);
        yCursor += 12;
        g.text(font, Component.translatable("orevault.ui.tier", tier, def.visibleTierCount()), x + 4, yCursor, 0xFFAAAAAA);
        yCursor += 12;
        int nextTier = tier + 1;
        if (def.hasTier(nextTier)) {
            g.text(font, Component.translatable("orevault.ui.cost", def.costs()[nextTier - 1]), x + 4, yCursor, 0xFF66FF66);
            yCursor += 11;
            g.text(font, Component.translatable("orevault.ui.level_req", def.levelReqs()[nextTier - 1]), x + 4, yCursor, 0xFF66FF66);
            yCursor += 11;
        }
        g.textWithWordWrap(font, Component.translatable(def.descriptionKey()), x + 4, yCursor, 136, 0xFFCCCCCC);
        rebuildActionButtonsIfNeeded();
    }

    private String lastActionKey = "";

    private void rebuildActionButtonsIfNeeded() {
        String key = tab + "|" + selectedNode;
        if (!key.equals(lastActionKey)) {
            lastActionKey = key;
            rebuildActionButtons();
        }
    }

    private void renderNodeTooltip(GuiGraphicsExtractor g, NodeDef def, int mouseX, int mouseY) {
        List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.translatable(def.displayNameKey()).getVisualOrderText());
        lines.add(Component.translatable(def.descriptionKey()).getVisualOrderText());
        CompoundTag nodes = data.getCompoundOrEmpty("nodes");
        int tier = nodes.getIntOr(def.id(), 0);
        int nextTier = tier + 1;
        if (def.hasTier(nextTier)) {
            lines.add(Component.translatable("orevault.ui.cost", def.costs()[nextTier - 1]).getVisualOrderText());
            lines.add(Component.translatable("orevault.ui.level_req", def.levelReqs()[nextTier - 1]).getVisualOrderText());
            if (!canUnlockClient(def, tier)) {
                lines.add(Component.translatable("orevault.ui.locked_hint").getVisualOrderText());
            }
        }
        g.setTooltipForNextFrame(lines, mouseX, mouseY);
    }

    private void renderMemoryTab(GuiGraphicsExtractor g, int x0, int y0, int mouseX, int mouseY) {
        CompoundTag members = data.getCompoundOrEmpty("members");
        if (viewedMember == null || !members.contains(viewedMember)) {
            viewedMember = members.keySet().stream().findFirst().orElse(null);
        }

        int x = x0 + 8;
        int y = y0 + 46;
        // Team aggregate header.
        g.text(font, Component.translatable("orevault.ui.team_stats"), x, y, 0xFFFFD700);
        y += 14;
        long totalOres = 0;
        long totalKills = 0;
        long totalRes = 0;
        long totalAnimus = 0;
        Map<String, Long> oresAgg = new HashMap<>();
        Map<String, Long> killsAgg = new HashMap<>();
        for (String id : members.keySet()) {
            PlayerStats stats = PlayerStats.readNbt(members.getCompoundOrEmpty(id).getCompoundOrEmpty("stats"));
            totalOres += stats.totalBlocksBroken();
            totalKills += stats.mobsKilled().values().stream().mapToLong(Long::longValue).sum();
            totalRes += stats.resonanceLifetime();
            totalAnimus += stats.animusLifetime();
            stats.oresMined().forEach((k, v) -> oresAgg.merge(k, v, Long::sum));
            stats.mobsKilled().forEach((k, v) -> killsAgg.merge(k, v, Long::sum));
        }
        g.text(font, Component.translatable("orevault.ui.team_ores", totalOres), x, y, 0xFFAAAAAA);
        y += 11;
        g.text(font, Component.translatable("orevault.ui.team_kills", totalKills), x, y, 0xFFAAAAAA);
        y += 11;
        g.text(font, Component.translatable("orevault.ui.team_res", totalRes), x, y, 0xFFAAAAAA);
        y += 11;
        g.text(font, Component.translatable("orevault.ui.team_animus", totalAnimus), x, y, 0xFFAAAAAA);
        y += 11;
        g.text(font, Component.translatable("orevault.ui.chunks", data.getLongOr("chunksGenerated", 0L)), x, y, 0xFFAAAAAA);
        y += 16;

        if (viewedMember != null) {
            PlayerStats stats = PlayerStats.readNbt(members.getCompoundOrEmpty(viewedMember).getCompoundOrEmpty("stats"));
            g.text(font, Component.translatable("orevault.ui.player_stats"), x, y, 0xFFFFD700);
            y += 14;
            g.text(font, Component.translatable("orevault.ui.blocks", stats.totalBlocksBroken()), x, y, 0xFFAAAAAA);
            y += 11;
            g.text(font, Component.translatable("orevault.ui.deepest", stats.deepestY()), x, y, 0xFFAAAAAA);
            y += 11;
            g.text(font, Component.translatable("orevault.ui.time", stats.timeInVaultTicks() / 20 / 60), x, y, 0xFFAAAAAA);
            y += 11;
            g.text(font, Component.translatable("orevault.ui.res_session", stats.resonanceSession()), x, y, 0xFFAAAAAA);
            y += 11;
            g.text(font, Component.translatable("orevault.ui.animus_session", stats.animusSession()), x, y, 0xFFAAAAAA);
            y += 11;
            g.text(font, Component.translatable("orevault.ui.echoes", stats.vaultEchoTriggers()), x, y, 0xFFAAAAAA);
            y += 11;
            g.text(font, Component.translatable("orevault.ui.twins", stats.twinVeinTriggers()), x, y, 0xFFAAAAAA);
            y += 11;
            g.text(font, Component.translatable("orevault.ui.volatile", stats.volatileVeinsTriggers()), x, y, 0xFFAAAAAA);
        }

        // Vote status (if active).
        CompoundTag vote = data.getCompoundOrEmpty("voteState");
        if (vote.getBooleanOr("active", false)) {
            int yv = y0 + PANEL_H - 30;
            g.text(font, Component.translatable("orevault.ui.vote_active"), x, yv, 0xFFFF5533);
        }
    }

    // --- input -------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x0 = panelX();
        int y0 = panelY();
        // Tabs
        if (mouseY >= y0 + 22 && mouseY <= y0 + 36) {
            for (int i = 0; i < 3; i++) {
                if (mouseX >= x0 + 8 + i * 120 && mouseX <= x0 + 8 + i * 120 + 112) {
                    tab = i;
                    selectedNode = null;
                    lastActionKey = "";
                    rebuildActionButtons();
                    return true;
                }
            }
        }
        // Node clicks
        if (tab <= 1 && mouseX >= graphX && mouseX <= graphX + graphW && mouseY >= graphY && mouseY <= graphY + graphH) {
            for (var entry : nodePos.entrySet()) {
                int[] pos = entry.getValue();
                if (mouseX >= pos[0] - 8 && mouseX <= pos[0] + 8 && mouseY >= pos[1] - 8 && mouseY <= pos[1] + 14) {
                    selectedNode = entry.getKey();
                    lastActionKey = "";
                    rebuildActionButtons();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

