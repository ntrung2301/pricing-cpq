# Backlog — Epics & User Stories

Nhãn: `[core]` = thuộc lõi generic, không chứa kiến thức domain.
`[pack:automotive]` = thuộc domain pack automotive.
Ước lượng: S (<1 ngày) / M (1-2 ngày) / L (3+ ngày, nên tách nhỏ nếu rơi vào
1 sprint).

Story ở đây là khung ban đầu cho Sprint 1-3; chi tiết hoá thêm khi vào từng
sprint (`docs/sprints/sprint-N.md`). Story ID: `EPIC-N`.

---

## Epic 1 — Metamodel & Catalog [core]

### E1-01 [core] (L) Định nghĩa metamodel động
- **Mô tả**: Entity, Attribute, Relationship, Constraint là dữ liệu, không
  phải class Java cứng. Domain pack định nghĩa entity (ví dụ "Part") bằng
  config, không bằng code.
- **Acceptance criteria**:
  - Tạo được 1 entity mới (ví dụ "TestWidget") hoàn toàn qua config (file
    `pack.yaml`), không sửa code Java nào trong engine, hiển thị được qua
    API.
  - Test tích hợp xác nhận: thêm entity mới không cần thêm/sửa class Java
    nào (theo D-013, không còn ArchUnit quét keyword domain — xác minh
    bằng test tích hợp + review diff, xem `docs/adr/0005-modular-monolith-structure.md`).

### E1-02 [core] (M) Constraint-as-data, bao gồm constraint loại "compatibility"
- **Mô tả**: Constraint engine generic; "fitment theo xe" sẽ là 1 instance
  của constraint loại compatibility, định nghĩa trong domain pack.
- **Acceptance criteria**:
  - Core có constraint type "compatibility" generic (A tương thích B theo
    điều kiện X), không biết gì về "xe" hay "part".
  - Domain pack automotive định nghĩa fitment dùng constraint này, không
    thêm constraint type mới trong core.

### E1-03 [pack:automotive] (M) Entity automotive: Part, Vehicle, Fitment
- **Mô tả**: Định nghĩa entity Part (part number, OEM/aftermarket,
  supersession, kit/bundle, core charge, MOQ, đơn vị đóng gói) và Vehicle
  (make/model/year/engine) qua metamodel, thiết kế để map được ACES/PIES
  sau này.
- **Acceptance criteria**:
  - Import 1 part mẫu với đủ field trên, query lại đúng.
  - Supersession: part A superseded by part B, query ra được chuỗi
    supersession.
  - Field mapping có ghi chú tương ứng ACES/PIES (chưa cần map thật).

### E1-04 [core] (M) Catalog import framework (CSV/Excel)
- **Mô tả**: Core cung cấp khung import generic: tên cột trong file
  CSV/Excel phải **match đúng tên** attribute đã định nghĩa trong entity
  definition hiện hành (không cần UI mapping kéo-thả cho MVP — quyết định
  D-005). Domain pack chỉ cung cấp entity definition (tên attribute), core
  không biết tên cột cụ thể.
- **Acceptance criteria**:
  - Import CSV có cột khớp đúng tên attribute → tạo đúng entity instance.
  - Cột sai tên/thiếu cột bắt buộc → báo lỗi rõ theo dòng/cột, không crash
    import, không tự đoán mapping.

---

## Epic 2 — Configurator [core + pack]

### E2-01 [core] (M) Configurator engine: chọn entity + validate constraint
- **Mô tả**: Luồng chọn 1 entity (vd Part) và các entity liên quan, validate
  qua constraint engine (E1-02), generic.
- **Acceptance criteria**:
  - Chọn combination hợp lệ → pass; chọn combination vi phạm constraint →
    báo lỗi rõ, không phải lỗi 500.

### E2-02 [pack:automotive] (M) Luồng chọn xe → part tương thích
- **Mô tả**: UI/API chọn make/model/year/engine, lọc part theo fitment
  constraint.
- **Acceptance criteria**:
  - Chọn xe → chỉ hiện part có fitment khớp.
  - Part có supersession → gợi ý part thay thế khi part cũ bị chọn.

---

## Epic 3 — Pricing Engine [core + pack]

### E3-01 [core] (L) Price list + rule engine (tier/volume/thời hạn/chiết khấu)
- **Mô tả**: Rule lưu dạng dữ liệu (JSON/DSL — chốt bằng ADR), áp theo
  khách/tier/số lượng/thời hạn. Dùng BigDecimal.
- **Acceptance criteria**:
  - Thay đổi rule (vd volume break) qua config, không deploy lại code, kết
    quả tính giá thay đổi đúng.
  - Test: cùng input → luôn cùng output (deterministic).

### E3-02 [core] (M) Price waterfall giải thích được
- **Mô tả**: API tính giá trả về từng bước điều chỉnh (list price → tier
  adjustment → volume break → discount → final), không chỉ số cuối.
- **Acceptance criteria**:
  - Waterfall hiển thị đủ bước, tổng các bước = giá cuối (round-trip kiểm
    chứng được bằng test).

### E3-03 [core] (M) Margin guardrail
- **Mô tả**: Rule chặn/cảnh báo khi giá dưới margin tối thiểu (margin
  guardrail là khái niệm generic, ngưỡng là config).
- **Acceptance criteria**:
  - Giá dưới ngưỡng margin → quote line bị flag, cần phê duyệt (liên kết
    Epic 4 workflow).

### E3-04 [pack:automotive] (M) Phân tầng khách (OEM/Distributor/Dealer/Workshop) + giá hợp đồng riêng
- **Mô tả**: Tier khách cụ thể automotive, map vào rule engine generic của
  E3-01; giá riêng theo hợp đồng override tier mặc định.
- **Acceptance criteria**:
  - Cùng part, khách khác tier → giá khác đúng theo rule.
  - Khách có hợp đồng riêng → giá hợp đồng override tier, có ghi trong
    waterfall là "contract override".

---

## Epic 4 — Quote [core]

### E4-01 [core] (M) Tạo quote, line items, version
- **Acceptance criteria**: Tạo quote nhiều line item, sửa quote tạo version
  mới, xem lại version cũ không mất dữ liệu.

### E4-02 [core] (M) Workflow phê duyệt theo ngưỡng chiết khấu/margin
- **Acceptance criteria**: Vượt ngưỡng (config) → quote chuyển trạng thái
  "pending approval", Sales Manager duyệt/từ chối, log lý do.

### E4-03 [core] (M) Xuất PDF
- **Acceptance criteria**: Quote đã duyệt (hoặc không cần duyệt) xuất PDF có
  đủ line item, giá, tổng; PDF tạo lại từ cùng version → nội dung giống
  nhau (deterministic).

---

## Epic 5 — Admin & Security [core]

### E5-01 [core] (M) Quản lý rule/bảng giá qua UI
- **Acceptance criteria**: Pricing Admin tạo/sửa rule qua UI, có validate,
  không cần sửa file config tay bằng SSH.

### E5-02 [core] (M) RBAC (Sales, Sales Manager, Pricing Admin) + OIDC (Keycloak)
- **Acceptance criteria**: Mỗi role thấy đúng chức năng cho phép; đăng nhập
  qua Keycloak; test theo từng role.

### E5-03 [core] (S) Audit log
- **Acceptance criteria**: Mọi thay đổi rule/giá/user/approve có log: ai,
  khi nào, thay đổi gì, truy vấn lại được.

### E5-04 [core] (M) Import user/catalog qua UI (CSV/Excel)
- **Acceptance criteria**: Pricing Admin import catalog qua UI, thấy lỗi
  dòng cụ thể nếu sai.

---

## Epic 6 — Extension Points [core]

### E6-01 [core] (M) SPI cho custom pricing function
- **Acceptance criteria**: Thêm 1 hàm tính giá tùy chỉnh qua plugin, không
  sửa core, có test minh họa.

### E6-02 [core] (S) SPI cho constraint type mới
- **Acceptance criteria**: Thêm 1 constraint type mới qua plugin (không
  phải "compatibility"), không sửa core.

### E6-03 [core] (S) SPI cho connector import/export
- **Acceptance criteria**: Thêm connector export mới qua plugin, core không
  cần biết format cụ thể.

---

## Epic 7 — Domain Pack thứ hai tổng hợp (Validate kiến trúc) [pack:synthetic]

### E7-01 [pack:synthetic] (L) Dựng domain pack tổng hợp thứ hai (Sprint 4)
- **Mô tả**: Theo D-004 — domain pack thật (ngành cụ thể) sẽ được cấu hình
  khi có khách hàng thật, nên Sprint 4 **không** chọn một ngành thật
  (SaaS/thiết bị công nghiệp). Thay vào đó dựng 1 domain pack **tổng hợp,
  trung lập ngành** (entity/attribute/constraint/rule giả định, đơn giản
  hơn automotive) chỉ để chứng minh metamodel đủ generic.
- **Acceptance criteria**:
  - Dựng được luồng tối thiểu (catalog → configure → price → quote) cho
    domain tổng hợp chỉ bằng cấu hình + (nếu cần) extension point đã có,
    không thêm entity/constraint type mới vào core.
  - Nếu phải sửa lõi: ghi rõ chỗ nào, tại sao, vào `docs/decisions.md` —
    đây là tín hiệu ranh giới core/pack sai, không phải lỗi nhỏ.
  - Không ảnh hưởng/regress domain pack automotive đã có.

---

## Theo dõi

Backlog này map trực tiếp vào `docs/roadmap.md` (sprint nào làm story nào)
và `docs/sprints/sprint-N.md` (chi tiết task). Story mới hoặc đổi scope →
cập nhật cả 3 file.
