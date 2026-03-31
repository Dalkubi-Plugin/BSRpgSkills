# BSSkill

Paper 1.21.8 기반 무기 스킬 플러그인입니다.  
MMOItems 무기를 기준으로 전투 모드를 구성하고, MythicLib API를 통해 MythicMobs 스킬을 직접 발동합니다.

## 핵심 구조

- 무기별 스킬 정보는 `plugins/BSSkill/weapons/*.yml`에서 관리합니다.
- 평타 슬롯 1은 `DamageType.WEAPON` 공격일 때만 발동합니다.
- 액티브/패시브 모두 `modifiers` 아래 값을 자유롭게 확장할 수 있습니다.
- GUI에서 자주 수정하는 값은 바로 편집할 수 있고, 변경 즉시 저장됩니다.
- 디버그 로그는 `debug.enabled`가 켜졌을 때만 자세히 출력됩니다.

## 권장 설정 구조

```yml
skills:
  slot-4:
    mythic-id: RAPID_ARROWS
    enabled: true
    timing:
      cooldown: 3
    display:
      name: "<gradient:#FF4D4D:#8B0000>섬광의 속사</gradient>"
      description: "짧은 시간 동안 연속 사격을 퍼붓습니다."
      icon: BOW
      custom-model-data: 0
    modifiers:
      damage: 5
      radius: 3
      projectile_count: 8
```

```yml
passives:
  passive-1:
    type: SOUL_LINK
    enabled: true
    timing:
      interval: 2
      cooldown: 0
    display:
      name: "<green>영혼의 연결</green>"
      description: "주기적으로 주변 아군을 강화합니다."
      icon: ENDER_EYE
      custom-model-data: 0
    modifiers:
      heal: 3
      duration: 4
```

## modifiers 규칙

- `modifiers.damage`는 액티브 기본 데미지로 사용됩니다.
- `modifiers.ratio`는 액티브 스킬 기본 배율용 기본값이며, 값이 없으면 자동으로 `1.0`이 들어갑니다.
- 그 외 키는 MythicLib/MythicMobs에서 `<modifier.key>` 형태로 참조합니다.
- GUI에서 새 modifier 추가 시 `damage`와 `:` 정도만 막고, 이름은 자유롭게 사용할 수 있습니다.

허용 예시:

```text
radius:3.5
projectile_count:6
test-value:2
```

제한 사항:

- `damage`는 전용 버튼으로만 수정합니다.
- `:` 문자는 `key:value` 구분자로 쓰이므로 이름에 사용할 수 없습니다.

## GUI

`/bsskill editor`로 편집 GUI를 엽니다.

액티브 편집:

- 활성화 여부
- 스킬 ID
- 표시 이름
- 기본 데미지
- 쿨타임
- 설명
- modifier 목록 조절
- modifier 추가 (`key:value`)

패시브 편집:

- 활성화 여부
- 스킬 ID
- 표시 이름
- 발동 주기
- 쿨타임
- 설명
- modifier 값 조절

## 명령어

| 명령어 | 권한 | 설명 |
|--------|------|------|
| `/bsskill editor` | `bsskill.admin` | GUI 편집기 열기 |
| `/bsskill reload` | `bsskill.admin` | 설정 리로드 |
| `/bsskill list` | `bsskill.admin` | 무기 목록 확인 |
| `/bsskill info <weapon>` | `bsskill.admin` | 무기 설정 확인 |
| `/bsskill toggle` | `bsskill.use` | 전투 모드 토글 |
| `/bsskill save` | `bsskill.admin` | 전체 저장 |
| `/bsskill debug` | `bsskill.admin` | 자신의 전투 상태 디버그 |
| `/bsskill debuglog` | `bsskill.admin` | 디버그 로그 ON/OFF |

## config.yml

```yml
use-actionbar: true
actionbar-interval: 5

debug:
  enabled: false
```

- `debug.enabled: true`일 때만 상세 로드 로그와 일부 내부 디버그 메시지가 콘솔에 출력됩니다.
- 평소 운영에서는 `false`를 권장합니다.

## 빌드

```bash
./gradlew shadowJar
```
