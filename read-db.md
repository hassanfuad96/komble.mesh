Here’s the read-only ContentProvider wired to your contract. It exposes your orders and items in SQLite using the URIs you specified, so other apps can query safely via ContentResolver .

What’s Added

- Orders tables in komble.db :
  - orders with order_id , id , created_at , delivery_method , user_id , status
  - order_items with order_id , item_id , name , quantity , variant , category_id
- Read-only provider com.komble.mesh.orders :
  - content://com.komble.mesh.orders/orders
  - content://com.komble.mesh.orders/orders/<order_id>
  - content://com.komble.mesh.orders/order_items?order_id=<order_id>
Authority and URIs

- Authority: content://com.komble.mesh.orders
- Endpoints:
  - orders (list): returns rows from orders
  - orders/<order_id> (single): filters by order_id
  - order_items?order_id=<order_id> : returns items for that order
Provider MIME Types

- orders list: vnd.android.cursor.dir/vnd.mesh.order
- orders/<order_id> : vnd.android.cursor.item/vnd.mesh.order
- order_items : vnd.android.cursor.dir/vnd.mesh.order_item
Consumer Examples

- Query all orders:
  - ContentResolver.query(Uri.parse("content://com.komble.mesh.orders/orders"), null, null, null, "created_at DESC")
- Query a single order:
  - ContentResolver.query(Uri.parse("content://com.komble.mesh.orders/orders/923808833302126"), null, null, null, null)
- Query items for an order:
  - ContentResolver.query(Uri.parse("content://com.komble.mesh.orders/order_items?order_id=923808833302126"), null, null, null, null)
Columns Returned

- orders : order_id , id , created_at , delivery_method , user_id , status
- order_items : order_id , item_id , name , quantity , variant , category_id
- Use a projection array to request specific columns; otherwise all columns are available.
How To Populate

- Upsert order:
  - AppDatabaseHelper.getInstance(context).upsertOrder(orderId, id, createdAt, deliveryMethod, userId, status)
- Replace items:
  - AppDatabaseHelper.getInstance(context).replaceOrderItems(orderId, itemsList)
- Typical flow after fetch:
  - Parse API response, call upsertOrder(...) , then replaceOrderItems(...) in one app-side transaction.
Notes

- Provider is read-only ( insert , update , delete throw UnsupportedOperationException ).
- Registered in AndroidManifest.xml with android:exported="true" and android:grantUriPermissions="true" .
- DB version bumped to 2 to add tables safely; old installs migrate by creating the new tables.

Fetching and Storing from API

- Call the helper to fetch and store orders so the provider serves real data:
  - Kotlin:
    - OrdersStoreHelper.fetchAndStore(context, userId = 1, authorizationHeader = null)
- The helper uses OkHttp via MerchantOrdersApi and Gson to parse:
  - API: https://go.realm.chat/api/v1/orders/user/1
  - Response shape: { success, message, data: [ OrderDto ] }
- Stored fields:
  - orders: order_id, id, created_at, delivery_method, user_id, status
  - order_items: order_id, item_id, name, quantity, variant, category_id

Consumer reads remain unchanged; once populated, all three URIs return data.