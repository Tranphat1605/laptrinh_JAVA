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
    private static final int MAX_RETRIES = 3;

    // Model text-only: nhanh, mạnh, miễn phí
    private static final String TEXT_MODEL = "llama-3.3-70b-versatile";
    // Model vision: hỗ trợ phân tích ảnh
    private static final String VISION_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct";

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
        String prompt = "Bạn là một AI chuyên gia về lập trình thi đấu (IOI, ICPC). " +
                "Hãy phân tích đề bài sau và trích xuất các thông tin: Tên bài, " +
                "Mô tả yêu cầu, Giới hạn đầu vào (Constraints), Định dạng Input, Định dạng Output. " +
                "Trả về dưới dạng JSON.";

        String imageBase64 = null;
        if (imageFile != null && imageFile.exists()) {
            byte[] fileContent = Files.readAllBytes(imageFile.toPath());
            imageBase64 = Base64.getEncoder().encodeToString(fileContent);
        }

        String model = (imageBase64 != null) ? VISION_MODEL : TEXT_MODEL;
        String payload = buildPayload(model, prompt, text, imageBase64);
        String responseMessage = sendRequestWithRetry(payload);

        Problem p = new Problem();
        p.setContent(responseMessage);
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
                "1. Dòng đầu tiên trong main: registerGen(argc, argv, 1);\n" +
                "2. Hỗ trợ seed từ argv[1].\n" +
                "3. In ra đúng định dạng Input mà đề bài yêu cầu, không in text thừa.\n" +
                "4. CHÚ Ý QUAN TRỌNG VỀ ĐỘ MẠNH (STRONG TESTCASES):\n" +
                "   - Generator cần lấy arg từ argv[2] (nếu truyền vào) làm tham số để quyết định mode sinh testcase.\n" +
                "   - Nếu mode là 'edge': hãy sinh các trường hợp biên, giá trị tối thiểu, tối đa (VD: N=0, N=1, mảng rỗng, mảng gồm các phần tử bằng nhau hoặc âm hoàn toàn).\n" +
                "   - Nếu mode là 'max': phải sinh Input sao cho N hoặc giá trị đạt sát Tối Đa của ràng buộc đề bài (áp lực cao để tạo TLE/MLE).\n" +
                "   - Nếu mode là 'random' hoặc không có mode, sinh Random ngẫu nhiên.\n" +
                "\nChỉ trả về code C++, không markdown.";
        String payload = buildPayloadWithSystem(TEXT_MODEL, systemPrompt, userPrompt);
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
                "- Đọc input từ: inf\n" +
                "- Đọc đáp án chuẩn từ: ans\n" +
                "- Đọc output của thí sinh từ: ouf\n" +
                "- Gọi quitf(_ok, ...) hoặc quitf(_wa, ...) tùy thuộc kết quả.\n" +
                "Chỉ trả về code C++, không markdown.";
        String payload = buildPayloadWithSystem(TEXT_MODEL, systemPrompt, userPrompt);
        return sendRequestWithRetry(payload);
    }

    /**
     * Tự động sinh code mẫu AC / WA / TLE
     */
    public String generateSampleCode(Problem problem, String type) throws Exception {
        String constraintInstructions = "";
        if (type.equals("AC")) {
            constraintInstructions = "- Là code C++ giải chuẩn xác nhất, độ phức tạp thời gian cực kỳ tối ưu, qua được toàn bộ các trường hợp Edge Cases và Input cực lớn.\n";
        } else if (type.equals("WA")) {
            constraintInstructions = "- Cố tình viết SAI LOGIC ở các TRƯỜNG HỢP BIÊN (Edge cases) nhưng vẫn chạy đúng ở các testcase cơ bản.\n" +
                                     "- Ví dụ: Không xét trường hợp n=0, hoặc kiểu dữ liệu Int bị tràn số thay vì dùng Long Long, hoặc sai dấu tại điểm giao cắt.\n" +
                                     "- Không bị TLE, chỉ được in kết quả sai.\n";
        } else if (type.equals("TLE")) {
            constraintInstructions = "- Cố tình viết thuật toán VÉT CẠN (Brute-force) có độ phức tạp cao (O(N^2) hoặc O(N^3)) để bị Quá thời gian (Time Limit Exceeded) khi Input lớn.\n" +
                                     "- Tuyệt đối KHÔNG LẶP VÔ HẠN bằng while(true), code vẫn phải cho ra kết quả đúng nếu chạy đủ lâu.\n";
        }

        String prompt = "Bạn là một thí sinh tham gia kỳ thi lập trình. \n" +
                "Hãy viết code mẫu bằng C++ cho bài toán sau với phân loại chất lượng là " + type + ".\n" +
                "=== ĐỀ BÀI ===\n" + problem.toString() + "\n\n" +
                "=== YÊU CẦU ĐỐI VỚI LOẠI CODE " + type + " ===\n" +
                constraintInstructions + 
                "\nChỉ trả về mã C++ thuần túy, không format markdown, không giải thích dòng nào cả.";

        String payload = buildTextPayload(TEXT_MODEL, prompt);
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
                JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);

                if (status == 200) {
                    if (jsonResponse.has("choices")) {
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
        // Xóa header markdown (### Bài A:  →  Bài A:)
        text = text.replaceAll("(?m)^#{1,6}\\s*", "");
        // Xóa bold/italic markdown (**text**, *text*, __text__)
        text = text.replaceAll("\\*{1,2}([^*]+)\\*{1,2}", "$1");
        text = text.replaceAll("_{1,2}([^_]+)_{1,2}", "$1");
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
    private String buildPayloadWithSystem(String model, String systemPrompt, String userMessage) {
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
        userMsg.addProperty("content", userMessage);
        messages.add(userMsg);

        payload.add("messages", messages);
        payload.addProperty("max_tokens", 2048);

        return gson.toJson(payload);
    }
}

