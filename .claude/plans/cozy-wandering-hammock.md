# Plan: RoundGauge Icon Scaling & Positioning

## Context

The RoundGauge component (`src/jsMain/kotlin/comp/RoundGauge.kt`) renders a circular gauge with an icon inside.
Currently the icon size is determined by Semantic UI's default label font sizing and doesn't scale with the gauge's
`size` prop. The icon should:

1. **Grow with the gauge size** — `fontSize` proportional to `props.size`
2. **Sit at the midpoint between center and top of the circle** — the circle center is at 50% from top, the top edge is
   at ~0%, so the icon center should be at ~25% from top

## Changes

**File:** `src/jsMain/kotlin/comp/RoundGauge.kt`

Modify the icon styling block (lines 158-166):

```kotlin
icon.iconFn().then {
    css {
        paddingTop = props.size * 0.075
        color = iconColor
        if (!isDisabled) {
            put("text-shadow", "0 0 10px")
        }
    }
}
```

Change to:

```kotlin
icon.iconFn().then {
    css {
        fontSize = props.size * 0.3       // icon scales with gauge
        paddingTop = props.size * 0.12    // center icon between top edge and center
        color = iconColor
        if (!isDisabled) {
            put("text-shadow", "0 0 10px")
        }
    }
}
```

The exact `fontSize` multiplier (0.3) and `paddingTop` multiplier (0.12) may need visual tuning. The key insight: the
Semantic UI circular label centers its content by default, so the icon is already roughly centered. Adding `paddingTop`
pushes it down from center. We want it at 25% from top (midpoint of center-to-top), which means pushing it UP from
center — so we actually need a smaller paddingTop or possibly negative margin.

Better approach: use absolute positioning on the icon to place it precisely at 25% from top.

```kotlin
icon.iconFn().then {
    css {
        position = Position.absolute
        top = 50.pct                        // halfway between 0% (top) and 50% (center)
        left = 50.pct
        fontSize = props.size * 0.3
        put("transform", "translate(-50%, -100%)")  // center the icon on that point
        color = iconColor
        if (!isDisabled) {
            put("text-shadow", "0 0 10px")
        }
    }
}
```

Wait — the label already has `position: relative`. The icon midpoint should be at 25% from top. So `top = 25%` with
`transform: translate(-50%, -50%)` centers the icon at that point.

## Final approach

```kotlin
icon.iconFn().then {
    css {
        position = Position.absolute
        top = 25.pct
        left = 50.pct
        put("transform", "translate(-50%, -50%)")
        fontSize = props.size * 0.3
        color = iconColor
        if (!isDisabled) {
            put("text-shadow", "0 0 10px")
        }
    }
}
```

## Verification

- Visual check: open the app, look at round gauges at different sizes
- The icon should visually sit between the center dot and the 12 o'clock position
- Icon should be proportionally larger on bigger gauges
