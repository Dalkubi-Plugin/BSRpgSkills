package kr.yongpyo.bsskill.command;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.StringArgument;
import kr.yongpyo.bsskill.BSSkill;
import kr.yongpyo.bsskill.model.*;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class BSSkillCommand {

    private final BSSkill plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public BSSkillCommand(BSSkill plugin) { this.plugin = plugin; }

    public void registerAll() {
        new CommandAPICommand("bsskill").withPermission("bsskill.admin")
                .withSubcommand(new CommandAPICommand("editor").withPermission("bsskill.admin")
                        .executesPlayer((p, a) -> { plugin.getGUIManager().openWeaponListGUI(p); }))

                .withSubcommand(new CommandAPICommand("reload").withPermission("bsskill.admin")
                        .executes((s, a) -> {
                            plugin.reloadConfig();
                            plugin.getWeaponSkillManager().loadAll();
                            plugin.restartHudTask();
                            s.sendMessage(mm.deserialize("<green>BSSkill 리로드 완료</green>"));
                        }))

                .withSubcommand(new CommandAPICommand("list").withPermission("bsskill.admin")
                        .executes((s, a) -> {
                            var weapons = plugin.getWeaponSkillManager().getAllWeapons();
                            if (weapons.isEmpty()) { s.sendMessage(mm.deserialize("<gray>무기 없음</gray>")); return; }
                            s.sendMessage(mm.deserialize("<white>무기 목록 (" + weapons.size() + "개)</white>"));
                            for (WeaponSkill w : weapons)
                                s.sendMessage(mm.deserialize("  <gray>- </gray><white>" + w.getWeaponId() + "</white> " + w.getDisplayName()
                                        + " <gray>(" + w.getActiveSkillCount() + "스킬 + " + w.getPassives().size() + "패시브)</gray>"));
                        }))

                .withSubcommand(new CommandAPICommand("info").withPermission("bsskill.admin")
                        .withArguments(new StringArgument("weapon")
                                .replaceSuggestions(ArgumentSuggestions.strings(i ->
                                        plugin.getWeaponSkillManager().getAllWeapons().stream()
                                                .map(WeaponSkill::getWeaponId).toArray(String[]::new))))
                        .executes((s, a) -> {
                            String id = (String) a.get("weapon");
                            WeaponSkill w = plugin.getWeaponSkillManager().getWeapon(id);
                            if (w == null) { s.sendMessage(mm.deserialize("<red>" + id + " 미발견</red>")); return; }
                            s.sendMessage(mm.deserialize("<white>" + w.getDisplayName() + "</white> <gray>(" + w.getMmoType() + ")</gray>"));
                            for (int i = 1; i <= WeaponSkill.MAX_SLOTS; i++) {
                                SkillSlot sk = w.getSkill(i);
                                String st = sk.isValid() ? "<green>ON</green>" : "<red>OFF</red>";
                                s.sendMessage(mm.deserialize("  [" + st + "<white>] " + i + " (" + sk.getKeybindLabel() + ") </white>"
                                        + sk.getDisplayName() + " <gray>| " + sk.getMythicId() + " | DMG:" + sk.getDamage() + " | CD:" + sk.getCooldown() + "s</gray>"));
                            }
                            for (PassiveSlot p : w.getPassives()) {
                                String st = p.isEnabled() ? "<green>ON</green>" : "<red>OFF</red>";
                                s.sendMessage(mm.deserialize("  [" + st + "<white>] 패시브 </white>" + p.getDisplayName()
                                        + " <gray>| " + p.getType() + " | T:" + p.getTimer() + "s</gray>"
                                        + (!p.getModifiers().isEmpty() ? " <gray>| mods:" + p.getModifiers() + "</gray>" : "")));
                            }
                        }))

                .withSubcommand(new CommandAPICommand("toggle").withPermission("bsskill.use")
                        .executesPlayer((p, a) -> { plugin.getCombatManager().handleFKey(p); }))

                .withSubcommand(new CommandAPICommand("save").withPermission("bsskill.admin")
                        .executes((s, a) -> { plugin.getWeaponSkillManager().saveAll(); s.sendMessage(mm.deserialize("<green>저장 완료</green>")); }))

                .register();
    }
}
