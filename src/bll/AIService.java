package bll;

import entity.Problem;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;

/**
 * AIService sử dụng Groq API (miễn phí, ~14,400 req/ngày).
 * Đăng ký API key miễn phí tại: https://console.groq.com
 *
 * Model mặc định: llama-3.3-70b-versatile (text)
 * Model vision:   meta-llama/llama-4-scout-17b-16e-instruct (text + ảnh)
 */
public class AIService {
    private final String apiKey;
    private final HttpClient client;
    private final Gson gson;

    // Groq API endpoint (OpenAI-compatible)
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    // private static final String GROQ_API_URL = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
    private static final int MAX_RETRIES = 3;

    // Model text-only: nhanh, mạnh, miễn phí
    private static final String TEXT_MODEL = "llama-3.3-70b-versatile";
    // private static final String TEXT_MODEL = "gemini-1.5-pro-latest";
    // Model vision: hỗ trợ phân tích ảnh
    private static final String VISION_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct";
    // private static final String VISION_MODEL = "gemini-1.5-pro-latest";
    public AIService(String apiKey) {
        this.apiKey = apiKey;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.gson = new Gson();
    }

    /**
     * Phân tích đề bài từ Text hoặc Ảnh
     */
    public Problem analyzeProblem(String text, File imageFile) throws Exception {
        String prompt = "Bạn là một siêu AI đánh máy (OCR) chuyên nghiệp được thiết kế riêng cho nền tảng thi đấu lập trình (IOI, ICPC). " +
                "Nhiệm vụ tối thượng của bạn là ĐỌC CHÍNH XÁC VÀ TRÍCH XUẤT Y XÌ ĐÚC (1:1) NHỮNG GÌ BẠN NHÌN THẤY thành dữ liệu văn bản. "+
                "TUYỆT ĐỐI KHÔNG TÓM TẮT, KHÔNG HƯ CẤU, KHÔNG GIẢI THÍCH.\n" +
                "Hãy trích xuất và trả về dữ liệu dưới định dạng JSON với các trường bắt buộc sau:\n" +
                "1. \"title\": Tên bài (nếu không có trong đề, hãy tự đặt ngắn gọn).\n" +
                "2. \"timeLimitMs\": Giới hạn thời gian tính bằng số milliseconds (Ví dụ 1 giây = 1000). Nếu không tìm thấy, trả về 1000.\n" +
                "3. \"memoryLimitMb\": Giới hạn bộ nhớ tính bằng số Megabytes. Nếu không tìm thấy, trả về 256.\n" +
                "4. \"content\": ĐÂY LÀ PHẦN QUAN TRỌNG NHẤT, BẮT BUỘC CHÉP LẠI Y NGUYÊN VĂN 100% TỪ GỐC ĐẾN NGỌN BAO GỒM:\n" +
                "   - Giữ nguyên toàn bộ cấu trúc đoạn, bảo toàn tuyệt đối dấu xuống dòng bằng ký tự `\\n` hoặc `\\n\\n`.\n" +
                "   - BĂT BUỘC xuống dòng rõ ràng để tách biệt các cụm: Đề bài, Giới hạn (Constraints), Đầu vào (Input), Đầu ra (Output).\n" +
                "   - BẢO TOÀN NGUYÊN VẸN CÁC KÝ HIỆU TOÁN HỌC, CÁC BIỂU THỨC (ví dụ: N <= 10^5, A_i, mảng 2 chiều).\n" +
                "   - PHẢI SAO CHÉP CHÍNH XÁC TỪNG CON SỐ, TỪNG KHOẢNG TRẮNG, TỪNG DẤU XUỐNG DÒNG trong phần BẢNG VÍ DỤ TESTCASE (Sample Input / Sample Output) để máy chấm không bị lỗi form.\n" +
                "Chỉ trả về trực tiếp DỮ LIỆU JSON HỢP LỆ, KHÔNG CHỨA BẤT KỲ VĂN BẢN TRÌNH BÀY NÀO KHÁC (no markdown, no extra text).";

        String imageBase64 = null;
        if (imageFile != null && imageFile.exists()) {
            byte[] fileContent = Files.readAllBytes(imageFile.toPath());
            imageBase64 = Base64.getEncoder().encodeToString(fileContent);
        }

        String model = (imageBase64 != null) ? VISION_MODEL : TEXT_MODEL;
        String payload = buildPayload(model, prompt, text, imageBase64);
        String responseMessage = sendRequestWithRetry(payload);

        Problem p = new Problem();
        p.setTimeLimitMs(1000);
        p.setMemoryLimitMb(256);
        p.setSource("AI Generated");

        // Lưu lại dữ liệu gốc tương ứng với cách gửi (Text hay Ảnh)
        if (imageFile == null || !imageFile.exists()) {
            p.setOriginalRawText(text); // Phân tích từ Text: Lưu nguyên vẹn text gốc của người dùng
        } else {
            p.setOriginalRawText(null); // Phân tích từ Ảnh: CSDL sẽ tự động lấy content do AI phân tích cấu trúc
            if (imageBase64 != null) {
                p.setOriginalImageBase64(imageBase64);
            }
        }

        try {
            // CẢI TIẾN: Dùng Regex để tìm khối JSON thực sự trong đống text AI trả về
            String jsonStr = responseMessage;
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{.*\\}", java.util.regex.Pattern.DOTALL).matcher(responseMessage);
            if (matcher.find()) {
                jsonStr = matcher.group();
            }

            JsonObject jsonOutput = this.gson.fromJson(jsonStr, JsonObject.class);
            
            if (jsonOutput.has("title")) {
                p.setTitle(jsonOutput.get("title").getAsString());
            } else {
                p.setTitle("Bài tập sinh bởi AI (" + System.currentTimeMillis() % 10000 + ")");
            }

            if (jsonOutput.has("timeLimitMs")) p.setTimeLimitMs(jsonOutput.get("timeLimitMs").getAsInt());
            if (jsonOutput.has("memoryLimitMb")) p.setMemoryLimitMb(jsonOutput.get("memoryLimitMb").getAsInt());
            
            if (jsonOutput.has("content")) {
                p.setContent(jsonOutput.get("content").getAsString());
            } else {
                p.setContent(responseMessage); // Fallback dùng toàn bộ text nếu không parse được trường content
            }

        } catch (Exception e) {
            System.err.println("AI không trả về JSON hợp lệ: " + e.getMessage());
            // FALLBACK TỐI THƯỢNG: Đảm bảo không bao giờ bị NULL khi vào DB
            p.setTitle("Bài tập AI " + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()));
            p.setContent(responseMessage);
        }
        return p;
    }

    /**
     * Sinh code C++ Generator sử dụng testlib.h
     */
    public String generateGeneratorCode(Problem problem) throws Exception {
        String systemPrompt = "Bạn là chuyên gia lập trình thi đấu (ICPC/IOI). " +
                "Nhiệm vụ của bạn là viết code C++ hoàn chỉnh sử dụng testlib.h để sinh dữ liệu test. " +
                "Chỉ trả về code C++ thuần túy, không markdown, không giải thích.";
        String userPrompt =
                "=== ĐỀ BÀI ===\n" + problem.toString() + "\n\n" +
                "=== YÊU CẦU ===\n" +
                "Viết code C++ generator sử dụng testlib.h để tự động sinh dữ liệu input cho bài toán trên.\n" +
                "Bắt buộc:\n" +
                "1. Luôn khai báo đầy đủ `#include <iostream>` và `using namespace std;` ở đầu file.\n" +
                "2. Dòng đầu tiên trong main BẮT BUỘC là: `registerGen(argc, argv, 1);`\n" +
                "3. Hỗ trợ seed từ `argv[1]`.\n" +
                "4. In ra ĐÚNG ĐỊNH DẠNG Input mà đề bài yêu cầu, KHÔNG BAO GIỜ in text thừa (như \"N=\", \"M=\"). Chỉ in ra những con số phân cách bằng khoảng trắng hoặc xuống dòng.\n" +
                "5. LƯU Ý QUAN TRỌNG VỀ testlib.h: ĐỂ SINH SỐ NGẪU NHIÊN, BẮT BUỘC DÙNG `rnd.next(min, max)` HOẶC `rnd.next(max)`.\n" +
                "   - TUYỆT ĐỐI KHÔNG sử dụng ký hiệu khoa học như `1e6`, `1e9` bên trong hàm `rnd.next()`. Thay vào đó hãy viết rõ số nguyên (VD: `1000000`) hoặc ép kiểu rõ ràng `(int)1e6` để tránh lỗi 'call of overloaded next is ambiguous'.\n" +
                "   - Đảm bảo kiểu dữ liệu của tham số min và max phải đồng nhất (cùng là int hoặc cùng là long long).\n" +
                "   - KHÔNG BAO GIỜ được dùng các hàm đọc dữ liệu như `inf.readInt()` vì Generator là SINH dữ liệu (rnd).\n"+
                "6. CHÚ Ý QUAN TRỌNG VỀ ĐỘ MẠNH (STRONG TESTCASES):\n" +
                "   - Generator cần lấy arg từ `argv[2]` (nếu có) làm tham số để quyết định mode sinh testcase.\n" +
                "   - Nếu mode là 'edge': Sinh các trường hợp biên, giá trị tối thiểu, tối đa (VD: N=1, mảng rỗng, mảng toàn số 0).\n" +
                "   - Nếu mode là 'max': BẮT BUỘC sinh Input có N, M, hoặc biến Vòng lặp lớn nhất (VD: sát 10^5, 10^9) theo đúng giới hạn đề bài để gây TLE.\n" +
                "   - TUYỆT ĐỐI KHÔNG sử dụng hàm `quit()` với 1 tham số như `quit(\"lỗi\")`. Thay vào đó, dùng `quitf(_fail, \"Lý do lỗi\");` nếu cần thiết.\n" +
                "\nChỉ trả về trực tiếp mã C++ thuần túy, tuyệt đối KHÔNG có markdown, KHÔNG thêm giải thích xung quanh.";
        
        String model = (problem.getOriginalImageBase64() != null) ? VISION_MODEL : TEXT_MODEL;
        String payload = buildPayloadWithSystem(model, systemPrompt, userPrompt, problem.getOriginalImageBase64());
        return sendRequestWithRetry(payload);
    }

    /**
     * Sinh checker C++ (nếu bài toán có nhiều cách giải đúng)
     */
    public String generateChecker(Problem problem) throws Exception {
        String systemPrompt = "Bạn là chuyên gia lập trình thi đấu (ICPC/IOI). " +
                "Nhiệm vụ là viết checker testlib.h để so khớp đáp án. Chỉ trả về code C++ thuần túy.";
        String userPrompt =
                "=== ĐỀ BÀI ===\n" + problem.toString() + "\n\n" +
                "=== YÊU CẦU ===\n" +
                "Viết code C++ checker sử dụng testlib.h cho bài toán trên.\n" +
                "- BẮT BUỘC có `#include <bits/stdc++.h>` trước tiên, rồi mới `#include \"testlib.h\"` để tránh lỗi thiếu khai báo khi biên dịch.\n" +
                "- BẮT BUỘC dòng đầu tiên trong hàm main() là: `registerTestlibCmd(argc, argv);` và kết thúc bằng `return 0;`.\n" +
                "- BẮT BUỘC toàn bộ code phải nằm trong một khối duy nhất, hàm `int main(int argc, char* argv[])` phải nằm ở ngoài cùng, tuyệt đối KHÔNG lồng trong hàm khác. Cẩn thận các dấu đóng mở ngoặc `{ }`.\n" +
                "- BẮT BUỘC sử dụng: `inf.read...` để đọc dữ liệu Input.\n" +
                "- BẮT BUỘC sử dụng: `ans.read...` để đọc biểu thức đáp án chuẩn (Jury).\n" +
                "- BẮT BUỘC sử dụng: `ouf.read...` để đọc đầu ra của Bài thí sinh nộp.\n" +
                "- CHÚ Ý CÚ PHÁP TESTLIB.H: Hàm đọc sẽ trực tiếp trả về giá trị (VD: `long long a = ans.readLong();` hoặc `int b = ouf.readInt();`). TUYỆT ĐỐI KHÔNG truyền tham chiếu vào hàm kiểu `ans.readLong(a)` vì điều này sẽ gây lỗi biên dịch nghiêm trọng.\n" +
                "- LƯU Ý VỀ TÊN BIẾN: Khai báo tên biến cục bộ là `jury_ans`, `contestant_ans`... TUYỆT ĐỐI KHÔNG đặt tên biến là `ans` hay `ouf` hay `inf` (VD: TRÁNH `int ans = ans.readInt();`) vì nó sẽ ghi đè luồng stream của thư viện testlib.h\n" +
                "- TUYỆT ĐỐI KHÔNG dung std::cin, std::cout, scanf, hay printf trong bất kì đâu.\n" +
                "- Chỉ gọi `quitf(_ok, \"Đúng\");` nếu thí sinh trả lời chính xác và `quitf(_wa, \"Sai\");` nếu sai kết quả.\n" +
                "- LƯU Ý MÃ TRẠNG THÁI: BẮT BUỘC dùng dấu gạch dưới như `_wa`, `_ok`, `_pe`. TUYỆT ĐỐI KHÔNG dùng `wa`, `ok` vì sẽ lỗi dịch code.\n" +
                "Chỉ trả về trực tiếp mã C++ thuần túy, tuyệt đối KHÔNG có markdown, KHÔNG thêm giải thích xung quanh.";
        
        String model = (problem.getOriginalImageBase64() != null) ? VISION_MODEL : TEXT_MODEL;
        String payload = buildPayloadWithSystem(model, systemPrompt, userPrompt, problem.getOriginalImageBase64());
        return sendRequestWithRetry(payload);
    }

    /**
     * Hỏi AI xem bài toán này có cần Custom Checker không.
     */
    public boolean checkIfCheckerIsNeeded(Problem problem) throws Exception {
        String prompt = "Bạn là chuyên gia thẩm định đề bài lập trình thi đấu.\n" +
                "Nhiệm vụ: Hãy phân tích đề bài sau và quyết định xem có cần viết 'Custom Checker' (C++ testlib.h) để chấm điểm hay không.\n\n" +
                "--- TRƯỜNG HỢP CẦN CHECKER (YES) ---\n" +
                "1. Bài toán có nhiều kết quả đúng (VD: 'In ra một cách bất kỳ', 'In ra bất kỳ bộ số nào thỏa mãn...').\n" +
                "2. Bài toán yêu cầu độ chính xác số thực (VD: 'Sai số không quá 10^-6').\n" +
                "3. Thứ tự các phần tử trong Output không quan trọng.\n\n" +
                "--- TRƯỜNG HỢP KHÔNG CẦN CHECKER (NO) ---\n" +
                "1. Kết quả là duy nhất (VD: Tính tổng, đếm số cách, tìm giá trị Min/Max cụ thể).\n" +
                "2. Bài toán chỉ in ra YES/NO hoặc một chuỗi cố định.\n\n" +
                "=== ĐỀ BÀI ===\n" + problem.toString() + "\n\n" +
                "Chỉ trả về 'YES' hoặc 'NO'. KHÔNG VIẾT GÌ THÊM.";

        String payload = buildTextPayload(TEXT_MODEL, prompt);
        String response = sendRequestWithRetry(payload).trim().toUpperCase();
        return response.contains("YES");
    }

    /**
     * Tự động sinh code mẫu AC / WA / TLE
     */
    public String generateSampleCode(Problem problem, String type) throws Exception {
        String constraintInstructions = "";
        if (type.equals("AC")) {
            constraintInstructions = "- Là code C++ giải chuẩn xác nhất, độ phức tạp thời gian cực kỳ tối ưu, qua được toàn bộ các trường hợp Edge Cases và Input cực lớn.\n" +
                                     "- BẮT BUỘC: Sử dụng `long long` thay cho `int` trong mọi biến tính toán, vòng lặp mảng lớn.\n" +
                                     "- BẮT BUỘC: TUYỆT ĐỐI CHỈ IN RA KẾT QUẢ, KHÔNG in chữ dư thừa như \"Result :\", \"Ket qua\".\n" +
                                     "- BẮT BUỘC: Có `ios_base::sync_with_stdio(0); cin.tie(0);` ở đầu main().\n";
        } else if (type.equals("WA")) {
            constraintInstructions = "- MỤC TIÊU TỐI THƯỢNG: ĐÂY LÀ CODE DÙNG ĐỂ BẪY LỖI, NÊN PHẢI BỊ WRONG ANSWER (SAI KẾT QUẢ) TRÊN ÍT NHẤT 1 TESTCASE, TUYỆT ĐỐI KHÔNG ĐƯỢC VIẾT CODE ĐÚNG HOÀN TOÀN (100% AC)!!!\n" +
                                     "- Cố tình chèn vào một lỗi Logic tế nhị (Subtle logic bug) hoặc bỏ sót Trường hợp biên (Edge cases).\n" +
                                     "- Ví dụ bắt buộc áp dụng 1 trong các lỗi sau: \n" +
                                     "  + Dùng kiểu `int` cho biến cộng dồn thay vì `long long` để cố tình gây tràn số khi Input lớn.\n" +
                                     "  + Bỏ qua trường hợp n=0, n=1, mảng rỗng.\n" +
                                     "  + Thuật toán Tham lam (Greedy) sai bản chất thay vì Quy hoạch động.\n" +
                                     "  + Khởi tạo min/max sai giá trị vô cực.\n" +
                                     "- Lưu ý: Phải biên dịch được, không bị lỗi cú pháp, chạy đúng ở testcase nhỏ, chỉ sai ở testcase dị/lớn.\n";
        } else if (type.equals("TLE")) {
            constraintInstructions = "- MỤC TIÊU TỐI THƯỢNG: ĐÂY LÀ CODE ĐỂ KIỂM TRA GIỚI HẠN THỜI GIAN, PHẢI CHẠY CHẬM VÀ BỊ TLE KHI INPUT LỚN (N=10^5).\n" +
                                     "- Cố tình sử dụng thuật toán VÉT CẠN (Brute-force) vô cùng chậm chạp có độ phức tạp thời gian cực kém như O(N^2), O(N^3), hoặc đệ quy không nhớ (Backtracking) thay vì Quy hoạch động/Tìm kiếm nhị phân.\n" +
                                     "- Ví dụ: Thay vì Binary Search, hãy duyệt mảng từ 1 đến N. Thay vì dùng `std::set`, hãy dùng mảng và duyệt tuyến tính để kiểm tra tồn tại.\n" +
                                     "- TUYỆT ĐỐI KHÔNG dùng vòng lặp vô hạn (infinite loop) `while(true)`, code vẫn phải có logic đúng và kết thúc được, chỉ là tốn nhiều phép tính hơn.\n";
        }

        String prompt = "Bạn là một thí sinh tham gia kỳ thi lập trình. \n" +
                "Hãy viết code mẫu bằng C++ cho bài toán sau với phân loại chất lượng là " + type + ".\n" +
                "=== ĐỀ BÀI ===\n" + problem.toString() + "\n\n" +
                "=== YÊU CẦU ĐỐI VỚI LOẠI CODE " + type + " ===\n" +
                constraintInstructions + 
                "- BẮT BUỘC #include đầy đủ các thư viện C++ cần thiết (như <iostream>, <cmath>, <vector>, <algorithm>, v.v.) hoặc dùng <bits/stdc++.h> để không bị lỗi Missing Declaration khi biên dịch.\n" +
                "\nChỉ trả về mã C++ thuần túy, không format markdown, không giải thích dòng nào cả.";

        String model = (problem.getOriginalImageBase64() != null) ? VISION_MODEL : TEXT_MODEL;
        String payload = buildPayload(model, prompt, null, problem.getOriginalImageBase64());
        return sendRequestWithRetry(payload);
    }

    /**
     * Gửi request tới Groq API kèm cơ chế Retry + xử lý 429 Rate Limit
     */
    private String sendRequestWithRetry(String jsonPayload) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + this.apiKey)
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        int attempts = 0;
        while (attempts < MAX_RETRIES) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                
                com.google.gson.JsonElement parsedElement = com.google.gson.JsonParser.parseString(response.body());
                JsonObject jsonResponse = null;
                if (parsedElement.isJsonArray() && parsedElement.getAsJsonArray().size() > 0) {
                    jsonResponse = parsedElement.getAsJsonArray().get(0).getAsJsonObject();
                } else if (parsedElement.isJsonObject()) {
                    jsonResponse = parsedElement.getAsJsonObject();
                }

                if (status == 200) {
                    if (jsonResponse != null && jsonResponse.has("choices")) {
                        JsonArray choices = jsonResponse.getAsJsonArray("choices");
                        if (choices.size() > 0) {
                            JsonObject message = choices.get(0).getAsJsonObject()
                                    .getAsJsonObject("message");
                            if (message != null && message.has("content")) {
                                return stripMarkdown(message.get("content").getAsString());
                            }
                        }
                    }
                    throw new Exception("API trả về thành công nhưng không tìm thấy nội dung.");

                } else if (status == 429) {
                    // Rate Limit: đợi rồi thử lại
                    long waitSecs = 60; // mặc định 60s
                    // Cố gắng đọc Retry-After header nếu có
                    response.headers().firstValue("retry-after")
                            .ifPresent(v -> { /* không thể assign lại, dùng giá trị mặc định */ });
                    attempts++;
                    if (attempts >= MAX_RETRIES) {
                        throw new Exception("⚠ Groq API đang bị giới hạn Token/phút (429 Rate Limit).\n"
                            + "Vui lòng đợi 1-2 phút rồi thử lại, hoặc rút ngắn đề bài.");
                    }
                    System.err.println("[429 Rate Limit] Đợi " + waitSecs + "s trước khi thử lại (lần " + attempts + "/" + MAX_RETRIES + ")");
                    Thread.sleep(waitSecs * 1000L);

                } else {
                    System.err.println("Lỗi kết nối AI API " + status + ": " + response.body());
                    if (jsonResponse != null && jsonResponse.has("error")) {
                        String errorMsg = jsonResponse.getAsJsonObject("error").get("message").getAsString();
                        throw new Exception("Lỗi AI API " + status + ": " + errorMsg);
                    }
                    attempts++;
                    Thread.sleep(2000L * attempts);
                }
            } catch (IOException | InterruptedException e) {
                attempts++;
                if (attempts == MAX_RETRIES) {
                    throw new Exception("Lỗi kết nối AI API sau " + MAX_RETRIES + " lần thử: " + e.getMessage());
                }
                Thread.sleep(2000L * attempts);
            }
        }
        throw new Exception("Thất bại khi lấy dữ liệu từ AI API.");
    }

    /**
     * Xóa markdown formatting từ response của AI (```code```, ###, v.v.)
     * Llama/Groq thường trả về có markdown dù không yêu cầu.
     */
    private String stripMarkdown(String text) {
        if (text == null) return "";
        // Xóa code fence ``` với hoặc không có ngôn ngữ (```json, ```cpp, ```)
        text = text.replaceAll("(?s)```[a-zA-Z]*\\n", "").replaceAll("```", "");
        // Xóa header markdown (### Bài A:  →  Bài A:) nhưng chừa lại dấu # của #include
        text = text.replaceAll("(?m)^#{1,6}\\s+(?!include)", "");
        // TUYỆT ĐỐI KHÔNG XÓA DẤU * HAY _ VÌ NÓ SẼ LÀM HỎNG PHÉP NHÂN (a * b) HOẶC CON TRỎ TRONG C++
        return text.trim();
    }

    /**
     * Build payload text-only (OpenAI chat format)
     */
    private String buildTextPayload(String model, String userMessage) {
        return buildPayload(model, userMessage, null, null);
    }

    /**
     * Build payload hỗ trợ cả text và ảnh (vision)
     */
    private String buildPayload(String model, String prompt, String extraText, String imageBase64) {
        JsonObject payload = new JsonObject();
        payload.addProperty("model", model);

        JsonArray messages = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");

        if (imageBase64 != null) {
            // Vision: content là mảng gồm text + image_url
            JsonArray contentArr = new JsonArray();

            JsonObject textPart = new JsonObject();
            textPart.addProperty("type", "text");
            textPart.addProperty("text", prompt + (extraText != null ? "\n" + extraText : ""));
            contentArr.add(textPart);

            JsonObject imagePart = new JsonObject();
            imagePart.addProperty("type", "image_url");
            JsonObject imageUrl = new JsonObject();
            imageUrl.addProperty("url", "data:image/jpeg;base64," + imageBase64);
            imagePart.add("image_url", imageUrl);
            contentArr.add(imagePart);

            userMsg.add("content", contentArr);
        } else {
            // Text-only: content là string
            String fullText = prompt + (extraText != null ? "\n" + extraText : "");
            userMsg.addProperty("content", fullText);
        }

        messages.add(userMsg);
        payload.add("messages", messages);

        // Giới hạn output để tránh vượt quota token
        payload.addProperty("max_tokens", 2048);

        return gson.toJson(payload);
    }

    /**
     * Build payload với system prompt + user message (tốt hơn cho code generation).
     * Groq/OpenAI hỗ trợ role "system" để set ngữ cảnh chuyên gia.
     */
    private String buildPayloadWithSystem(String model, String systemPrompt, String userMessage, String imageBase64) {
        JsonObject payload = new JsonObject();
        payload.addProperty("model", model);

        JsonArray messages = new JsonArray();

        // System message — set vai trò chuyên gia
        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", systemPrompt);
        messages.add(sysMsg);

        // User message — đề bài + yêu cầu
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        
        if (imageBase64 != null) {
            JsonArray contentArr = new JsonArray();
            JsonObject textPart = new JsonObject();
            textPart.addProperty("type", "text");
            textPart.addProperty("text", userMessage);
            contentArr.add(textPart);

            JsonObject imagePart = new JsonObject();
            imagePart.addProperty("type", "image_url");
            JsonObject imageUrl = new JsonObject();
            imageUrl.addProperty("url", "data:image/jpeg;base64," + imageBase64);
            imagePart.add("image_url", imageUrl);
            contentArr.add(imagePart);

            userMsg.add("content", contentArr);
        } else {
            userMsg.addProperty("content", userMessage);
        }
        
        messages.add(userMsg);

        payload.add("messages", messages);
        payload.addProperty("max_tokens", 2048);

        return gson.toJson(payload);
    }
}

