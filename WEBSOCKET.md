# WebSocket API

This document describes the WebSocket endpoint, how to connect, and the message schemas exchanged between client and server in the KDS app.

## Endpoint
- URL: `wss://go.realm.chat/api/v1/ws`
- Query params: `user_id` (required)
  - Example: `wss://go.realm.chat/api/v1/ws?user_id=2`
  - Development fallback: if no `user_id` is stored, the app uses `demo_user_123`.
- Protocol: Secure WebSocket (`wss`)
- Authentication: Not required for the WebSocket connection. HTTP APIs use `Authorization: Bearer <token>`.

## Connecting

### React hook (recommended)
```ts
import useWebSocket from './src/hooks/useWebSocket';

const MyComponent = () => {
  const {
    connectionStatus,
    lastMessage,
    sendMessage,
    reconnect,
    isConnected,
  } = useWebSocket({
    autoConnect: true,
    onMessage: (data) => console.log('WS received:', data),
    onConnected: () => console.log('WS connected'),
    onDisconnected: () => console.log('WS disconnected'),
    onError: (err) => console.error('WS error', err),
  });

  return null;
};
```

### Direct manager
```ts
import WebSocketManager from './src/utils/WebSocketManager';

const wsManager = new WebSocketManager();
const userId = '2';
const url = `wss://go.realm.chat/api/v1/ws?user_id=${userId}`;

wsManager.on('connected', () => console.log('connected'));
wsManager.on('message', (data) => console.log('message', data));
wsManager.on('error', (err) => console.error('error', err));

wsManager.connect(url, userId);
wsManager.send({ type: 'ping' });
```

### Raw browser API
```js
const ws = new WebSocket('wss://go.realm.chat/api/v1/ws?user_id=2');
ws.onopen = () => console.log('open');
ws.onmessage = (e) => console.log('message', e.data);
ws.onerror = (e) => console.error('error', e);
ws.onclose = (e) => console.log('close', e.code, e.reason);
ws.send(JSON.stringify({ type: 'ping', timestamp: Date.now() }));
```

## Heartbeat
- Server may send `{ type: 'ping' }`.
- Client responds with `{ type: 'pong' }`.
- Client also sends a health ping every 30s:
```json
{
  "type": "ping",
  "timestamp": 1730000000000,
  "health_check": true
}
```
- Client tracks last pong time to assess connection health.

## Incoming Messages (server → client)
All messages include a top-level `type`. Order-related messages carry an `Order` payload under `data`.

- `order_created`
  - Payload: `{ type: 'order_created', data: Order }`
- `order_updated`
  - Payload: `{ type: 'order_updated', data: Order }`
- `order_deleted`
  - Payload: `{ type: 'order_deleted', orderId: string }`
- `product_prepared`
  - Payload: `{ type: 'product_prepared', data: Order }`
- `ping`
  - Payload: `{ type: 'ping', ... }`
- `pong`
  - Payload: `{ type: 'pong', ... }`

### Order schema
From `src/types/index.ts`:
```ts
export interface Order {
  id: string;
  order_id: string;
  global_note?: string;
  customer_name: string;
  table_number?: string;
  created_at: string;
  delivery_method: string;
  pickup_time?: string;
  printed_at?: string;
  device_id: string;
  user_id: string;
  status: OrderStatus;
  products: Product[];
  // Legacy compatibility fields
  orderNumber?: string;
  customerName?: string;
  items?: OrderItem[];
  priority?: OrderPriority;
  createdAt?: Date;
  updatedAt?: Date;
  timer?: number;
  station?: string;
  notes?: string;
}

export type OrderStatus =
  | 'new'
  | 'paid'
  | 'preparing'
  | 'processing'
  | 'ready'
  | 'printed'
  | 'completed'
  | 'canceled';

export interface Product {
  id: string;
  name: string;
  price: number;
  quantity: number;
  note?: string;
  prepared?: boolean;
}

export interface OrderItem {
  id: string;
  name: string;
  quantity: number;
  price: number;
  category: string;
  specialInstructions?: string;
  variant?: string;
}
```

### Example payloads
- New/updated order
```json
{
  "type": "order_created",
  "data": {
    "id": "abc123",
    "order_id": "100045",
    "customer_name": "John Doe",
    "table_number": "A-5",
    "created_at": "2025-11-04T12:34:56.000Z",
    "delivery_method": "dine_in",
    "status": "paid",
    "device_id": "device-01",
    "user_id": "2",
    "products": [
      { "id": "p1", "name": "Burger", "quantity": 2, "price": 5.5 },
      { "id": "p2", "name": "Fries", "quantity": 1, "price": 2.0 }
    ]
  }
}
```
- Order deletion
```json
{ "type": "order_deleted", "orderId": "abc123" }
```
- Product prepared update
```json
{
  "type": "product_prepared",
  "data": {
    "id": "abc123",
    "order_id": "100045",
    "status": "processing",
    "products": [
      { "id": "p1", "name": "Burger", "quantity": 2, "price": 5.5, "prepared": true },
      { "id": "p2", "name": "Fries", "quantity": 1, "price": 2.0, "prepared": false }
    ]
  }
}
```

## Commands (client → server)
Commands are sent with a standardized envelope. The manager adds `request_id` and `timestamp`.

### Command envelope
```json
{
  "type": "update_order_status",
  "data": { "order_id": "100045", "status": "ready" },
  "request_id": "req_1730000000_xxxxxx",
  "timestamp": 1730000000000
}
```

### Supported commands
- `update_order_status`
  - Data: `{ order_id: string, status: OrderStatus }`
- `update_product_prepared`
  - Data: `{ order_id: string, product_id: string, prepared: boolean }`
- `create_order`
  - Data: `Order`-like payload for creation
- `delete_order`
  - Data: `{ order_id: string }`

### Responses
- Success: `{ type: 'command_success', request_id: string, ... }`
- Error: `{ type: 'command_error', request_id: string, error: string }`
- Other data responses may omit `command_*` types but include the matching `request_id`.

## Reconnection & errors
- Automatic backoff: attempts increase up to 30s interval; max 5 attempts.
- Error categories: `server`, `network`, `auth`, `websocket`, `security`, `unknown`.
- Manager exposes `configureBackoff()` and `getBackoffStatus()` for tuning and diagnostics.

## Notes
- Use a valid `user_id` assigned to your account or device.
- WebSocket does not require an auth token; HTTP fallbacks and REST calls do.
- Health pings help keep the connection alive and provide status.