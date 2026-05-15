package kr.yongpyo.bsrpgskills.command;

import kr.yongpyo.bsrpgskills.BSRpgSkills;
import kr.yongpyo.bsrpgskills.model.CombatState;
import kr.yongpyo.bsrpgskills.model.PassiveSlot;
import kr.yongpyo.bsrpgskills.model.SkillSlot;
import kr.yongpyo.bsrpgskills.model.WeaponSkill;
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
 * Paper/Bukkit 기본 명령 API 기반의 BSRpgSkills 명령 처리기입니다.
 * CommandAPI 없이도 동일한 관리자 기능과 탭 완성을 제공합니다.
 */
public class BSRpgSkillsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "editor", "reload", "list", "info", "toggle", "save", "debug", "debuglog", "validate"
    );

    private final BSRpgSkills plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public BSRpgSkillsCommand(BSRpgSkills plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        PluginCommand command = plugin.getCommand("bsrpgskills");
        if (command == null) {
            plugin.getLogger().severe("plugin.yml에 bsrpgskills 명령이 등록되어 있지 않습니다.");
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
            case "validate" -> handleValidate(sender);
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

        if (args.length == 2 && "info".equalsIgnoreCase(args[0]) && sender.hasPermission("bsrpgskills.admin")) {
            List<String> weaponIds = plugin.getWeaponSkillManager().getAllWeapons().stream()
                    .map(WeaponSkill::getWeaponId)
                    .sorted()
                    .toList();
            return filterByPrefix(weaponIds, args[1]);
        }

        return List.of();
    }

    private boolean handleEditor(CommandSender sender) {
        if (!sender.hasPermission("bsrpgskills.admin")) {
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
        if (!sender.hasPermission("bsrpgskills.admin")) {
            deny(sender);
            return true;
        }

        plugin.reloadConfig();
        plugin.reloadRuntimeSettings();
        plugin.getWeaponSkillManager().loadAll();
        plugin.restartHudTask();
        // 이미 전투 중인 플레이어의 패시브 타이머를 새 PassiveSlot 인스턴스로 재바인딩합니다.
        plugin.getCombatManager().refreshPassiveTimers();
        sender.sendMessage(mm.deserialize("<green>BSRpgSkills 리로드가 완료되었습니다.</green>"));
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!sender.hasPermission("bsrpgskills.admin")) {
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
        if (!sender.hasPermission("bsrpgskills.admin")) {
            deny(sender);
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(mm.deserialize("<red>사용법: /bsrpgskills info <weapon></red>"));
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
        if (!sender.hasPermission("bsrpgskills.use")) {
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
        if (!sender.hasPermission("bsrpgskills.admin")) {
            deny(sender);
            return true;
        }

        plugin.getWeaponSkillManager().saveAll();
        sender.sendMessage(mm.deserialize("<green>모든 무기 설정을 저장했습니다.</green>"));
        return true;
    }

    private boolean handleDebug(CommandSender sender) {
        if (!sender.hasPermission("bsrpgskills.admin")) {
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
        if (!sender.hasPermission("bsrpgskills.admin")) {
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

    /**
     * 등록된 모든 무기 설정의 구조와 값을 검증해 문제 목록을 리포트합니다.
     * YAML 파싱 자체는 loadAll()에서 이미 수행되므로, 여기서는 메모리에 적재된
     * WeaponSkill 인스턴스를 기준으로 의미적 일관성만 검사합니다.
     */
    private boolean handleValidate(CommandSender sender) {
        if (!sender.hasPermission("bsrpgskills.admin")) {
            deny(sender);
            return true;
        }

        var weapons = plugin.getWeaponSkillManager().getAllWeapons();
        if (weapons.isEmpty()) {
            sender.sendMessage(mm.deserialize("<gray>검증할 무기가 없습니다.</gray>"));
            return true;
        }

        int totalIssues = 0;
        int weaponsWithIssues = 0;
        sender.sendMessage(mm.deserialize("<gold>--- BSRpgSkills 설정 검증 ---</gold>"));

        for (WeaponSkill weapon : weapons) {
            List<String> issues = new ArrayList<>();

            if (weapon.getMmoType() == null || weapon.getMmoType().isBlank()) {
                issues.add("mmo-type이 비어 있음");
            }
            if (weapon.getDisplayName() == null || weapon.getDisplayName().isBlank()) {
                issues.add("display-name이 비어 있음");
            }

            for (int i = 1; i <= WeaponSkill.MAX_SLOTS; i++) {
                SkillSlot skill = weapon.getSkill(i);
                if (skill == null) {
                    continue;
                }
                if (skill.isEnabled() && skill.getMythicId().isBlank()) {
                    issues.add("슬롯 " + i + ": enabled=true이지만 mythic-id가 비어 있음");
                }
                if (skill.getCooldown() < 0) {
                    issues.add("슬롯 " + i + ": cooldown 음수 (" + skill.getCooldown() + ")");
                }
                for (var entry : skill.getModifiers().entrySet()) {
                    Double value = entry.getValue();
                    if (value == null || !Double.isFinite(value)) {
                        issues.add("슬롯 " + i + ": modifier '" + entry.getKey() + "' 값이 비정상");
                    }
                }
            }

            for (PassiveSlot passive : weapon.getPassives()) {
                String label = "패시브 #" + passive.getIndex();
                if (passive.getType().isBlank()) {
                    issues.add(label + ": type이 비어 있음");
                }
                if (passive.getTriggerType() == kr.yongpyo.bsrpgskills.model.PassiveTrigger.TIMER
                        && passive.getTimer() < 0.1) {
                    issues.add(label + ": TIMER인데 interval이 0.1초 미만 (" + passive.getTimer() + ")");
                }
                if (passive.getCooldown() < 0) {
                    issues.add(label + ": cooldown 음수 (" + passive.getCooldown() + ")");
                }
                for (var entry : passive.getModifiers().entrySet()) {
                    Double value = entry.getValue();
                    if (value == null || !Double.isFinite(value)) {
                        issues.add(label + ": modifier '" + entry.getKey() + "' 값이 비정상");
                    }
                }
            }

            if (issues.isEmpty()) {
                sender.sendMessage(mm.deserialize("<green>[OK]</green> <white>" + weapon.getWeaponId() + "</white>"));
            } else {
                weaponsWithIssues++;
                totalIssues += issues.size();
                sender.sendMessage(mm.deserialize("<red>[" + issues.size() + " 문제]</red> <white>"
                        + weapon.getWeaponId() + "</white>"));
                for (String issue : issues) {
                    sender.sendMessage(mm.deserialize("  <gray>-</gray> <yellow>" + issue + "</yellow>"));
                }
            }
        }

        sender.sendMessage(mm.deserialize("<gold>--- 검증 완료: "
                + "<white>" + weapons.size() + "</white>개 무기, "
                + "<white>" + weaponsWithIssues + "</white>개에 문제 있음, "
                + "<white>" + totalIssues + "</white>개 문제 발견 ---</gold>"));
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
        sender.sendMessage(mm.deserialize("<gray>/" + label + " validate</gray>"));
    }

    private void sendDebugReport(CommandSender viewer, Player target) {
        CombatState state = plugin.getCombatManager().getExistingState(target);
        WeaponSkill weapon = plugin.getCombatManager().getCurrentWeapon(target);

        viewer.sendMessage(mm.deserialize("<gold>--- BSRpgSkills 디버그 ---</gold>"));
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

        String weaponIdForCooldowns = weapon != null ? weapon.getWeaponId() : state.getCurrentWeaponId();

        for (int i = 1; i <= WeaponSkill.MAX_SLOTS; i++) {
            double remaining = state.getRemainingCooldown(weaponIdForCooldowns, i);
            SkillSlot skill = weapon != null ? weapon.getSkill(i) : null;
            String skillName = skill != null && !skill.getMythicId().isBlank() ? skill.getMythicId() : "비어있음";
            viewer.sendMessage(mm.deserialize("<gray>슬롯 " + i + ":</gray> <white>" + skillName + "</white>"
                    + " <gray>| cooldown:</gray> <white>" + String.format("%.2f", remaining) + "초</white>"));
        }

        if (weapon != null && !weapon.getPassives().isEmpty()) {
            for (PassiveSlot passive : weapon.getPassives()) {
                double remaining = state.getRemainingCooldown(weaponIdForCooldowns, 100 + passive.getIndex());
                viewer.sendMessage(mm.deserialize("<gray>패시브 " + passive.getIndex() + ":</gray> <white>"
                        + passive.getType() + "</white> <gray>| cooldown:</gray> <white>"
                        + String.format("%.2f", remaining) + "초</white>"));
            }
        }

        var snapshot = state.getCooldownSnapshot(weaponIdForCooldowns);
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
                if (sender.hasPermission("bsrpgskills.use")) {
                    available.add(subcommand);
                }
                continue;
            }
            if (sender.hasPermission("bsrpgskills.admin")) {
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
