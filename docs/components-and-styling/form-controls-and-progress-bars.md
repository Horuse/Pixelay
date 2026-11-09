# Form Controls & Progress Bars

Pixelay provides interactive form controls for settings and stateful options.

## Checkbox (`Checkbox`)

The `Checkbox` component renders a geometric square indicator with an optional text label:

```java
Checkbox soundOption = checkbox(viewer, "Enable Sound FX", true, (player, checked) -> {
    player.sendMessage("Sound status: " + checked);
})
    .boxSize(18)
    .checkColor(0xFF38BDF8);
```

## Toggle Switch (`Toggle`)

The `Toggle` component renders a smooth pill switch:

```java
Toggle pvpToggle = toggle(viewer, "Allow World PvP", false, (player, active) -> {
    player.sendMessage("PvP status: " + active);
})
    .thumbColor(0xFFFFFFFF)
    .activeTrackColor(0xFF10B981)
    .inactiveTrackColor(0xFF475569);
```

## Progress Bar (`ProgressBar`)

The `ProgressBar` displays a filled progress track:

```java
ProgressBar hpBar = progressBar(0.75) // 75% fill value
    .width(300)
    .trackHeight(12)
    .fillColor(0xFF10B981)
    .trackColor(0x30FFFFFF);
```
