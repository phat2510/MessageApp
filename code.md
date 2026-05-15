# MessageApp — Giải thích chi tiết từng file

## Flow tổng kết

```
[Client A] kết nối ws://host/ws/chat?token=xxx
  → SecurityConfig: cho qua
  → WsHandshakeAuthInterceptor: rate limit IP + verify JWT → lưu userId vào session
  → ChatWebSocketHandler.afterConnectionEstablished: activeSessions["userA"] = session

[Client A] gửi {conversationId, content, clientMessageId}
  → WsPayloadFilter: validate kích thước + JSON + fields + type + sanitize
  → ChatWebSocketHandler: build ChatMessageEvent → publish "chat-topic" (key=conversationId)
  → ACK "SENT" trả về A ngay

[ChatService] consume "chat-topic"
  → lưu DB → messageId được sinh
  → query members theo conversationId → [A, B, C]
  → bỏ A, fanout publish "delivery-topic" per member (key=memberId)
     event B: {targetMemberId: B, conversationId, content, messageId, ...}
     event C: {targetMemberId: C, conversationId, content, messageId, ...}

[DeliveryEventListener] consume "delivery-topic"
  → nhận event của B → pushToUser("userB", payload)
  → activeSessions.get("userB") → session.sendMessage()
  → B nhận được {conversationId, content, senderId, ...} → hiển thị đúng conversation
```

---

## `common-shared` — Thư viện dùng chung

### `ChatMessageEvent`

```java
public class ChatMessageEvent implements Serializable {
    private String clientMessageId;  // ID do client tự sinh, dùng để chống gửi trùng
    private String targetMemberId;   // biến tạm — chỉ để routing delivery, không hiển thị cho client
    private String messageId;        // ID message sau khi lưu DB, do server sinh ra
    private String senderId;         // userId của người gửi
    private String senderName;       // tên hiển thị (đang bị set nhầm = userId)
    private String conversationId;   // conversation nào, đi theo suốt flow
    private List<String> memberIds;  // danh sách member trong conversation
    private String content;          // nội dung tin nhắn
    private long timestamp;          // thời điểm gửi
    private String type;             // TEXT / IMAGE / VIDEO
}
```

Class này là **object trung gian** được truyền qua Kafka giữa các service. `Serializable` để Java có thể convert thành bytes khi truyền qua mạng.

---

### `ErrorCodeMessageRoute`

```java
CONTENT_EMPTY("VALID_001", "Nội dung tin nhắn không được để trống"),
KAFKA_SEND_ERROR("SYS_001", "Lỗi đường truyền Kafka"),
```

Enum chứa tất cả mã lỗi của hệ thống — mỗi lỗi có `code` (để client xử lý logic) và `message` (để hiển thị). Hiện tại chưa được dùng ở đâu, vẫn còn dùng string thô trong code.

### `BusinessException`

```java
public BusinessException(ErrorCodeMessageRoute errorCode) {
    super(errorCode.getMessage()); // message của RuntimeException = message của errorCode
    this.errorCode = errorCode;    // giữ lại errorCode để caller lấy ra dùng
}
```

Wrap `ErrorCodeMessageRoute` thành Exception để throw. Cũng chưa được dùng.

---

## `chat-ws-gateway` — Service xử lý WebSocket

### `JacksonConfig`

```java
mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
```

Đăng ký 1 `ObjectMapper` bean dùng chung toàn app. Disable việc serialize `LocalDateTime` thành số — thay vào đó ra string `"2025-05-14T10:30:00"` cho dễ đọc.

---

### `JwtUtil`

```java
public Claims validateAndGetClaims(String token) {
    Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    // convert secret string → Key object mà JJWT hiểu

    return Jwts.parserBuilder()
            .setSigningKey(key)    // dùng key này để verify chữ ký
            .build()
            .parseClaimsJws(token) // parse + verify, throw JwtException nếu sai
            .getBody();            // lấy payload ra → chứa userId, exp, ...
}
```

Chỉ **verify** token, không tạo — nghĩa là có auth service khác tạo token trước, gateway chỉ check hợp lệ không.

---

### `KafkaProducerConfig` (gateway)

```java
config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, true);
// thêm header chứa tên class vào mỗi message → consumer biết deserialize về class nào
```

Config để gateway **gửi** message lên `chat-topic`.

---

### `KafkaConsumerConfig` (gateway)

```java
config.put(ConsumerConfig.GROUP_ID_CONFIG, "gateway-delivery-group");
config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
// latest = chỉ đọc message mới, bỏ qua message cũ khi khởi động
config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "com.app.common_shared.dto.ChatMessageEvent");
// deserialize JSON về ChatMessageEvent
```

Config để gateway **nhận** message từ `delivery-topic`.

---

### `SecurityConfig`

```java
.requestMatchers("/ws/chat").permitAll() // cho qua tất cả
.anyRequest().denyAll()                  // chặn hết request khác
```

Tầng bảo vệ đầu tiên — Spring Security chặn mọi thứ không phải `/ws/chat`. Auth thật sự nằm trong `WsHandshakeAuthInterceptor`.

---

### `WebSocketConfig`

```java
registry.addHandler(chatWebSocketHandler, "/ws/chat")   // chat
        .addHandler(notiWebSocketHandler, "/ws/noti/*") // notification
        .addInterceptors(handshakeAuthInterceptor)       // cả 2 đều phải qua auth
        .setAllowedOriginPatterns("*");                  // cho phép mọi domain kết nối
```

Bảng điều phối — ai vào `/ws/chat` thì `chatWebSocketHandler` xử lý. Lưu ý: `chatWebSocketHandler` và `notiWebSocketHandler` đang inject cùng 1 bean — 2 endpoint đang dùng chung 1 handler.

---

### `WsHandshakeAuthInterceptor`

Chạy **trước khi** WebSocket kết nối, làm 3 việc:

**1. Lấy IP client:**

```java
String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
// nếu có proxy/load balancer thì IP thật nằm ở header này
// không thì lấy IP trực tiếp từ connection
```

**2. Rate limit theo IP:**

```java
String key = "ws:rate:" + clientIp;
Long count = redisTemplate.opsForValue().increment(key); // tăng đếm lên 1
if (count == 1) redisTemplate.expire(key, 1, TimeUnit.MINUTES); // lần đầu thì set TTL 1 phút
return count <= 10; // quá 10 lần/phút → chặn
```

Sau 1 phút Redis tự xóa key → count reset về 0 → user có thể connect lại.

**3. Extract và verify token:**

```java
// Cách 1: query param — ws://host/ws/chat?token=xxx
String query = request.getURI().getQuery(); // lấy phần sau dấu ?
for (String param : query.split("&")) {     // tách từng param
    if (param.startsWith("token="))
        return param.substring(6);          // cắt "token=" lấy giá trị
}

// Cách 2: Authorization header
String authHeader = request.getHeaders().getFirst("Authorization");
if (authHeader.startsWith("Bearer "))
    return authHeader.substring(7); // cắt "Bearer " lấy giá trị
```

```java
Claims claims = jwtUtil.validateAndGetClaims(token);
String userId = claims.getSubject(); // lấy userId từ payload JWT
attributes.put("userId", userId);    // lưu vào session → handler dùng sau
return true;                         // cho phép kết nối
```

---

### `WsMessagePayload`

```java
private String clientMessageId; // client tự sinh, để chống gửi trùng
private String conversationId;  // conversation nào
private String content;         // nội dung
private String type;            // TEXT / IMAGE / VIDEO
```

Format JSON client gửi lên WebSocket.

---

### `WsPayloadFilter`

```java
// 1. Check kích thước — chặn payload quá lớn
if (rawPayload.getBytes().length > 64 * 1024) → reject

// 2. Parse JSON → WsMessagePayload object

// 3. Validate field bắt buộc
if (isBlank(payload.getUserId()))          → reject  // ← bug: phải là conversationId
if (isBlank(payload.getContent()))         → reject
if (isBlank(payload.getClientMessageId())) → reject

// 4. Validate type — chỉ chấp nhận 3 loại
if (!type.matches("^(TEXT|IMAGE|VIDEO)$")) → reject

// 5. Sanitize — xóa HTML tags khỏi content, tránh XSS
payload.setContent(content.replaceAll("<[^>]*>", "").trim())
```

---

### `ChatWebSocketHandler`

Xử lý toàn bộ vòng đời WebSocket:

**Khi A connect:**

```java
String userId = session.getAttributes().get("userId"); // lấy từ interceptor đã lưu
activeSessions.put(userId, session); // lưu đường dây WebSocket của A theo userId
sendAck(session, "CONNECTED", null, "Kết nối thành công"); // báo cho A biết
```

**Khi A gửi message:**

```java
// 1. Validate payload
payload = payloadFilter.validateAndParse(message.getPayload(), userId);

// 2. Build event
ChatMessageEvent event = ChatMessageEvent.builder()
        .senderId(userId)                           // lấy từ session, không tin client
        .conversationId(payload.getConversationId())
        .content(payload.getContent())
        .timestamp(System.currentTimeMillis())
        .build();

// 3. Publish lên Kafka — key = conversationId để message cùng conversation vào cùng partition
kafkaTemplate.send("chat-topic", event.getConversationId(), event);

// 4. ACK ngay cho A — không chờ xử lý xong
sendAck(session, "SENT", clientMessageId, "Tin nhắn đang được xử lý");
```

**Khi cần push xuống B:**

```java
public boolean pushToUser(String userId, String jsonPayload) {
    WebSocketSession session = activeSessions.get(userId); // tìm đường dây của B
    if (session != null && session.isOpen()) {
        session.sendMessage(new TextMessage(jsonPayload)); // push xuống B
        return true;
    }
    return false; // B offline
}
```

**`sendAck`:**

```java
// Nếu có clientMessageId thì gửi kèm để client biết ACK này cho message nào
Map<String, String> ack = clientMessageId != null
    ? Map.of("status", status, "clientMessageId", clientMessageId, "message", message)
    : Map.of("status", status, "message", message);
session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ack)));
```

---

### `DeliveryEventListener`

```java
@KafkaListener(topics = "delivery-topic", groupId = "gateway-delivery-group")
public void onDelivery(ChatMessageEvent event) {
    String payload = objectMapper.writeValueAsString(event); // convert object → JSON string
    boolean delivered = chatWebSocketHandler.pushToUser(event.getTargetMemberId(), payload);
    // targetMemberId → tìm session → push xuống WebSocket
}
```

---

## `chat-consumer` — Service xử lý business logic

### Entities

**`Conversation`** — bảng `conversations`:

```java
private UUID conversationId; // PK tự sinh
private String type;         // "DIRECT" hoặc "GROUP"
private LocalDateTime createdAt;
```

**`ConversationMemberId`** — composite key (2 cột làm PK):

```java
@Embeddable // nhúng vào entity khác làm PK
private UUID conversationId;
private UUID userId;
// PK = (conversationId + userId) → 1 user chỉ vào 1 conversation 1 lần
```

**`ConversationMember`** — bảng `conversation_members`:

```java
@EmbeddedId
private ConversationMemberId id; // PK = (conversationId + userId)
private LocalDateTime joinedAt;
```

**`Message`** — bảng `messages`:

```java
private UUID messageId;      // PK tự sinh
private UUID conversationId; // thuộc conversation nào
private UUID senderId;       // ai gửi
private String content;
private String type;
private LocalDateTime createdAt;
```

**`User`** — bảng `users`:

```java
private UUID userId;
private String name;
private LocalDateTime createdAt;
```

---

### Repositories

**`MessageRepository`**:

```java
// Spring tự generate SQL từ tên method
List<Message> findByConversationIdOrderByCreatedAtDesc(UUID conversationId);
// → SELECT * FROM messages WHERE conversation_id = ? ORDER BY created_at DESC
```

**`ConversationMemberRepository`**:

```java
// Lấy tất cả members của 1 conversation
List<ConversationMember> findById_ConversationId(UUID conversationId);
// → SELECT * FROM conversation_members WHERE conversation_id = ?

// Check user có trong conversation không
boolean existsById_ConversationIdAndId_UserId(UUID conversationId, UUID userId);
// → SELECT EXISTS(... WHERE conversation_id = ? AND user_id = ?)
```

---

### `ChatService`

Làm 3 việc tuần tự:

**Bước 1 — Lưu message vào DB:**

```java
Message message = Message.builder()
        .conversationId(UUID.fromString(event.getConversationId()))
        .senderId(UUID.fromString(event.getSenderId()))
        .content(event.getContent())
        .createdAt(LocalDateTime.now())
        .build();
messageRepository.save(message);
// messageId được sinh ra ở đây → dùng ở bước 3
```

**Bước 2 — Query members:**

```java
memberIds = ConversationMemberRepository
        .findById_ConversationId(UUID.fromString(event.getConversationId()))
        .stream()
        .map(m -> m.getId().getUserId().toString()) // lấy userId từ composite key
        .collect(Collectors.toList());
// conversationId → [userA, userB, userC]
```

**Bước 3 — Fanout per member:**

```java
for (String memberId : memberIds) {
    if (memberId.equals(event.getSenderId())) continue; // bỏ qua A

    ChatMessageEvent enriched = ChatMessageEvent.builder()
            .targetMemberId(memberId)               // routing: push cho ai
            .messageId(message.getMessageId()...)   // thêm messageId từ DB
            .conversationId(event.getConversationId()) // giữ lại để client biết conversation nào
            ...build();

    kafkaTemplate.send("delivery-topic", memberId, enriched);
    // key = memberId → cùng user vào cùng partition → đảm bảo thứ tự
}
```

---

### `ConversationController`

```
GET /api/conversations/{conversationId}/messages?userId=xxx&limit=50
```

```java
// 1. Validate userId có đúng format UUID không
userUUID = UUID.fromString(userId); // throw nếu sai format → 400

// 2. Check quyền — user có trong conversation không
if (!conversationMemberRepository.existsById_ConversationIdAndId_UserId(...))
    return 403;

// 3. Query messages
messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId)
    .stream()
    .limit(limit)                                        // lấy tối đa 50
    .sorted(Comparator.comparing(Message::getCreatedAt)) // đảo lại cũ → mới
    .map(m -> MessageResponse.builder()...build())       // convert entity → DTO
    .collect(toList());
```

Query DESC trước rồi đảo lại ASC — lý do: lấy 50 message **mới nhất** (DESC) rồi hiển thị theo thứ tự **cũ → mới** (đảo lại ASC) như chat thông thường.

---

## Những điểm cần lưu ý

| # | Vấn đề | Mức độ |
|---|--------|--------|
| 1 | `WsPayloadFilter` đang check `userId` thay vì `conversationId` | Cần sửa |
| 2 | `senderName` đang được set = `userId` (UUID) thay vì tên thật | Cần fix sau |
| 3 | `chatWebSocketHandler` và `notiWebSocketHandler` dùng chung 1 bean | Cần tách |
| 4 | `activeSessions` chỉ hỗ trợ 1 session per user | Biết để scale sau |
| 5 | Không có Kafka error recovery / dead letter queue | Production concern |
| 6 | `ErrorCodeMessageRoute` và `BusinessException` chưa được dùng | Bổ sung sau |
| 7 | Thiếu endpoint tạo conversation (`POST /conversations`) | Bổ sung sau |