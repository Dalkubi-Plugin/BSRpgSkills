package kr.yongpyo.bsrpgskills.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.entity.Player;

/**
 * WorldGuard 커스텀 플래그 연동.
 *
 * 이 클래스는 WorldGuard가 설치된 경우에만 로드/호출되어야 합니다.
 * (호출 측에서 plugin 존재 여부로 가드 → 미설치 시 NoClassDefFound 방지)
 */
public final class WorldGuardHook {

    private static StateFlag combatFlag;

    private WorldGuardHook() {
    }

    /**
     * 반드시 onLoad 단계에서 호출해야 합니다. WorldGuard enable 이후에는 등록이 거부됩니다.
     */
    public static void registerFlag() {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            // 기본값 true = allow. 구역에 deny 설정한 곳에서만 전투 진입 차단됩니다.
            StateFlag flag = new StateFlag("bsrpgskills-combat", true);
            registry.register(flag);
            combatFlag = flag;
        } catch (FlagConflictException conflict) {
            // 이미 다른 곳에서 같은 이름으로 등록된 경우 기존 것을 재사용합니다.
            if (registry.get("bsrpgskills-combat") instanceof StateFlag existing) {
                combatFlag = existing;
            }
        }
    }

    /**
     * 해당 플레이어 위치에서 전투 모드 진입이 허용되는지 반환합니다.
     * 플래그 미등록 또는 우회 권한 보유 시 항상 true.
     */
    public static boolean canEnterCombat(Player player) {
        if (combatFlag == null) {
            return true;
        }

        LocalPlayer local = WorldGuardPlugin.inst().wrapPlayer(player);
        if (WorldGuard.getInstance().getPlatform().getSessionManager().hasBypass(local, local.getWorld())) {
            return true;
        }

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        return query.testState(BukkitAdapter.adapt(player.getLocation()), local, combatFlag);
    }
}
