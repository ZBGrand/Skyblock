# OneBlockStorageBridge

Plugin cầu nối cho kho cá nhân của **OneBlockCore**: thêm lệnh xem số dư, rút, thêm block và cung cấp placeholder cho PlaceholderAPI.

## Yêu cầu

- Java 21
- Paper 1.20+ (compile với Paper API 1.21.4)
- **OneBlockCore** (bắt buộc, `depend`)
- **PlaceholderAPI** (tùy chọn, `softdepend`; không có thì chỉ mất placeholder)

## Build

1. Đặt `OneBlockCore.jar` vào thư mục `libs/` (chỉ để compile, không shade).
2. Chạy:

   ```bash
   mvn clean package
   ```

3. Lấy file jar trong `target/` bỏ vào thư mục `plugins/` của server.

> Nếu compile lỗi ở `getPrices().getPrice(...)` hoặc các chỗ gọi API OneBlockCore, đối chiếu lại chữ ký thực tế trong source OneBlockCore (tài liệu `storage-api.md` ghi ngày 18/10/2026).

## Lệnh

Lệnh gốc: `/obs` (alias của `/obstorage`).

| Lệnh | Quyền | Mô tả |
|---|---|---|
| `/obs balance [block\|all] [người chơi]` | `obstorage.use` | Xem tổng/dung lượng/giữ chỗ và top 10 loại block. Truyền block để xem riêng loại đó. Xem người khác cần `obstorage.admin`. |
| `/obs withdraw <block> [số lần]` | `obstorage.use` | Rút block vào túi. Mỗi lần rút tối đa theo giới hạn kho (≤ 64); `[số lần]` gọi lặp tối đa 64 lần. |
| `/obs add <người chơi> <block> <số lượng>` | `obstorage.admin` | Thêm block vào kho (reserve → commit, có kiểm tra dung lượng). |
| `/obs reload` | `obstorage.admin` | Tải lại `config.yml`. |

Alias phụ: `bal` = `balance`, `rut` = `withdraw`.

### Quyền

| Quyền | Mặc định |
|---|---|
| `obstorage.use` | Mọi người chơi |
| `obstorage.admin` | OP |

### Lưu ý hành vi

- **Rút lặp:** dừng ngay khi kết quả không phải `SUCCESS` (hết hàng, túi đầy, lỗi...). Với `AMBIGUOUS`, plugin chỉ cảnh báo và ghi log, **không tự retry** — admin cần đối soát journal.
- **Thêm block:** chỉ nhận block nằm trong danh mục cho phép của kho. Nếu `reserve` trả `null` thì có thể do kho đầy, chưa sẵn sàng, block không hợp lệ hoặc lỗi lưu trữ (API không phân biệt được). Mỗi lần thêm thành công đều được ghi vào console.
- Người chơi nhận tên khi `add`/`balance` phải đang online hoặc nằm trong cache tên của server.

## PlaceholderAPI

Identifier: `obstorage`

| Placeholder | Kết quả |
|---|---|
| `%obstorage_ready%` | `true`/`false` |
| `%obstorage_total%` | Tổng block trong kho |
| `%obstorage_total_formatted%` | Như trên, có dấu phẩy ngăn cách (`12,345`) |
| `%obstorage_capacity%` | Dung lượng tối đa |
| `%obstorage_capacity_formatted%` | Như trên, có dấu phẩy |
| `%obstorage_pending%` | Lượng đang giữ chỗ cho các lượt đào chờ xử lý |
| `%obstorage_free%` | `capacity - total - pending` (≥ 0). **Chỉ để hiển thị**, không dùng để quyết định có thêm được hàng hay không |
| `%obstorage_used_percent%` | % dung lượng đã dùng, gồm giữ chỗ (1 chữ số thập phân) |
| `%obstorage_types%` | Số loại block đang có |
| `%obstorage_balance_<material>%` | Số lượng một loại, ví dụ `%obstorage_balance_stone%` |
| `%obstorage_balance_<material>_formatted%` | Như trên, có dấu phẩy |
| `%obstorage_price_<material>%` | Giá bán một block |
| `%obstorage_sell_enabled%` | `true`/`false` — bán có được bật không |
| `%obstorage_filter%` | Chữ trạng thái lọc kho (mặc định `Bật`/`Tắt`, chỉnh trong config) |
| `%obstorage_filter_boolean%` | `true`/`false` |
| `%obstorage_max_withdraw%` | Giới hạn rút mỗi lần theo config kho |

- `<material>` không phân biệt hoa thường, dùng tên Bukkit Material (`stone`, `oak_log`, `deepslate`...). Tên sai sẽ để nguyên placeholder để dễ phát hiện.
- Khi OneBlockCore/kho chưa sẵn sàng, placeholder trả về giá trị `placeholders.not-ready` (mặc định `N/A`).

### Cache

Số dư được cache ngắn hạn theo người chơi (`placeholders.cache-ms`, mặc định 1000 ms) để scoreboard/tab refresh liên tục không làm nặng main thread, vì backend OneBlockCore ghi YAML đồng bộ. Cache tự xóa khi người chơi rút hoặc được thêm block bằng lệnh của plugin này. Thay đổi từ plugin khác có thể hiện chậm tối đa bằng thời gian cache.

## Cấu hình (`config.yml`)

```yaml
placeholders:
  not-ready: "N/A"     # giá trị khi kho chưa sẵn sàng
  filter-on: "Bật"     # %obstorage_filter%
  filter-off: "Tắt"
  cache-ms: 1000       # thời gian cache số dư (ms)

messages:
  prefix: "&8[&bKho&8] &7"
  # ... toàn bộ tin nhắn, dùng mã màu & và biến {ten}
```

Chỉnh xong dùng `/obs reload`.

## Cấu trúc project

```
OneBlockStorageBridge/
├── libs/                       # đặt OneBlockCore.jar ở đây
├── pom.xml
└── src/main
    ├── java/com/luckyvn/oneblockstorage/
    │   ├── OneBlockStorageBridge.java   # main, lấy StorageFeature, gửi tin nhắn
    │   ├── ObsCommand.java              # lệnh + tab complete
    │   └── StoragePlaceholders.java     # PlaceholderAPI expansion
    └── resources/
        ├── plugin.yml
        └── config.yml
```

## Nguyên tắc tích hợp đã áp dụng

Theo `storage-api.md`:

- Lấy lại `StorageFeature` và kiểm tra `isReady()` mỗi lần dùng, không cache lâu dài `getService()`/`getConfig()`/`getPrices()`.
- Rút bằng `withdraw()` (có journal), thêm bằng `reserve()` → `commit()`; **không** dùng `addBalance()` hay `debitBalance()` trực tiếp.
- Mọi thao tác Player/inventory chạy trên main thread (lệnh Bukkit mặc định chạy ở đây).
- Không tự retry thao tác tài sản khi kết quả `AMBIGUOUS`.
