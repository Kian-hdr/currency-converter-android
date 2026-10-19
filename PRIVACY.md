# Privacy

Currency Converter needs no account and includes no ads, analytics, tracking SDKs, location access or financial-account connections.

Calculations, selected currencies, recent history and last successful exchange rates are stored in the app’s private storage on your device. Android cloud backup is disabled. Clear history from the History dialog; clearing app data removes all local preferences and cached data.

The app makes HTTPS requests to fxapi.app for a USD exchange-rate table while open. These requests include a general app User-Agent and expose ordinary network information such as IP address to the provider. They do not contain your amounts, expressions, history, name or device identifier. Currency pairs are calculated locally from the table.

Network refresh is bounded and only runs while the app is in the foreground. Reconnection schedules a fresh request after a short debounce. Offline conversion uses the last successful table and shows its original update time and saved/offline status. Provider rates are indicative; bank/card spreads and fees may differ.

Data provider: https://fxapi.app/. FXAPI publicly advertises a free, keyless API for application integration. See the provider’s website for current service information.

The app checks whether Whicons is installed to select its own white launcher icon. It does not read the full installed-app inventory or send icon-pack information over the network.
