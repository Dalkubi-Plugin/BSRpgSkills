package kr.yongpyo.bsskill.command;

import kr.yongpyo.bsskill.BSSkill;
import kr.yongpyo.bsskill.model.CombatState;
import kr.yongpyo.bsskill.model.PassiveSlot;
import kr.yongpyo.bsskill.model.SkillSlot;
import kr.yongpyo.bsskill.model.WeaponSkill;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Paper/Bukkit 기본 명령 API 기반의 BSSkill 명령 처리기입니다.
 * CommandAPI 없이도 동일한 관리자 기능과 탭 완성을 제공합니다.
 */
public class BSSkillCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "editor", "reload", "list", "info", "toggle", "save", "debug", "debuglog"
    );

    private final BSSkill plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public BSSkillCommand(BSSkill plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        PluginCommand command = plugin.getCommand("bsskill");
        if (command == null) {
            plugin.getLogger().severe("plugin.yml에 bsskill 명령이 등록되어 있지 않습니다.");
            return;
        }

        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "editor" -> handleEditor(sender);
            case "reload" -> handleReload(sender);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "toggle" -> handleToggle(sender);
            case "save" -> handleSave(sender);
            case "debug" -> handleDebug(sender);
            case "debuglog" -> handleDebugLog(sender);
            default -> {
                sendUsage(sender, label);
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterByPrefix(availableSubcommands(sender), args[0]);
        }

        if (args.length == 2 && "info".equalsIgnoreCase(args[0]) && sender.hasPermission("bsskill.admin")) {
            List<String> weaponIds = plugin.getWeaponSkillManager().getAllWeapons().stream()
                    .map(WeaponSkill::getWeaponId)
                    .sorted()
                    .toList();
            return filterByPrefix(weaponIds, args[1]);
        }

        return List.of();
    }

    private boolean handleEditor(CommandSender sender) {
        if (!sender.hasPermission("bsskill.admin")) {
            deny(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<red>플레이어만 사용할 수 있는 명령어입니다.</red>"));
            return true;
        }

        plugin.getGUIManager().openWeaponListGUI(player);
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("bsskill.admin")) {
            deny(sender);
            return true;
        }

        plugin.reloadConfig();
        plugin.reloadRuntimeSettings();
        plugin.getWeaponSkillManager().loadAll();
        plugin.restartHudTask();
        sender.sendMessage(mm.deserialize("<green>BSSkill 리로드가 완료되었습니다.</green>"));
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!sender.hasPermission("bsskill.admin")) {
            deny(sender);
            return true;
        }

        var weapons = plugin.getWeaponSkillManager().getAllWeapons().stream()
                .sorted(Comparator.comparing(WeaponSkill::getWeaponId))
                .toList();

        if (weapons.isEmpty()) {
            sender.sendMessage(mm.deserialize("<gray>등록된 무기가 없습니다.</gray>"));
            return true;
        }

        sender.sendMessage(mm.deserialize("<white>무기 목록 <gray>(" + weapons.size() + "개)</gray></white>"));
        for (WeaponSkill weapon : weapons) {
            sender.sendMessage(mm.deserialize(
                    " <gray>-</gray> <white>" + weapon.getWeaponId() + "</white> "
                            + weapon.getDisplayName()
                            + " <gray>(" + weapon.getActiveSkillCount() + "개 액티브, "
                            + weapon.getPassives().size() + "개 패시브)</gray>"));
        }
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bsskill.admin")) {
            deny(sender);
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(mm.deserialize("<red>사용법: /bsskill info <weapon></red>"));
            return true;
        }

        String id = args[1];
        WeaponSkill weapon = plugin.getWeaponSkillManager().getWeapon(id);
        if (weapon == null) {
            sender.sendMessage(mm.deserialize("<red>" + id + " 무기를 찾지 못했습니다.</red>"));
            return true;
        }

        sender.sendMessage(mm.deserialize("<white>" + weapon.getDisplayName() + "</white> <gray>(" + weapon.getMmoType() + ")</gray>"));
        for (int i = 1; i <= WeaponSkill.MAX_SLOTS; i++) {
            SkillSlot skill = weapon.getSkill(i);
            String state = skill.isValid() ? "<green>ON</green>" : "<red>OFF</red>";
            sender.sendMessage(mm.deserialize(
                    " [" + state + "<white>] 슬롯 " + i + " (" + skill.getKeybindLabel() + ") </white>"
                            + skill.getDisplayName()
                            + " <gray>| " + skill.getMythicId()
                            + " | damage:" + skill.getDamage()
                            + " | ratio:" + skill.getRatio()
                            + " | cooldown:" + skill.getCooldown() + "초</gray>"
                            + (!skill.getModifiers().isEmpty()
                            ? " <gray>| modifiers:" + skill.getModifiers() + "</gray>"
                            : "")));
        }

        for (PassiveSlot passive : weapon.getPassives()) {
            String state = passive.isEnabled() ? "<green>ON</green>" : "<red>OFF</red>";
            sender.sendMessage(mm.deserialize(
                    " [" + state + "<white>] 패시브 </white>" + passive.getDisplayName()
                            + " <gray>| " + passive.getType()
                            + " | interval:" + passive.getTimer() + "초"
                            + " | cooldown:" + passive.getCooldown() + "초</gray>"
                            + (!passive.getModifiers().isEmpty()
                            ? " <gray>| modifiers:" + passive.getModifiers() + "</gray>"
                            : "")));
        }
        return true;
    }

    private boolean handleToggle(CommandSender sender) {
        if (!sender.hasPermission("bsskill.use")) {
            deny(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<red>플레이어만 사용할 수 있는 명령어입니다.</red>"));
            return true;
        }

        plugin.getCombatManager().handleFKey(player);
        return true;
    }

    private boolean handleSave(CommandSender sender) {
        if (!sender.hasPermission("bsskill.admin")) {
            deny(sender);
            return true;
        }

        plugin.getWeaponSkillManager().saveAll();
        sender.sendMessage(mm.deserialize("<green>모든 무기 설정을 저장했습니다.</green>"));
        return true;
    }

    private boolean handleDebug(CommandSender sender) {
        if (!sender.hasPermission("bsskill.admin")) {
            deny(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<red>플레이어만 사용할 수 있는 명령어입니다.</red>"));
            return true;
        }

        sendDebugReport(player, player);
        return true;
    }

    private boolean handleDebugLog(CommandSender sender) {
        if (!sender.hasPermission("bsskill.admin")) {
            deny(sender);
            return true;
        }

        boolean next = !plugin.getConfig().getBoolean("debug.enabled", false);
        plugin.getConfig().set("debug.enabled", next);
        plugin.saveConfig();
        plugin.reloadRuntimeSettings();
        sender.sendMessage(mm.deserialize("<yellow>디버그 로그를 "
                + (next ? "<green>활성화</green>" : "<red>비활성화</red>")
                + "<yellow>했습니다.</yellow>"));
        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(mm.deserialize("<gold>사용 가능한 명령어</gold>"));
        sender.sendMessage(mm.deserialize("<gray>/" + label + " editor</gray>"));
        sender.sendMessage(mm.deserialize("<gray>/" + label + " reload</gray>"));
        sender.sendMessage(mm.deserialize("<gray>/" + label + " list</gray>"));
        sender.sendMessage(mm.deserialize("<gray>/" + label + " info <weapon></gray>"));
        sender.sendMessage(mm.deserialize("<gray>/" + label + " toggle</gray>"));
        sender.sendMessage(mm.deserialize("<gray>/" + label + " save</gray>"));
        sender.sendMessage(mm.deserialize("<gray>/" + label + " debug</gray>"));
        sender.sendMessage(mm.deserialize("<gray>/" + label + " debuglog</gray>"));
    }

    private void sendDebugReport(CommandSender viewer, Player target) {
        CombatState state = plugin.getCombatManager().getExistingState(target);
        WeaponSkill weapon = plugin.getCombatManager().getCurrentWeapon(target);

        viewer.sendMessage(mm.deserialize("<gold>--- BSSkill 디버그 ---</gold>"));
        viewer.sendMessage(Component.text("대상: ").append(target.displayName()));
        viewer.sendMessage(mm.deserialize("<gray>디버그 로그:</gray> <white>"
                + (plugin.isDebugMode() ? "활성화" : "비활성화") + "</white>"));

        if (state == null) {
            viewer.sendMessage(mm.deserialize("<gray>전투 상태가 아직 생성되지 않았습니다.</gray>"));
            return;
        }

        viewer.sendMessage(mm.deserialize("<gray>전투 모드:</gray> <white>"
                + (state.isCombatMode() ? "ON" : "OFF") + "</white>"));
        viewer.sendMessage(mm.deserialize("<gray>현재 무기:</gray> <white>"
                + safe(state.getCurrentWeaponId()) + "</white>"));
        viewer.sendMessage(mm.deserialize("<gray>고정 슬롯:</gray> <white>" + state.getWeaponSlot()
                + "</white> <gray>| 원래 슬롯:</gray> <white>" + state.getOriginalSlot() + "</white>"));
        viewer.sendMessage(mm.deserialize("<gray>스왑 여부:</gray> <white>"
                + (state.isSwapped() ? "예" : "아니오") + "</white>"
                + " <gray>| 내부 캐스팅:</gray> <white>"
                + (state.isInternalCasting() ? "예" : "아니오") + "</white>"));

        if (weapon == null && state.getCurrentWeaponId() != null) {
            weapon = plugin.getWeaponSkillManager().getWeapon(state.getCurrentWeaponId());
        }

        if (weapon != null) {
            viewer.sendMessage(mm.deserialize("<gray>무기 설정:</gray> " + weapon.getDisplayName()
                    + " <gray>(" + weapon.getWeaponId() + ")</gray>"));
        }

        for (int i = 1; i <= WeaponSkill.MAX_SLOTS; i++) {
            double remaining = state.getRemainingCooldown(i);
            SkillSlot skill = weapon != null ? weapon.getSkill(i) : null;
            String skillName = skill != null && !skill.getMythicId().isBlank() ? skill.getMythicId() : "비어있음";
            viewer.sendMessage(mm.deserialize("<gray>슬롯 " + i + ":</gray> <white>" + skillName + "</white>"
                    + " <gray>| cooldown:</gray> <white>" + String.format("%.2f", remaining) + "초</white>"));
        }

        if (weapon != null && !weapon.getPassives().isEmpty()) {
            for (PassiveSlot passive : weapon.getPassives()) {
                double remaining = state.getRemainingCooldown(100 + passive.getIndex());
                viewer.sendMessage(mm.deserialize("<gray>패시브 " + passive.getIndex() + ":</gray> <white>"
                        + passive.getType() + "</white> <gray>| cooldown:</gray> <white>"
                        + String.format("%.2f", remaining) + "초</white>"));
            }
        }

        var snapshot = state.getCooldownSnapshot();
        if (snapshot.isEmpty()) {
            viewer.sendMessage(mm.deserialize("<gray>현재 활성화된 쿨타임이 없습니다.</gray>"));
            return;
        }

        viewer.sendMessage(mm.deserialize("<gray>활성 쿨타임 키:</gray> <white>" + snapshot + "</white>"));
    }

    private void deny(CommandSender sender) {
        sender.sendMessage(mm.deserialize("<red>권한이 없습니다.</red>"));
    }

    private List<String> availableSubcommands(CommandSender sender) {
        List<String> available = new ArrayList<>();
        for (String subcommand : SUBCOMMANDS) {
            if ("toggle".equals(subcommand)) {
                if (sender.hasPermission("bsskill.use")) {
                    available.add(subcommand);
                }
                continue;
            }
            if (sender.hasPermission("bsskill.admin")) {
                available.add(subcommand);
            }
        }
        return available;
    }

    private List<String> filterByPrefix(List<String> values, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "없음" : value;
    }
}
