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
