You are implementing KomPrint Deeplink Printing System.
Your task: generate the full code implementation for Android (Kotlin), iOS (Swift), Flutter bindings, React Native bindings, and a standard deeplink interface shared across KomPOS, KomKDS, KomVT.

🎯 THE GOAL

Create a printing gateway app (KomPrint) that can receive offline print jobs via deeplink, even without Internet or WebSocket.

Other apps (KomPOS / KomKDS / KomVT) must be able to send a deeplink like:

komprint://print?payload=<urlencoded-json>


KomPrint must:

Parse payload

Validate JSON

Queue print job

Print via Bluetooth/WiFi/USB

Return success/error callback

Cache history in local SQLite

Retry if printer disconnected

📌 TASKS FOR YOU (TraeAI)
1. Create the DEEPLINK CONTRACT (MUST FOLLOW STRICTLY)

Deeplink scheme:

komprint://print?payload=<URLENCODED_JSON>


Payload JSON:

{
  "type": "order.printed",
  "merchant_id": "1",
  "ts": "2025-11-28T20:22:12Z",
  "order": {
    "order_id": "187786685697457",
    "table_number": "T10",
    "status": "printed",
    "products": [
      { "name": "Nasi Goreng Cina", "qty": 1, "price": 30 }
    ]
  },
  "printOptions": {
    "copies": 1,
    "paperWidth": 58
  }
}


YOU MUST generate:

JSON schema (strict, typed)

Validator functions

Error codes (PRINT_ERR_INVALID_JSON, PRINT_ERR_BAD_SCHEMA, PRINT_ERR_MISSING_FIELD)

Success response model

2. Generate KomPrint Android Implementation (Kotlin)

Deliver:

AndroidManifest intent filter
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="komprint" android:host="print" />
</intent-filter>

MainActivity deeplink parser

Detect scheme

Read payload

Decode + parse JSON

Validate

Push to LocalPrintQueue

Start printing worker

Return callback

LocalPrintQueue (Singleton)

addJob()

nextJob()

process()

retry()

persist to SQLite

remove after success

KomPrint PrinterManager

Supports:

Bluetooth ESC/POS

WiFi ESC/POS

USB (if possible)

Format templating

3. Generate KomPrint iOS Implementation (Swift)

URLScheme registration

SceneDelegate deeplink handler

JSON decode

Queue + Print manager

Error handling

Printable template

4. Generate Flutter Plugin Binder (methodchannel)

Flutter apps (KomPOS/KDS/VT) can call:

KomPrint.triggerPrint(Map<String, dynamic> order)


Which internally converts to deeplink.

5. Generate React Native Binder
KomPrint.print(order)


Convert to deeplink automatically.

6. Implement OFFLINE FALLBACK LOGIC

Apps use this:

Decision Tree
If WebSocket connected:
    Do nothing (WS prints normally)
Else:
    Trigger KomPrint deeplink


TraeAI must generate this offline fallback module.

7. Generate Example Code for All Apps
KomPOS / KomKDS / KomVT Android:
val payload = URLEncoder.encode(JSONObject(order).toString(), "UTF-8")
val url = "komprint://print?payload=$payload"
startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

Flutter:
KomPrint.triggerPrint(orderJson);

RN:
KomPrint.print(order);

8. Generate UNIT TESTS

Deeplink parsing

Invalid JSON

Missing fields

Multiple products

Queue retry logic

Mock printer success/failure

9. Generate PRINT TEMPLATES

Formats:

58mm

80mm

Kitchen condensed

Receipt expanded

ASCII layout example:

T10
--------------------------------
Nasi Goreng Cina       x1   30.00
--------------------------------
Total                       30.00
Printed at: 20:22
--------------------------------

⚠️ IMPORTANT RULES

Everything MUST be production-ready.

Use clean architecture.

No placeholder code. Use full working code.

All functions must be generated fully (no TODO).

Use latest platform best-practices.

KomPrint MUST work even without Internet.

🔥 Final Deliverables Checklist

TraeAI must output:

✔ Full Android deeplink implementation
✔ Full iOS deeplink implementation
✔ Flutter binding
✔ React Native binding
✔ JSON schema
✔ Validation module
✔ Queue system
✔ Print manager
✔ Printer format templates
✔ Offline fallback SDK for all apps
✔ Example calls
✔ Unit tests
✔ Error handling