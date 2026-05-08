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

public class AIService {
    private final String apiKey;
    private final HttpClient client;
    private static final String GEMINI_API_URL_TEMPLATE = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private static final int MAX_RETRIES = 3;
    private final Gson gson;

    public AIService(String apiKey) {
        this.apiKey = apiKey;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
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
                "Trả về dưới dạng file JSON.";
        
        String imageBase64 = null;
        if (imageFile != null && imageFile.exists()) {
            byte[] fileContent = Files.readAllBytes(imageFile.toPath());
            imageBase64 = Base64.getEncoder().encodeToString(fileContent);
        }

        String payload = buildJsonPayload(prompt, text, imageBase64);
        String model = (imageBase64 == null) ? "gemini-1.5-flash" : "gemini-1.5-pro";
        String responseMessage = sendRequestWithRetry(payload, model);
        
        // Parse JSON về entity Problem (Cần cài đặt logic parse tuỳ theo schema bạn yêu cầu AI trả về)
        Problem p = new Problem();
        p.setContent(responseMessage); // Tạm lưu thô để in ra GUI
        return p;
    }

    /**
     * Sinh code C++ Generator sử dụng testlib.h
     */
    public String generateGeneratorCode(Problem problem) throws Exception {
        String prompt = "Viết một đoạn code C++ sử dụng thư viện \"testlib.h\" để sinh ngẫu nhiên dữ liệu đầu vào (Input) cho bài toán sau:\n" +
                problem.toString() + "\n" +
                "Yêu cầu TRỌNG TÂM:\n" +
                "1. Bắt buộc phải khởi tạo bằng lệnh: registerGen(argc, argv, 1);\n" +
                "2. Dùng rnd.next() để sinh số liệu.\n" +
                "3. Dữ liệu sinh ra phải tuân thủ tuyệt đối các ràng buộc của đề bài.\n" +
                "4. Hỗ trợ bắt tham số seed từ argv[1] để đảm bảo tính tất định.\n" +
                "5. Tuyệt đối chỉ in ra dữ liệu test, không in thêm text thừa (như \"Nhap N:\").\n" +
                "Chỉ trả về mã code C++ không kèm markdown.";

        String payload = buildTextPayload(prompt);
        return sendRequestWithRetry(payload, "gemini-1.5-flash");
    }

    /**
     * Sinh checker C++ (nếu bài toán có nhiều cách giải đúng)
     */
    public String generateChecker(Problem problem) throws Exception {
        String prompt = "Viết code C++ checker sử dụng thư viện testlib.h cho bài toán sau:\n" +
                problem.toString() + "\n" +
                "Checker cần đọc input từ inf, đáp án dự kiến từ ans, và đầu ra của thí sinh từ ouf. " +
                "Chỉ trả về mã C++ không kèm markdown.";
        String payload = buildTextPayload(prompt);
        return sendRequestWithRetry(payload, "gemini-1.5-flash");
    }

    /**
     * Tự động sinh code mẫu AC / WA / TLE
     */
    public String generateSampleCode(Problem problem, String type) throws Exception {
        String prompt = "Viết code mẫu bằng C++ cho bài toán sau với kết quả mong đợi là: " + type + " (AC: Tối ưu chuẩn, WA: Sai logic, TLE: Quá thời gian n^2, n^3...)\n" +
                problem.toString() + "\nChỉ trả về mã C++.";
        String payload = buildTextPayload(prompt);
        return sendRequestWithRetry(payload, "gemini-1.5-flash");
    }

    /**
     * Gửi request gọi API kèm cơ chế Retry và Timeout
     */
    private String sendRequestWithRetry(String jsonPayload, String model) throws Exception {
        String apiUrl = String.format(GEMINI_API_URL_TEMPLATE, model, this.apiKey);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(120)) // Tăng Timeout lên 120s cho mô hình lớn và sinh code
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        int attempts = 0;
        while (attempts < MAX_RETRIES) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);
                
                if (response.statusCode() == 200) {
                    if (jsonResponse.has("candidates")) {
                        JsonArray candidates = jsonResponse.getAsJsonArray("candidates");
                        if (candidates.size() > 0) {
                            JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
                            if (content != null && content.has("parts")) {
                                JsonArray parts = content.getAsJsonArray("parts");
                                if (parts.size() > 0) {
                                    return parts.get(0).getAsJsonObject().get("text").getAsString();
                                }
                            }
                        }
                    }
                    throw new Exception("API trả về thành công nhưng không tìm thấy nội dung content.");
                } else {
                    // Nếu lỗi HTTP 400, 500, in ra body để dễ debug
                    System.err.println("API Error " + response.statusCode() + ": " + response.body());
                    if (jsonResponse != null && jsonResponse.has("error")) {
                        throw new Exception("Gemini API Error: " + jsonResponse.getAsJsonObject("error").get("message").getAsString());
                    }
                }
                attempts++;
                Thread.sleep(2000 * attempts); // Backoff
            } catch (IOException | InterruptedException e) {
                attempts++;
                if (attempts == MAX_RETRIES) {
                    throw new Exception("Lỗi kết nối API sau " + MAX_RETRIES + " lần thử: " + e.getMessage());
                }
                Thread.sleep(2000 * attempts);
            }
        }
        throw new Exception("Thất bại khi lấy dữ liệu từ AI API.");
    }

    private String buildTextPayload(String text) {
        JsonObject payload = new JsonObject();
        JsonArray contents = new JsonArray();
        
        JsonObject contentObj = new JsonObject();
        JsonArray parts = new JsonArray();
        
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", text);
        parts.add(textPart);
        
        contentObj.add("parts", parts);
        contents.add(contentObj);
        
        payload.add("contents", contents);
        return gson.toJson(payload);
    }

    private String buildJsonPayload(String prompt, String text, String imageBase64) {
        JsonObject payload = new JsonObject();
        JsonArray contents = new JsonArray();
        
        JsonObject contentObj = new JsonObject();
        JsonArray parts = new JsonArray();
        
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", prompt + "\n" + (text != null ? text : ""));
        parts.add(textPart);
        
        if (imageBase64 != null) {
            JsonObject inlineDataPart = new JsonObject();
            JsonObject inlineData = new JsonObject();
            // Gemini API expects mime_type and base64 data
            inlineData.addProperty("mime_type", "image/jpeg");
            inlineData.addProperty("data", imageBase64);
            inlineDataPart.add("inline_data", inlineData);
            parts.add(inlineDataPart);
        }
        
        contentObj.add("parts", parts);
        contents.add(contentObj);
        
        payload.add("contents", contents);
        return gson.toJson(payload);
    }
}

