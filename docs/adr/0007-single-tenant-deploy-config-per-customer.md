# ADR-007: Mô hình deploy single-tenant per cluster và config-as-code theo khách

- **Trạng thái**: Accepted
- **Ngày**: 2026-11-12
- **Người quyết**: architect

## Ngữ cảnh

CLAUDE.md mục 8: single-tenant per deployment, không viết code tenant
isolation; cấu hình theo khách = config-as-code (domain pack + override),
không phải multi-tenant runtime switching. Cần chốt cụ thể: domain pack
nào được chọn cho 1 deployment, chọn lúc nào (build/release time hay
runtime), và chiến lược nâng cấp khi có nhiều cluster khách khác nhau
(vision.md tiêu chí thành công #5 yêu cầu viết thành văn bản).

## Lựa chọn đã xét

1. **Runtime multi-pack switching (1 image chứa mọi pack, chọn pack theo
   request/tenant header lúc runtime)** — vi phạm trực tiếp CLAUDE.md mục
   8 ("không phải multi-tenant runtime switching"); cũng kéo theo rủi ro
   leak domain keyword vào logic routing runtime nếu không cẩn thận.
   Loại.
2. **Mỗi khách build riêng 1 image chỉ chứa pack của khách đó (loại pack
   khác ra khỏi classpath lúc build)** — cô lập mạnh nhất, nhưng phức tạp
   hoá CI/CD (build matrix theo khách) không cần thiết ở quy mô hiện tại
   (1-2 pack, MVP, chưa nhiều khách thật — Q-003 "chưa có khách"). Có thể
   cân nhắc lại khi có nhiều khách thật.
3. **1 image chứa tất cả pack đã build (cùng artifact), chọn pack active
   bằng config lúc deploy (Spring profile/property
   `cpq.active-packs=automotive`, không đổi được lúc runtime sau khi app
   đã start — chỉ đổi qua redeploy)** — cân bằng: build 1 lần dùng nhiều
   khách, nhưng việc "chọn pack" vẫn là **cấu hình tại thời điểm
   deploy/release**, không phải runtime switching theo request — đúng
   tinh thần mục 8. Chọn.

## Quyết định

Mỗi khách = 1 Helm release riêng (namespace hoặc cluster riêng), mỗi
release có `values-<customer>.yaml` riêng khai báo: image tag, Postgres
connection, Keycloak realm riêng, và `cpq.active-packs` (property app đọc
lúc khởi động để quyết định pack nào trong artifact được
`PackLoaderService` (ADR-001) nạp — pack không chọn sẽ không nạp dữ liệu,
không ảnh hưởng runtime). 1 Helm chart chung (`charts/cpq`), version hoá
độc lập với version app; mỗi khách pin 1 chart version + app image version
trong `values-<customer>.yaml` riêng (lưu trong repo/values riêng theo
khách, có thể tách repo "fleet config" sau nếu số khách tăng).

**Chiến lược nâng cấp nhiều cluster** (trả lời tiêu chí #5 vision.md):
nâng chart/app version theo kiểu GitOps per-customer — mỗi khách có version
pin riêng; nâng cấp 1 khách = PR đổi version trong
`values-<customer>.yaml` của khách đó → pipeline deploy **chỉ** cluster/
namespace của khách đó. Không có "nâng cấp đồng loạt mọi khách cùng lúc"
ở MVP — tránh rủi ro 1 version lỗi ảnh hưởng tất cả khách cùng lúc; khách
đầu tiên dùng để validate version mới trước khi nhân rộng values sang
khách khác (copy giá trị version đã validate). Flyway migration chạy như
Helm hook (`pre-upgrade` Job) riêng theo từng release, không migration
chung.

## Hệ quả

- Tác động tới: S1-T6 (mỗi cluster 1 Keycloak realm), devops Sprint 4
  (thêm config domain tổng hợp vào `config/packs/synthetic/` — theo
  ADR-005/D-013 không phải module Gradle riêng — build vẫn 1 artifact),
  roadmap Sprint 4 (deploy domain tổng hợp cạnh automotive trong cùng
  image, chọn qua config, không build riêng).
- Rủi ro đã biết: artifact chứa tất cả pack (kể cả pack khách không dùng)
  — chấp nhận ở quy mô hiện tại (1-2 pack); nếu số pack tăng nhiều, cân
  nhắc lại phương án build-per-pack (lựa chọn 2) và ghi ADR mới.
- Ảnh hưởng ranh giới core/pack: **Gián tiếp** — việc pack nào "active"
  là cấu hình, không phải code; core (`PackLoaderService`) không cần biết
  pack nào sẽ được chọn lúc build core, giữ đúng nguyên tắc pack = dữ liệu/
  config, core không phụ thuộc ngược vào pack cụ thể.
