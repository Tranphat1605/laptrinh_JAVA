## **Phân chia vai trò (4 người)**

### **1. Thành viên A – Thiết kế CSDL & Tầng truy xuất dữ liệu (Backend/Data Layer)**

**Nhiệm vụ chính:**

* Thiết kế lược đồ CSDL (các bảng `problems`, `testcases`, `sample_codes`, `checkers`, `ai_logs`...).
* Cài đặt kết nối CSDL (JDBC), viết các lớp DAO để thực hiện thêm/sửa/xóa/truy vấn.
* Xây dựng các class Model (POJO) ánh xạ dữ liệu.
* Chuẩn bị dữ liệu mẫu cho nhóm test.
* Quản lý file cấu hình (CSDL, API key...).

**Kỹ năng cần:** Java core, JDBC, thiết kế CSDL quan hệ, SQL.

**Giao tiếp với nhóm:** Cung cấp interface (method) cho các thành viên khác gọi để lưu/lấy dữ liệu. Sớm hoàn thiện các hàm cơ bản để các nhóm khác không bị chặn.

---

### **2. Thành viên B – Tích hợp AI (AI Service)**

**Nhiệm vụ chính:**

* Kết nối API của AI (OpenAI, Gemini...), quản lý API key, xử lý timeout, retry.
* Thiết kế prompt cho từng tác vụ:
  * Phân tích đề bài (từ text hoặc ảnh) → trích xuất yêu cầu.
  * Sinh bộ test case (input/output) theo định dạng cấu trúc (JSON).
  * Sinh checker (nếu cần).
  * Tự động sinh code mẫu AC/WA/TLE khi người dùng không cung cấp.
* Parse phản hồi JSON từ AI thành các object của hệ thống (Testcase, Checker...).
* Nếu đề bài là ảnh: sử dụng OCR (Tess4J) hoặc gửi thẳng đến model multimodal để nhận text.
* Cung cấp service class (ví dụ `AIService`) cho GUI gọi.

**Kỹ năng cần:** Gọi REST API (HttpClient), xử lý JSON (Jackson/Gson), prompt engineering cơ bản, kiên nhẫn với việc thử và tinh chỉnh prompt.

**Giao tiếp:** Nhận yêu cầu phân tích đề từ GUI (thông qua controller chung) và trả về danh sách test case, checker, code mẫu đã parse. Lưu kết quả vào DB thông qua DAO của A.

---

### **3. Thành viên C – Module chấm & kiểm tra code (Sandbox & Evaluation)**

**Nhiệm vụ chính:**

* Xây dựng môi trường thực thi an toàn cho code mẫu:
  * Dùng `ProcessBuilder` để biên dịch + chạy code (Java/Python/C++... tùy chọn).
  * Giới hạn thời gian chạy (timeout), bộ nhớ (nếu làm trên Linux).
  * Bắt output, lỗi, thoát đúng cách.
* Phát triển logic đánh giá test case dựa trên code mẫu:
  * Với mỗi code mẫu (AC, WA, TLE), chạy qua toàn bộ test case, ghi nhận pass/fail.
  * Từ kết quả đó, đưa ra nhận xét: test case có sai không? Có yếu không? (ví dụ code WA lại pass → test case yếu).
* Hỗ trợ chạy checker: Nếu có checker, thực hiện so sánh output bằng checker thay vì so khớp chính xác.
* Xây dựng service `EvaluationService` để GUI gọi.

**Kỹ năng cần:** Làm việc với Process, luồng I/O, quản lý tiến trình, xử lý đa luồng để không treo giao diện, hiểu biết sơ lược về các ngôn ngữ lập trình cần hỗ trợ chấm.

**Giao tiếp:** Lấy test case từ DB (qua A) và nhận code mẫu từ người dùng hoặc do AI sinh ra (từ B). Trả về báo cáo chi tiết cho GUI.

---

### **4. Thành viên D – Giao diện người dùng & Tích hợp hệ thống (GUI + Controller)**

**Nhiệm vụ chính:**

* Thiết kế và lập trình toàn bộ giao diện (JavaFX hoặc Swing) bao gồm:
  * Form nhập đề bài (text box + nút chọn file ảnh).
  * Bảng hiển thị danh sách test case (có thể sửa/xóa).
  * Vùng nhập code mẫu (chọn loại AC/WA/TLE).
  * Nút “Phân tích bằng AI” để gọi AI service.
  * Hiển thị kết quả đánh giá (báo cáo dạng cây/bảng).
* Viết lớp Controller điều phối luồng:
  * Khi người dùng nhấn nút, controller gọi `AIService`, `EvaluationService`, `DAO` một cách tuần tự/đa luồng.
  * Đảm bảo cập nhật giao diện an toàn từ luồng phụ (Platform.runLater...).
* Xử lý sự kiện, validate dữ liệu nhập, hiển thị thông báo lỗi.
* Thiết kế trải nghiệm người dùng mượt mà (loading indicator khi gọi AI/chạy code).

**Kỹ năng cần:** JavaFX/Swing, MVC, đa luồng trong GUI, khả năng ráp nối các module.

**Giao tiếp:** Là người dùng trực tiếp service của B và C, dùng DAO của A để load/save dữ liệu. Cần thống nhất interface (method signature) sớm với các thành viên khác.

---

## **Cách phối hợp và mốc thời gian đề xuất**

1. **Tuần 1:** Cả nhóm cùng thống nhất kiến trúc, thiết kế CSDL (A chủ trì), chọn giao thức giao tiếp giữa các module (interface rõ ràng). A bắt đầu code DAO, B tạo tài khoản AI và test prompt thô, C dựng sandbox đơn giản, D vẽ prototype GUI.
2. **Tuần 2-3:** A hoàn thiện DAO, B hoàn thành parse AI response ra object, C hoàn thành chạy được code và so sánh output, D code GUI phần nhập liệu cơ bản.
3. **Tuần 4:** Tích hợp dây chuyền nhỏ: GUI gọi AI → nhận test case → lưu DB → hiển thị. C kiểm thử sandbox với test case giả.
4. **Tuần 5:** Tích hợp module đánh giá: GUI nhập code mẫu → chạy qua test case → hiện báo cáo.
5. **Tuần 6:** Kiểm thử toàn bộ, hoàn thiện giao diện, xử lý ngoại lệ, làm tài liệu.

Phân công này cho phép A, B, C làm việc song song gần như độc lập sau khi thống nhất interface, còn D sẽ bắt đầu sau nhưng có thể dùng dữ liệu giả để thiết kế giao diện trước khi có service thật. Nếu có thành viên mạnh về full-stack, có thể linh hoạt đổi vai trò. Chúc nhóm bạn triển khai suôn sẻ!


Tables DB, run on SSMS

-- 1. Xóa các bảng cũ nếu tồn tại (theo thứ tự để không bị lỗi khóa ngoại)
DROP TABLE IF EXISTS EvaluationResult;
DROP TABLE IF EXISTS Submission;
DROP TABLE IF EXISTS TestCase;
DROP TABLE IF EXISTS SampleCode;
DROP TABLE IF EXISTS Checker;
DROP TABLE IF EXISTS Problem;

-- 2. Tạo mới toàn bộ bảng
CREATE TABLE Problem (
  id INT IDENTITY(1,1) PRIMARY KEY,
  title NVARCHAR(512) NOT NULL,
  content NVARCHAR(MAX),
  timeLimitMs INT,
  memoryLimitMb INT,
  source NVARCHAR(256)
);

CREATE TABLE TestCase (
  id INT IDENTITY(1,1) PRIMARY KEY,
  problemId INT NOT NULL,
  inputData NVARCHAR(MAX),
  expectedOutput NVARCHAR(MAX),
  isHidden BIT DEFAULT 0,
  strengthStatus NVARCHAR(64),
  CONSTRAINT FK_TestCase_Problem FOREIGN KEY (problemId) REFERENCES Problem(id) ON DELETE CASCADE
);

CREATE TABLE Submission (
  id INT IDENTITY(1,1) PRIMARY KEY,
  problemId INT NOT NULL,
  sourceCode NVARCHAR(MAX),
  language NVARCHAR(64),
  finalStatus NVARCHAR(32),
  isReferenceCode BIT DEFAULT 0,
  CONSTRAINT FK_Submission_Problem FOREIGN KEY (problemId) REFERENCES Problem(id) ON DELETE CASCADE
);

CREATE TABLE EvaluationResult (
  id INT IDENTITY(1,1) PRIMARY KEY,
  submissionId INT NOT NULL,
  testcaseId INT NOT NULL,
  status NVARCHAR(32),
  actualOutput NVARCHAR(MAX),
  executionTimeMs BIGINT,
  CONSTRAINT FK_Eval_Submission FOREIGN KEY (submissionId) REFERENCES Submission(id) ON DELETE CASCADE,
  CONSTRAINT FK_Eval_TestCase FOREIGN KEY (testcaseId) REFERENCES TestCase(id) NO ACTION
);

CREATE TABLE SampleCode (
  id INT IDENTITY(1,1) PRIMARY KEY,
  problemId INT,
  code NVARCHAR(MAX),
  language NVARCHAR(64),
  expectedVerdict NVARCHAR(16),
  CONSTRAINT FK_SampleCode_Problem FOREIGN KEY (problemId) REFERENCES Problem(id) ON DELETE CASCADE
);

CREATE TABLE Checker (
  id INT IDENTITY(1,1) PRIMARY KEY,
  problemId INT,
  code NVARCHAR(MAX),
  language NVARCHAR(64),
  CONSTRAINT FK_Checker_Problem FOREIGN KEY (problemId) REFERENCES Problem(id) ON DELETE CASCADE
);


