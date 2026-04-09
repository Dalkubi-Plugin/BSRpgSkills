package kr.yongpyo.bsrpgskills.placeholder;

import kr.yongpyo.bsrpgskills.BSRpgSkills;
import kr.yongpyo.bsrpgskills.model.*;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BSRpgSkillsExpansion extends PlaceholderExpansion {
    private final BSRpgSkills plugin;
    public BSRpgSkillsExpansion(BSRpgSkills plugin) { this.plugin = plugin; }

    @Override public @NotNull String getIdentifier() { return "bsrpgskills"; }
    @Override public @NotNull String getAuthor() { return "yongpyo"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";
        var combat = plugin.getCombatManager();
        var state = combat.getExistingState(player);
        if (state == null) {
            return switch (params) {
                case "combat_mode" -> "false";
                case "weapon_name", "weapon_id" -> "";
                case "active_count" -> "0";
                default -> {
                    if (params.startsWith("slot_")) yield "";
                    yield null;
                }
            };
        }

        return switch (params) {
            case "combat_mode" -> String.valueOf(state.isCombatMode());
            case "weapon_name" -> { var w = combat.getCurrentWeapon(player); yield w != null ? w.getDisplayName() : ""; }
            case "weapon_id" -> state.getCurrentWeaponId() != null ? state.getCurrentWeaponId() : "";
            case "active_count" -> { var w = combat.getCurrentWeapon(player); yield w != null ? String.valueOf(w.getActiveSkillCount()) : "0"; }
            default -> {
                if (params.startsWith("slot_")) yield handleSlot(player, state, params);
                yield null;
            }
        };
    }

    private String handleSlot(Player player, CombatState state, String params) {
        String[] parts = params.split("_", 3);
        if (parts.length < 3) return "";
        int slot;
        try { slot = Integer.parseInt(parts[1]); } catch (NumberFormatException e) { return ""; }
        if (slot < 1 || slot > WeaponSkill.MAX_SLOTS) return "";

        WeaponSkill w = plugin.getCombatManager().getCurrentWeapon(player);
        if (w == null) { String id = plugin.getCombatManager().detectWeaponId(player); if (id != null) w = plugin.getWeaponSkillManager().getWeapon(id); }
        if (w == null) return "";
        SkillSlot s = w.getSkill(slot);
        if (s == null) return "";

        return switch (parts[2]) {
            case "name" -> s.getDisplayName();
            case "id" -> s.getMythicId();
            case "cooldown" -> { double r = state.getRemainingCooldown(slot); yield r > 0 ? String.format("%.1f", r) : "0.0"; }
            case "damage" -> String.valueOf(s.getDamage());
            case "enabled" -> String.valueOf(s.isEnabled());
            case "keybind" -> s.getKeybindLabel();
            default -> "";
        };
    }
}
