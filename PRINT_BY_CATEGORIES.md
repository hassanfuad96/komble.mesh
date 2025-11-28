# Print by Category

This document explains how category-based printing currently works in the KDS app, including category selection, item filtering, printing, and post-print status updates.

## Overview
- The app filters order items by selected API categories before printing.
- Selection is persisted locally and affects both what gets printed and how order/product statuses are updated after a successful print.
- If no items match the selected categories, the print is skipped.

## Category Sources and Storage
- API endpoint: `https://node-client.realm.chat/api/product/categories`
- Service: `src/utils/categoriesApi.ts`
- Keys in storage:
  - `selected_categories` (JSON array of numbers)
  - `uncategorized_selected` (`'true'` or `'false'`)
- Special values:
  - `null` represents the "Uncategorized" category and is stored separately using `uncategorized_selected`.
  - `0` represents "All Categories" sentinel; when present, all items are included in printing.

### Core Functions
- `CategoriesApiService.fetchCategories()`
  - Loads categories and prepends an `Uncategorized` category with `id: null`.
- `CategoriesApiService.getSelectedCategories(): (number | null)[]`
  - Returns numeric category IDs plus `null` if `uncategorized_selected === 'true'`.
- `CategoriesApiService.setSelectedCategories(categoryIds: (number | null)[])`
  - Persists numeric IDs in `selected_categories` and flags `uncategorized_selected` when `null` is included.
- `CategoriesApiService.filterOrderItems(items: any[])`
  - Filtering rules:
    - If `selectedCategories.includes(0)`: return all items (All Categories).
    - If `selectedCategories.length === 0`: return `[]` (no categories selected → print skipped).
    - Otherwise, return items whose `category_id` matches one of the selected IDs.

## Printing Pipeline
- Trigger: `useOrders.autoPrintNewOrder(order)` in `src/hooks/useOrders.ts` when `auto_print_enabled === 'true'`.
- Default printer: `PrinterManager.loadPrinters()` and select the one with `isDefault`.
- Content generation: `PrinterManager.formatOrderForPrintWithApiCategories(order)`
  - Filters items via `CategoriesApiService.filterOrderItems()`.
  - Labels with category name via `CategoriesApiService.getCategoryName(id)`.
  - Returns an empty string if no items match; printing is skipped.

### Example
```ts
import { CategoriesApiService } from './src/utils/categoriesApi';
import { PrinterManager } from './src/utils/printerManager';

// Select All Categories explicitly
await CategoriesApiService.setSelectedCategories([0]);

// Format and print
const content = await PrinterManager.formatOrderForPrintWithApiCategories(order);
if (content.trim() !== '') {
  const printers = await PrinterManager.loadPrinters();
  const defaultPrinter = printers.find(p => p.isDefault);
  if (defaultPrinter) {
    await PrinterManager.printOrder(defaultPrinter, content, order);
  }
}
```

## Post‑Print Status Updates
After a successful print, statuses are updated depending on the selection.

### All Categories selected (`0` present)
- Update order status to `printed`:
  - Path: `OrderApiService.updateOrderStatus(orderId, 'printed')`
  - Prefers WebSocket commands via `WebSocketCommandService.updateOrderStatus` with HTTP fallback.

### Specific categories selected (no `0`)
- Mark only matching items as prepared:
  - Path: `OrderApiService.updateProductPrepared(orderId, productId, true)` for each matching item.
- If all items are now prepared → update order status to `ready`:
  - Path: `OrderApiService.updateOrderStatus(orderId, 'ready')`.

### Where this happens
- Immediate auto‑print: `src/hooks/useOrders.ts` (`autoPrintNewOrder`) applies status updates after printing.
- Queued prints: `src/utils/printerManager.ts` (`updateStatusesAfterPrint`) mirrors the same logic for retry queue processing.

## Queued Retry Flow
- If printing fails, the order is enqueued and retried:
  - `PrinterManager.enqueueAutoPrint(order, content)` and `PrinterManager.processPrintQueue()`.
- On successful queued print, `updateStatusesAfterPrint(order)` applies the same category‑aware status updates.

## Legacy Printer Categories (String‑based)
- There is older support for printer category filtering using string tags (e.g., `all`, `beverage`, `cold`, `dessert`, `hot`).
- Functions: `PrinterManager.filterItemsByCategories` and `PrinterManager.formatOrderForPrint`.
- Current logic prefers API category IDs and uses `formatOrderForPrintWithApiCategories`.

## Edge Cases and Notes
- No categories selected: printing is skipped (filtered list is empty).
- `Uncategorized` handling: `null` category persists via `uncategorized_selected` and participates in filtering.
- Category ID parsing: `category_id` is treated as string in items; the code uses `parseInt(item.category_id)` when matching.
- Order ID resolution: status updates use `order.order_id` when present; otherwise `order.id`.
- Health and reliability: printing can be retried via the queue; status updates prefer WebSocket and fallback to HTTP.

## Key References
- `src/utils/categoriesApi.ts`
- `src/utils/printerManager.ts`
- `src/hooks/useOrders.ts`
- `src/utils/orderApi.ts`
- `src/utils/webSocketCommands.ts`