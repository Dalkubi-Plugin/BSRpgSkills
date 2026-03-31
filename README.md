# BSSkill

MMOItems 무기 기반 스킬 바인딩 시스템.  
MythicLib API로 MythicMobs 스킬을 직접 시전하며, 패시브(TIMER) 자동 발동을 지원합니다.

## 요구 사항

| 플러그인 | 버전 | 필수 |
|----------|------|------|
| Paper | 1.21.8+ | O |
| CommandAPI | 11.x | O |
| MMOItems | 6.10+ | O |
| MythicLib | 1.7+ | O |
| MythicMobs | 5.7+ | O |
| PlaceholderAPI | 2.11+ | X |

## 설치

1. `BSSkill.jar`를 `plugins/` 폴더에 배치
2. 서버 시작 — `plugins/BSSkill/weapons/` 폴더에 기본 무기 파일 생성됨
3. 무기 설정 편집 후 `/bsskill reload`

## 키 바인딩

전투 모드 중:

| 입력 | 무기 들고 있을 때 | 아이템 들 때 |
|------|-------------------|-------------|
| F키 | 전투 모드 해제 | 무기(9번)로 복귀 |
| 좌클릭 (허공) | 슬롯 1 스킬 | 일반 |
| 좌클릭 (몬스터) | 슬롯 1 스킬 + 타격 | 일반 타격 |
| 우클릭 | 슬롯 2 스킬 | 일반 (포션 등) |
| 1~4키 | 슬롯 3~6 스킬 | 차단 |
| 5~8키 | 자유 이동 | 자유 이동 |
| 9키 | - | 차단 (F키 복귀) |
| Q키 | 드롭 차단 | 일반 |

전투 모드 진입 시 무기가 자동으로 **핫바 9번**으로 이동됩니다.  
해제 시 원래 슬롯으로 복귀됩니다.

### 좌클릭 동작 상세

- 허공/블록 좌클릭: 슬롯 1 스킬만 시전
- 몬스터 근접 타격: 슬롯 1 스킬 시전 **+ 바닐라 데미지 유지**
- 투사체(화살 등) 피격: **스킬 발동 안 함** (원거리 무한 시전 방지)
- 동일 틱 중복 방지: 50ms 이내 같은 슬롯 재시전 차단

## 무기 설정

`plugins/BSSkill/weapons/{MMOITEMS_ITEM_ID}.yml`

파일명이 MMOItems 아이템 ID와 매칭됩니다.

```yaml
mmo-type: SWORD
display-name: "<gradient:#00ff2a:#e1ff75:#00ff2a>Galebow</gradient>"

skills:
  slot-1:
    mythic-id: BLASTING_COMBO      # MythicMobs 스킬 ID
    display-name: "<yellow>폭발 콤보</yellow>"
    cooldown: 0.3                   # 초
    damage: 5                       # ATTACK_DAMAGE x 이 값 = 최종
    icon: BLAZE_POWDER
    custom-model-data: 0
    description: "설명"
    enabled: true
    extra:                          # <modifier.burndamage>
      burndamage: 1

  slot-2:
    mythic-id: EVASIVE_SHOT
    # ...

passives:
  passive1:
    type: SOUL_LINK
    display-name: "<green>영혼 연결</green>"
    timer: 2                        # 발동 주기 (초)
    cooldown: 0
    icon: TOTEM_OF_UNDYING
    custom-model-data: 0
    description: "주변 아군 회복"
    enabled: true
    heal: 2                         # <modifier.heal>
    healinterval: 1                 # <modifier.healinterval>
```

### 필드 설명

| 필드 | 설명 |
|------|------|
| `mythic-id` / `type` | MythicMobs에 등록된 스킬 이름 |
| `damage` | 무기 공격력(MythicLib ATTACK_DAMAGE)에 곱해지는 배수 |
| `cooldown` | 재사용 대기 시간(초) |
| `timer` | 패시브 자동 발동 주기(초) |
| `icon` | GUI에 표시될 Material 이름 |
| `custom-model-data` | 리소스팩 모델 번호 (0이면 미적용) |
| `extra` / 추가 키 | `registerModifier`로 전달, `<modifier.키>`로 참조 |

### MythicMobs 스킬 연동

BSSkill이 등록한 모디파이어는 MythicMobs YML에서 `<modifier.키>`로 참조합니다.

```yaml
# MythicMobs/Skills/BLASTING_COMBO.yml
BLASTING_COMBO:
  Skills:
  - mmodamage{amount="<modifier.damage>";type=PHYSICAL} @target
  - particles{particle=FLAME;amount=10} @target

# MythicMobs/Skills/SOUL_LINK.yml
SOUL_LINK:
  Skills:
  - heal{amount="<modifier.heal>"} @PIR{r=10}
```

## 명령어

| 명령어 | 권한 | 설명 |
|--------|------|------|
| `/bsskill editor` | bsskill.admin | GUI 에디터 |
| `/bsskill reload` | bsskill.admin | 설정 리로드 |
| `/bsskill list` | bsskill.admin | 무기 목록 |
| `/bsskill info <weapon>` | bsskill.admin | 무기 상세 (탭 완성) |
| `/bsskill toggle` | bsskill.use | 전투 모드 토글 |
| `/bsskill save` | bsskill.admin | 전체 저장 |

## PlaceholderAPI

| 플레이스홀더 | 값 |
|-------------|-----|
| `%bsskill_combat_mode%` | true / false |
| `%bsskill_weapon_name%` | 무기 표시 이름 |
| `%bsskill_weapon_id%` | MMOITEMS_ITEM_ID |
| `%bsskill_slot_<1~6>_name%` | 스킬 이름 |
| `%bsskill_slot_<1~6>_cooldown%` | 남은 쿨타임(초) |
| `%bsskill_slot_<1~6>_damage%` | 데미지 배수 |
| `%bsskill_slot_<1~6>_keybind%` | 키 바인딩 텍스트 |

## GUI 에디터

`/bsskill editor`로 4단계 GUI 에디터를 열 수 있습니다.

1. **무기 목록** — MMOItems 실제 아이템으로 렌더링
2. **무기 상세** — 액티브 6슬롯 + 패시브 목록
3. **액티브 편집** — ON/OFF, ID, 이름, 데미지, 쿨타임, 설명
4. **패시브 편집** — ON/OFF, ID, 이름, 타이머, 쿨타임, 모디파이어 수치

수치는 좌/우/Shift 클릭으로 조정, 텍스트는 채팅 입력으로 변경합니다.  
모든 변경사항은 즉시 YAML에 저장됩니다.

## config.yml

```yaml
use-actionbar: true
actionbar-interval: 5

sounds:
  combat-on: entity.ender_dragon.growl
  combat-off: block.note_block.bass
  skill-cast: ""                    # 빈 문자열 = 출력 안 함
  cooldown-deny: block.note_block.hat

messages:
  combat-on: "<gradient:red:gold>전투 모드 ON</gradient> <yellow>{weapon}</yellow>"
  combat-off: "<gradient:gray:dark_gray>전투 모드 OFF</gradient>"
  skill-cast: ""                    # 빈 문자열 = 출력 안 함
```

메시지와 사운드 모두 빈 문자열(`""`)이면 출력하지 않습니다.

## 기술 구현

### MythicLib API 시전 (콘솔 명령 미사용)

```java
MythicMobsSkillHandler handler = getOrCreateHandler(mythicId);
ModifiableSkill skill = new ModifiableSkill(handler);
skill.registerModifier("damage", 25.0);
skill.registerModifier("heal", 3.0);
skill.cast(new TriggerMetadata(playerData, TriggerType.API, (Entity) null));
```

- `registerModifier(key, double)` — 대소문자 구분, 키 제한 없음
- `TriggerMetadata` 세 번째 인자는 `(Entity) null`로 캐스트 필요
- 핸들러는 서버 시작 시 사전 캐싱 (MythicLib 개발자 권장)
- `cast()` 반환값 `SkillResult.isSuccessful()`로 성공 판정

### 패시브 TIMER

- 전투 모드 진입 시 `BukkitScheduler.runTaskTimer`로 등록
- 무기를 들고 있을 때만 발동 (다른 슬롯이면 스킵)
- 전투 모드 해제 / 사망 / 퇴장 시 모든 타이머 취소

### 무기 NBT 감지

```java
NBTItem nbt = NBTItem.get(item);
String id = nbt.getString("MMOITEMS_ITEM_ID");
```

## 빌드

```bash
./gradlew shadowJar
# build/libs/BSSkill-4.0.0.jar
```

## 라이선스

MIT