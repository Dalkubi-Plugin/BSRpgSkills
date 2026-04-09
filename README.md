# BSRpgSkills

Paper 1.21.8 기반 무기 스킬 플러그인입니다.  
MMOItems 무기를 기준으로 전투 모드를 구성하고, MythicLib API를 통해 MythicMobs 스킬을 발동합니다.

## 핵심 기능

- 전투 모드 토글(F 키/명령어)
- 슬롯 기반 액티브 스킬(최대 6개)
- 타이머 기반 패시브 스킬(여러 개)
- GUI 기반 무기/스킬 편집
- ActionBar 쿨다운 HUD
- PlaceholderAPI 연동

## 요구 사항

- Java 21
- Paper 1.21.8
- MythicMobs
- MythicLib
- MMOItems
- (선택) PlaceholderAPI

## 명령어

| 명령어 | 권한 | 설명 |
|---|---|---|
| `/bsrpgskills editor` | `bsrpgskills.admin` | GUI 편집기 열기 |
| `/bsrpgskills reload` | `bsrpgskills.admin` | 설정/무기 파일 리로드 |
| `/bsrpgskills list` | `bsrpgskills.admin` | 등록된 무기 목록 확인 |
| `/bsrpgskills info <weapon>` | `bsrpgskills.admin` | 무기 상세 정보 확인 |
| `/bsrpgskills toggle` | `bsrpgskills.use` | 전투 모드 수동 토글 |
| `/bsrpgskills save` | `bsrpgskills.admin` | 무기 설정 저장 |
| `/bsrpgskills debug` | `bsrpgskills.admin` | 본인 전투 상태 디버그 출력 |
| `/bsrpgskills debuglog` | `bsrpgskills.admin` | 디버그 로그 ON/OFF |

## 설정 파일

- 메인 설정: `plugins/BSRpgSkills/config.yml`
- 무기 설정 폴더: `plugins/BSRpgSkills/weapons/*.yml`

### `config.yml` 예시

```yml
use-actionbar: true
actionbar-interval: 5

debug:
  enabled: false

sounds:
  combat-on: ""
  combat-off: ""
  skill-cast: ""
  cooldown-deny: ""
  return-weapon: ""

messages:
  combat-on: "<gradient:red:gold>전투 모드 ON</gradient> <yellow>{weapon}</yellow>"
  combat-off: "<gradient:gray:dark_gray>전투 모드 OFF</gradient>"
  no-weapon: "<red>등록된 전투 무기를 손에 들고 있어야 합니다.</red>"
  weapon-lost: "<red>전투 무기를 잃어 전투 모드가 해제되었습니다.</red>"
  cooldown: "<red>쿨다운이 {remaining}초 남았습니다.</red>"
  skill-cast: ""
  no-skill: "<gray>해당 슬롯에는 스킬이 없습니다.</gray>"
  cast-failed: "<red>스킬 시전에 실패했습니다.</red>"
  return-weapon: "<yellow>전투 무기 슬롯으로 복귀했습니다.</yellow>"
```

## 무기 YAML 구조

### 액티브 스킬 예시

```yml
mmo-type: BOW
display-name: "<gradient:#6EE7B7:#3B82F6>AWAKENED_ARCHER_GALEBOW</gradient>"

skills:
  slot-1:
    mythic-id: RAPID_SHOT
    enabled: true
    timing:
      cooldown: 2.5
    display:
      name: "<green>연사</green>"
      description: "짧은 시간 동안 빠르게 화살을 발사합니다."
      icon: BOW
      custom-model-data: 0
    modifiers:
      damage: 7
      ratio: 1.0
      projectile_count: 5
```

### 패시브 스킬 예시

```yml
passives:
  passive-1:
    type: SOUL_LINK
    enabled: true
    timing:
      interval: 2
      cooldown: 0
    display:
      name: "<green>영혼 연결</green>"
      description: "주기적으로 생명력을 회복합니다."
      icon: ENDER_EYE
      custom-model-data: 0
    modifiers:
      heal: 3
      duration: 4
```

## Modifiers 규칙

- 액티브 기본 계수는 `modifiers.damage`, `modifiers.ratio`
- `ratio`는 미지정 시 기본값 `1.0`
- 그 외 키(`radius`, `amount`, `duration` 등)는 MythicMobs 쪽에서 `<modifier.key>`로 참조
- GUI의 커스텀 modifier 입력 형식은 `key:value`

## PlaceholderAPI

식별자: `bsrpgskills`

- `%bsrpgskills_combat_mode%`
- `%bsrpgskills_weapon_name%`
- `%bsrpgskills_weapon_id%`
- `%bsrpgskills_active_count%`
- `%bsrpgskills_slot_1_name%`
- `%bsrpgskills_slot_1_id%`
- `%bsrpgskills_slot_1_cooldown%`
- `%bsrpgskills_slot_1_damage%`
- `%bsrpgskills_slot_1_enabled%`
- `%bsrpgskills_slot_1_keybind%`

`slot_1` 부분은 `slot_1` ~ `slot_6` 사용 가능

## 빌드

```bash
./gradlew shadowJar
```

생성 산출물:

- `build/libs/BSRpgSkills-<version>.jar`
