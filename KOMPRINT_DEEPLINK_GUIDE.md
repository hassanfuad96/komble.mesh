# KomPrint Deeplink Printing Guide

This guide explains how KomPOS, KomKDS, and KomVT can trigger printing via the KomPrint app using a simple deeplink. The deeplink works offline and queues a print job locally on the device.

## Deeplink Format
- Scheme: `komprint`
- Path: `print`
- Query: `payload=<urlencoded-json>`
- Full example: `komprint://print?payload=%7B...urlencoded%20json...%7D`

## Payload Schema
KomPrint expects the following JSON payload (URL-encoded when placed in the deeplink):

```json
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
```

Notes:
- `type` must be `order.printed`.
- `merchant_id` is the merchant owner of the order.
- `ts` is an ISO 8601 timestamp.
- `order.products[*]` require `name`, `qty`, `price`. Optional: `categoryId` if you want station printers to filter.
- `printOptions.paperWidth` determines the receipt template (58mm or 80mm).

## Printer Roles
- Main printers (`role = "main"`) receive receipt printouts from deeplink.
- Station printers (`role = "station"`) receive category-filtered kitchen tickets.

## Android (Kotlin)
Trigger KomPrint from any Android app:

```kotlin
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder
import org.json.JSONObject

fun triggerKomPrint(context: android.content.Context, payload: JSONObject) {
    val encoded = URLEncoder.encode(payload.toString(), "UTF-8")
    val url = "komprint://print?payload=$encoded"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
```

Result broadcast (optional to listen):
- Action: `komprint.PRINT_RESULT`
- Extras: `success: Boolean`, `error: String?`, `message: String?`

## iOS (Swift)
Register the URL scheme `komprint` in your app and open the deeplink:

```swift
import UIKit

func triggerKomPrint(payloadJson: String) {
    let encoded = payloadJson.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? payloadJson
    let urlString = "komprint://print?payload=\(encoded)"
    if let url = URL(string: urlString) {
        UIApplication.shared.open(url, options: [:], completionHandler: nil)
    }
}
```

## Flutter
Using `url_launcher`:

```dart
import 'dart:convert';
import 'package:url_launcher/url_launcher.dart';

Future<void> triggerKomPrint(Map<String, dynamic> payload) async {
  final jsonStr = jsonEncode(payload);
  final encoded = Uri.encodeComponent(jsonStr);
  final url = Uri.parse('komprint://print?payload=$encoded');
  await launchUrl(url, mode: LaunchMode.externalApplication);
}
```

## React Native
Using the `Linking` API:

```ts
import { Linking } from 'react-native';

export async function triggerKomPrint(payload: Record<string, unknown>) {
  const jsonStr = JSON.stringify(payload);
  const encoded = encodeURIComponent(jsonStr);
  const url = `komprint://print?payload=${encoded}`;
  await Linking.openURL(url);
}
```

## Error Handling
- Validation errors: KomPrint rejects the deeplink and broadcasts `komprint.PRINT_RESULT` with `success=false` and an `error` code (`PRINT_ERR_INVALID_JSON`, `PRINT_ERR_BAD_SCHEMA`, or `PRINT_ERR_MISSING_FIELD`).
- Success enqueue: KomPrint broadcasts `success=true` with `message="queued"` after the job is queued.
- Printing happens asynchronously; logs are stored locally in SQLite.

## Example Payload

```json
{
  "type": "order.printed",
  "merchant_id": "42",
  "ts": "2025-11-30T12:00:00Z",
  "order": {
    "order_id": "100045",
    "table_number": "A-5",
    "status": "printed",
    "products": [
      { "name": "Burger", "qty": 2, "price": 5.5 },
      { "name": "Fries", "qty": 1, "price": 2.0 }
    ]
  },
  "printOptions": { "copies": 1, "paperWidth": 80 }
}
```

