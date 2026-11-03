Base UI components
==================

Previews
--------

Use @ProtonVpnPreview and ProtonVpnTvPreview annotations plus ProtonVpnPreview
and ProtonVpnTvPreview composables. Together they provide several variants
(annotations) and a common theme and background (the composables).

Example:

@ProtonVpnPreview
@Composable
fun MyComposablePreview() {
  ProtonVpnPreview(/* optional parameters */) {
    MyComposable(...)
  }
}

Dimensions and other values
---------------------------

Write dimensions, colors, scales, shapes etc. inline at the place of use, e.g.
`Modifier.height(56.dp)`, `RoundedCornerShape(16.dp)`. Extract a named constant
only when the same value is needed in more than one place and the places must
stay in sync (e.g. `CollapsedDrawerWidth` used by the TV drawer and the
content padding). A value used once doesn't need a name; if its purpose isn't
obvious, add a short comment next to it instead.

Images
------

To add raster assets exported from Figma, use
`scripts/figma_export_to_webp.py <export.zip>` (see its docstring for the
export settings).

Accessibility
-------------

Every interactive element must be usable with TalkBack (touch on mobile, D-pad on TV).

Action labels: give clicks a label describing what happens, e.g. "connect",
"upgrade". Use `onClickLabel` / `onLongClickLabel` on `clickable` /
`combinedClickable`. If the click is handled by a component that doesn't take a
label (e.g. a TV Material `Surface`), add `semantics { onClick(label, null) }`.
Examples: `tv/countries/ui/CountryGridTile.kt`, `redesign/vpn/ui/ConnectIntentRow.kt`.

Secondary actions (open details, delete, move) go into `customActions` so they
are reachable without extra focusable elements, see `ConnectIntentRow.kt`.

One item, one announcement: use `semantics(mergeDescendants = true)` on rows
and tiles so their texts are read together. Decorative images (flags, icons next
to text) get `contentDescription = null`.

Merge checkboxes and radio buttons with their labels, the whole item should use
toggleable or selectable modifiers for proper handling. Clear semantics on the
checkboxes and radios inside a toggleable/selectable block.

State must not be conveyed by color or alpha alone. Add it as
`stateDescription` or to `contentDescription`, e.g. "connected",
"unavailable", "in maintenance" (`accessibility_item_*` strings).

Section titles get `semantics { heading() }` so they can be used for navigation.

Strings used only by accessibility services are named `accessibility_*`. Reuse
existing ones (`accessibility_action_connect`, `accessibility_action_upgrade`,
...) where they fit. In string comments describe the action and where it is
used; don't quote what TalkBack will say unless verified on that platform -
announcements differ between mobile and TV.

Testing: assert labels with Robolectric Compose tests on semantics nodes, see
`TvCountriesAccessibilityTests`. Check new screens manually with TalkBack (on a
TV emulator for TV screens).
