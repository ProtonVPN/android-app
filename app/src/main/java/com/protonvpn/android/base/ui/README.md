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

