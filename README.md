# BSRpgSkills

Paper 1.21.8 기반 무기 스킬 플러그인.
MMOItems 무기에 6슬롯 액티브 + 다중 패시브 + 스킬 레벨 시스템을 결합.
MythicLib API로 MythicMobs 스킬 발동.

## 핵심 기능

- 전투 모드 토글 (F 키 — 인벤토리 스왑 핸드 키)
- 6 슬롯 액티브: 좌클릭, 우클릭, 핫바키 1~4
- 다중 패시브: TIMER / ON_DAMAGE_TAKEN / ON_DAMAGE_DEALT 트리거
- 스킬 레벨 시스템: 슬롯별 독립 레벨 + 공용 포인트, 레벨업으로 damage/cooldown 스케일
- 무기-스코프 쿨타임: 전투 모드 토글/사망에도 유지 (로그아웃 시만 소멸)
- 마지막 사용 무기 컨텍스트 보존: 플레이스홀더가 전투 모드 OFF 상태에서도 정확
- GUI: 무기/스킬/패시브 편집 + 인게임 스킬 강화
- 스킬 초기화 아이템: 우클릭으로 모든 레벨 초기화 + 포인트 전액 환불 (custom-model-data / tooltip-style 지정 가능)
- 중앙화된 채팅 prefix: `{p_success}` / `{p_error}` 토큰 치환 (MiniMessage)
- WorldGuard 구역 플래그: 지정 구역에서 전투 모드 진입 차단
- ActionBar HUD: 슬롯별 쿨다운 표시
- PlaceholderAPI 통합

## 요구사항

- Java 21
- Paper 1.21.8
- MythicMobs, MythicLib, MMOItems
- (선택) PlaceholderAPI, MMOCore, WorldGuard

## 명령어

전투 모드 토글은 F 키만 사용 (스왑 핸드 키). 별도 `toggle` 명령어 없음.
스킬 레벨업은 `/bsrpgskills skills` GUI에서 처리. 별도 `levelup` 명령어 없음.
스킬 포인트는 **무기/슬롯 구분 없는 공용 풀**. 레벨은 무기-슬롯별 독립.

| 명령어 | 권한 | 콘솔 | 설명 |
|---|---|---|---|
| `/bsrpgskills editor` | `bsrpgskills.admin` | ✗ | 무기/스킬 편집 GUI |
| `/bsrpgskills reload` | `bsrpgskills.admin` | ✓ | config/weapons 리로드, 패시브 타이머 재바인딩, 레벨 클램프 |
| `/bsrpgskills list` | `bsrpgskills.admin` | ✓ | 등록 무기 목록 |
| `/bsrpgskills info <weapon>` | `bsrpgskills.admin` | ✓ | 무기 상세 |
| `/bsrpgskills save` | `bsrpgskills.admin` | ✓ | 모든 무기 YAML 저장 |
| `/bsrpgskills debug` | `bsrpgskills.admin` | ✗ | 본인 전투 상태 디버그 |
| `/bsrpgskills debuglog` | `bsrpgskills.admin` | ✓ | 디버그 로그 토글 |
| `/bsrpgskills validate` | `bsrpgskills.admin` | ✓ | YAML 의미 검증 리포트 |
| `/bsrpgskills point give <player> <amount>` | `bsrpgskills.admin` | ✓ | 공용 포인트 지급 |
| `/bsrpgskills point take <player> <amount>` | `bsrpgskills.admin` | ✓ | 공용 포인트 차감 (보유량까지만) |
| `/bsrpgskills point check <player> [weapon]` | `bsrpgskills.admin` | ✓ | 포인트/레벨 확인 (무기 필터 가능) |
| `/bsrpgskills point reset <player> [weapon] [slot]` | `bsrpgskills.admin` | ✓ | 레벨 초기화 (스코프 지정 가능) |
| `/bsrpgskills setlevel <player> <weapon> <slot> <level>` | `bsrpgskills.admin` | ✓ | 강제 레벨 설정 (max-level 클램프) |
| `/bsrpgskills cooldown reset <player> [slot]` | `bsrpgskills.admin` | ✓ | 쿨타임 초기화 (슬롯 생략 시 전체, 온라인만) |
| `/bsrpgskills resetitem give <player> [amount]` | `bsrpgskills.admin` | ✓ | 스킬 초기화 아이템 지급 |
| `/bsrpgskills skills` | `bsrpgskills.use` | ✗ | 인게임 스킬 강화 GUI (포인트 사용 레벨업) |

## 권한

- `bsrpgskills.admin` — 모든 관리 명령
- `bsrpgskills.use` — 전투 모드, 본인 레벨업, 스킬 GUI

## 설정 파일

- `plugins/BSRpgSkills/config.yml` — 메시지, prefix, 사운드, HUD, 초기화 아이템 옵션
- `plugins/BSRpgSkills/weapons/<MMOITEMS_ITEM_ID>.yml` — 무기별 스킬/패시브 설정
- `plugins/BSRpgSkills/playerdata/<uuid>.yml` — 플레이어별 레벨 / 포인트

### `config.yml` 예시

```yml
use-actionbar: false
actionbar-interval: 5

debug:
  enabled: false

sounds:
  combat-on: ""
  combat-off: ""
  skill-cast: ""
  cooldown-deny: ""
  return-weapon: ""

# 스킬 초기화 아이템
reset-item:
  material: NETHER_STAR
  custom-model-data: 0          # 0이면 미적용
  tooltip-style: ""             # 예) "minecraft:default" 또는 리소스팩 키. 비우면 미적용
  consume: true                 # 사용 시 1개 소모 여부
  name: "<gradient:red:gold>스킬 초기화 주문서</gradient>"
  lore:
    - "<gray>우클릭하면 모든 스킬 레벨이 초기화되고</gray>"
    - "<gray>사용한 스킬 포인트가 전부 환불됩니다.</gray>"

# 채팅 prefix. 한 번만 정의하고 메시지에서 {p_success}/{p_error} 토큰으로 참조.
prefix:
  success: "<shift:-2><!shadow><shift:-3><glyph:chat_prefix><#4caf50><glyph:chat_background:c><shift:-223><glyph:error_green> "
  error: "<shift:-2><!shadow><shift:-3><glyph:chat_prefix><#db0000><glyph:chat_background:c><shift:-223><glyph:error_red> "

messages:
  combat-on: "{p_success}<#4caf50>전투 모드 ON</#4caf50> <#a5d6a7>{weapon}</#a5d6a7>"
  combat-off: "{p_success}<#4caf50>전투 모드 OFF</#4caf50>"
  no-weapon: "{p_error}<#db0000>등록된 전투 무기를 손에 들고 있어야 합니다.</#db0000>"
  weapon-lost: "{p_error}<#db0000>전투 무기를 잃어 전투 모드가 해제되었습니다.</#db0000>"
  cooldown: ""                  # 매 시전 발동 → 비워서 도배 방지
  skill-cast: ""
  no-skill: "{p_error}<#db0000>해당 슬롯에는 스킬이 없습니다.</#db0000>"
  cast-failed: ""
  return-weapon: "{p_success}<#4caf50>전투 무기 슬롯으로 복귀했습니다.</#4caf50>"
  region-blocked: "{p_error}<#db0000>이 구역에서는 전투 모드를 사용할 수 없습니다.</#db0000>"
```

## 채팅 Prefix 토큰

- `prefix.success` / `prefix.error`를 한 번만 정의 → 메시지에 `{p_success}` / `{p_error}` 토큰만 적으면 전송 시 자동 치환.
- 모든 메시지는 MiniMessage 파싱 (`<gradient>`, `<#hex>`, `<glyph>` 등).
- 텍스트 색 권장: 성공 `#4caf50` / 강조 `#a5d6a7`, 오류 `#db0000` / 강조 `#ff8a80`.
- glyph(`chat_prefix`, `chat_background`, `error_green/red`)는 리소스팩 폰트 필요. 미사용 시 prefix 문자열만 비우면 됨.
- 매 시전 발동 메시지(`cooldown`, `skill-cast`, `cast-failed`)는 비워두는 게 기본 (채팅 도배 방지).

## 스킬 초기화 아이템

- `/bsrpgskills resetitem give <player> [amount]`로 지급. PDC 태그로 식별 (이름 변경/스택 무관).
- 우클릭(허공/블록) 시: 해당 플레이어의 **모든 무기-슬롯 레벨 초기화 + 사용 포인트 전액 환불** (`resetAll`).
- `reset-item.consume: true`면 사용 시 1개 소모.
- `custom-model-data` (>0일 때) / `tooltip-style` (NamespacedKey 형식) / `name` / `lore` 모두 config에서 지정.

## WorldGuard 구역 플래그

- 플래그명: `bsrpgskills-combat`, 기본값 **allow**.
- `onLoad`에서 등록 (WorldGuard enable 이후엔 등록 거부됨). softdepend로 로드 순서 보장.
- 차단 설정: `/region flag <구역> bsrpgskills-combat deny`
- deny 구역 → 전투 모드 진입 차단 + `region-blocked` 메시지. 이미 전투 중 플레이어가 deny 구역 진입 → 자동 해제.
- bypass 권한 보유자는 플래그 무시 (WG 세션 bypass).
- WorldGuard 미설치 시 기능 자동 비활성 (관련 클래스 미로드 → `NoClassDefFound` 없음).

## 무기 YAML 구조

### 필드 트리

- `mmo-type`: MMOItems 타입 (SWORD, BOW 등)
- `display-name`: MiniMessage 문자열
- `skills.slot-N`:
  - `mythic-id`: MythicMobs 스킬 키
  - `enabled`: bool
  - `max-level`: int (default 1, >1이면 레벨업 가능)
  - `timing.cooldown`: base cooldown (초)
  - `display.{name, description, icon, custom-model-data}`
  - `modifiers.{damage, ratio, ...}`: MythicMobs `<modifier.key>` 변수
  - `levels.level-N.{cooldown, damage, display.lore}`: 레벨별 오버라이드 (cooldown/damage만 적용됨)
- `passives.passive-N`:
  - `type`: MythicMobs 스킬 키
  - `trigger`: TIMER | ON_DAMAGE_TAKEN | ON_DAMAGE_DEALT (default TIMER)
  - `enabled`: bool
  - `timing.interval`: TIMER 발동 주기 (초)
  - `timing.cooldown`: 발동 후 재사용 대기 (초)
  - `display.{name, description, icon, custom-model-data}`
  - `modifiers.{...}`

### 액티브 + 레벨 예시

```yml
mmo-type: SWORD
display-name: "<gradient:#f153ff:#4e00ca:#f153ff>드래곤랜스</gradient>"

skills:
  slot-2:
    mythic-id: DRAGONFANG_THRUST
    enabled: true
    max-level: 5
    timing:
      cooldown: 1.0
    display:
      name: "<gradient:#f153ff:#4e00ca:#f153ff>용아 찌르기</gradient>"
      description: "용의 송곳니로 적을 꿰뚫고 점화시킵니다."
      icon: NETHERITE_SWORD
      custom-model-data: 0
    modifiers:
      ratio: 1.0
      damage: 2.0
      burnduration: 3.0
      burndamage: 1.0
    levels:
      level-2:
        cooldown: 0.92
        damage: 2.4
        display:
          lore: ["<gray>강화 I (데미지 +20%, 쿨다운 -8%)</gray>"]
      level-3:
        cooldown: 0.85
        damage: 2.8
        display:
          lore: ["<gray>강화 II (데미지 +40%, 쿨다운 -15%)</gray>"]
      level-4:
        cooldown: 0.78
        damage: 3.4
        display:
          lore: ["<gray>강화 III (데미지 +70%, 쿨다운 -22%)</gray>"]
      level-5:
        cooldown: 0.72
        damage: 4.0
        display:
          lore: ["<gold>최대 강화 (데미지 +100%, 쿨다운 -28%)</gold>"]
```

### 패시브 + 트리거 예시

```yml
passives:
  passive-1:
    type: DRAGONFLAME
    trigger: TIMER
    enabled: true
    timing:
      interval: 10.0
      cooldown: 0.0
    display:
      name: "<gradient:#f153ff:#4e00ca:#f153ff>용염 발산</gradient>"
      description: "주기적으로 용의 불꽃을 방출해 주변 적을 점화시킵니다."
      icon: BLAZE_POWDER
      custom-model-data: 0
    modifiers: {}

  passive-2:
    type: COUNTER_FLAME
    trigger: ON_DAMAGE_TAKEN
    enabled: true
    timing:
      cooldown: 3.0
    display:
      name: "<red>반격의 불꽃</red>"
      description: "피격 시 주변 적에게 화염 피해."
      icon: BLAZE_ROD
      custom-model-data: 0
    modifiers:
      damage: 5.0
```

## Modifiers 규칙

- 액티브 기본: `damage`, `ratio` (ratio 누락 시 1.0 자동 보정)
- 레벨 스케일링 대상: `damage`, `cooldown`만. `burnduration`, `duration`, `radius` 등 기타 modifier는 base 값 고정
- MythicMobs 측: `<modifier.key>`로 참조 (예: `<modifier.burnduration>`)
- GUI 커스텀 modifier 입력 포맷: `key:value`

## 입력 매핑

| 슬롯 | 입력 |
|---|---|
| slot-1 | 좌클릭 |
| slot-2 | 우클릭 |
| slot-3 | 핫바키 1 |
| slot-4 | 핫바키 2 |
| slot-5 | 핫바키 3 |
| slot-6 | 핫바키 4 |

전투 모드 진입 시 무기는 인벤토리 9번 슬롯(인덱스 8)으로 강제 스왑됨. 해제 시 원래 슬롯으로 복귀.

## 패시브 트리거

| 트리거 | 발동 시점 | 사용 필드 |
|---|---|---|
| `TIMER` | `timing.interval`초마다 자동 | interval, cooldown |
| `ON_DAMAGE_TAKEN` | 플레이어 피격 시 | cooldown |
| `ON_DAMAGE_DEALT` | 플레이어가 무기 평타로 가격 시 | cooldown |

이벤트 트리거(`ON_DAMAGE_*`)는 다음 조건 동시 만족 시에만 발동:
- 전투 모드 ON
- 무기 메인핸드 보유
- 내부 캐스팅 락 아님 (재귀 방지)

## 쿨타임 동작

- 무기별 분리 저장: `cooldownsByWeapon: Map<weaponId, Map<slot, expireAt>>`
- 전투 모드 OFF / 사망 / 무기 교체로 초기화되지 않음
- 플레이어 로그아웃 시에만 완전 소멸
- 관리자 강제 초기화: `/bsrpgskills cooldown reset <player> [slot]` (온라인 대상)
- `/bsrpgskills reload` 직후 모든 캐시된 플레이어의 stale 패시브 타이머가 새 `PassiveSlot` 인스턴스로 재바인딩됨

## 스킬 레벨 시스템

- 레벨: 무기-슬롯별 독립. 키 포맷 `weaponId:slot`
- 포인트: **공용 풀** (무기/슬롯 구분 없음). 모든 슬롯 레벨업에 공통 사용
- 저장 위치: `plugins/BSRpgSkills/playerdata/<uuid>.yml`
- 사용 흐름:
  1. 관리자가 `/bsrpgskills point give <player> <amount>`로 공용 포인트 지급 (콘솔 가능)
  2. 플레이어가 `/bsrpgskills skills` GUI에서 포인트로 레벨업 (전투 모드 진입 후 사용)
  3. 캐스팅 시 `getDamageForLevel(level)` / `getCooldownForLevel(level)` 적용
- max-level 초과 레벨은 reload 시 자동 클램프 + 포인트 환불
- 초기화 아이템 우클릭 → 전체 레벨 초기화 + 포인트 전액 환불

## PlaceholderAPI

식별자: `bsrpgskills`

### 전역

- `%bsrpgskills_combat_mode%` — `true` / `false`
- `%bsrpgskills_weapon_name%` — 현재/마지막 사용 무기 displayName
- `%bsrpgskills_weapon_id%` — MMOItems ID
- `%bsrpgskills_active_count%` — 활성 슬롯 수

### 슬롯별 (N = 1~6)

- `%bsrpgskills_slot_N_name%`
- `%bsrpgskills_slot_N_id%`
- `%bsrpgskills_slot_N_cooldown%` — 남은 쿨다운 (초, 소수1)
- `%bsrpgskills_slot_N_base_cooldown%` — YAML 기본 쿨다운
- `%bsrpgskills_slot_N_damage%`
- `%bsrpgskills_slot_N_enabled%`
- `%bsrpgskills_slot_N_keybind%` — "좌클릭" / "우클릭" / "1번"~"4번"
- `%bsrpgskills_slot_N_level%` — 현재 레벨
- `%bsrpgskills_slot_N_maxlevel%` — 최대 레벨
- `%bsrpgskills_slot_N_points%` — 보유 공용 포인트

## 디버그

- `/bsrpgskills debuglog` — 디버그 로그 ON/OFF
- `/bsrpgskills debug` — 본인 전투 상태 (전투 모드, 무기 ID, 슬롯 정보, 쿨다운, 내부 캐스팅 플래그)
- `/bsrpgskills validate` — 모든 무기 YAML 의미 검증 리포트 (빈 type, 음수 cooldown, NaN modifier 등)

## 빌드

```bash
./gradlew shadowJar
```

산출물: `build/libs/BSRpgSkills-<version>.jar`
