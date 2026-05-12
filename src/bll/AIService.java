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
        String prompt = "You are a Competitive Programming Expert. Analyze the provided problem and return a JSON object.\n" +
                "JSON Structure:\n" +
                "1. \"title\": Short title.\n" +
                "2. \"timeLimitMs\": Integer (default 1000).\n" +
                "3. \"memoryLimitMb\": Integer (default 256).\n" +
                "4. \"content\": Comprehensive summary including requirements, constraints, input/output formats.\n" +
                "Output ONLY valid JSON. No markdown.";

        String imageBase64 = null;
        if (imageFile != null && imageFile.exists()) {
            byte[] fileContent = Files.readAllBytes(imageFile.toPath());
            imageBase64 = Base64.getEncoder().encodeToString(fileContent);
        }

        String model = (imageBase64 != null) ? VISION_MODEL : TEXT_MODEL;
        String payload = buildPayload(model, prompt, text, imageBase64);
        String responseMessage = sendRequestWithRetry(payload);

        Problem p = new Problem();
        try {
            JsonObject jsonOutput = this.gson.fromJson(responseMessage, JsonObject.class);
            if (jsonOutput.has("title")) p.setTitle(jsonOutput.get("title").getAsString());
            if (jsonOutput.has("timeLimitMs")) p.setTimeLimitMs(jsonOutput.get("timeLimitMs").getAsInt());
            if (jsonOutput.has("memoryLimitMb")) p.setMemoryLimitMb(jsonOutput.get("memoryLimitMb").getAsInt());
            if (jsonOutput.has("content")) p.setContent(jsonOutput.get("content").getAsString());
        } catch (Exception e) {
            System.err.println("AI không trả về JSON hợp lệ: " + e.getMessage());
            p.setContent(responseMessage);
            p.setTimeLimitMs(1000);
            p.setMemoryLimitMb(256);
        }
        return p;
    }

    /**
     * Sinh code C++ Generator sử dụng testlib.h
     */
    public String generateGeneratorCode(Problem problem) throws Exception {
        String systemPrompt = "You are a Competitive Programming Expert specializing in 'testlib.h' for C++. " +
                "Your task is to write a robust, professional Generator. " +
                "Output ONLY raw C++ code. No markdown, no explanations.";

        String userPrompt = "### PROBLEM CONTEXT\n" + problem.toString() + "\n\n" +
                "### GENERATOR REQUIREMENTS\n" +
                "Write a C++ generator using 'testlib.h' to produce input cases for the above problem.\n\n" +
                "1. MANDATORY STRUCTURE:\n" +
                "   - Include <bits/stdc++.h> then \"testlib.h\".\n" +
                "   - Use 'using namespace std;'.\n" +
                "   - First line in main: registerGen(argc, argv, 1);\n" +
                "   - Use argv[1] as seed.\n\n" +
                "2. MODE-BASED GENERATION (argv[2]):\n" +
                "   - If argv[2] == 'edge': Generate minimal/boundary cases.\n" +
                "   - If argv[2] == 'max': Generate cases at the absolute MAXIMUM constraints.\n" +
                "   - If argv[2] == 'random': Generate balanced random cases.\n\n" +
                "3. CRITICAL TESTLIB RULES:\n" +
                "   - DO NOT use 'inf', 'ouf', or 'ans' in a Generator. Use ONLY 'rnd.next()'.\n" +
                "   - AMBIGUITY FIX: Both arguments of rnd.next() MUST be the same type (e.g., rnd.next(1LL, 200LL)).\n" +
                "   - Use ONLY 'cout <<' for output. DO NOT read from stdin.\n\n" +
                "4. SAFETY:\n" +
                "   - Use '1LL << n' instead of 'pow(2, n)' for integers.\n" +
                "   - No markdown. Output raw code only.";

        String payload = buildPayloadWithSystem(TEXT_MODEL, systemPrompt, userPrompt);
        String code = sendRequestWithRetry(payload);
        return validateAndFixCppCode(code, "generator");
    }

    /**
     * Sinh checker C++ (nếu bài toán có nhiều cách giải đúng)
     */
    public String generateChecker(Problem problem) throws Exception {
        String systemPrompt = "You are a Competitive Programming Expert specializing in 'testlib.h' for C++. " +
                "Output ONLY raw C++ code. No markdown, no explanations.";

        String userPrompt = "### PROBLEM CONTEXT\n" + problem.toString() + "\n\n" +
                "### CHECKER REQUIREMENTS\n" +
                "1. MANDATORY STRUCTURE:\n" +
                "   - Include <bits/stdc++.h> and \"testlib.h\".\n" +
                "   - Use 'using namespace std;'.\n" +
                "   - First line: registerTestlibCmd(argc, argv);\n\n" +
                "2. STREAM HANDLING:\n" +
                "   - Use 'inf', 'ans', and 'ouf'.\n" +
                "   - Methods: readInt(), readLong(), readDouble(), readWord(), readString().\n" +
                "   - FORBIDDEN: .read(), .readSpace(), .readEoln(), .readByte().\n\n" +
                "3. RULES:\n" +
                "   - Use quitf(_ok, ...) or quitf(_wa, ...).\n" +
                "   - No markdown. Raw code only.";

        String payload = buildPayloadWithSystem(TEXT_MODEL, systemPrompt, userPrompt);
        String code = sendRequestWithRetry(payload);
        return validateAndFixCppCode(code, "checker");
    }

    /**
     * Tự động sinh code mẫu AC / WA / TLE
     */
    public String generateSampleCode(Problem problem, String type) throws Exception {
        String instruction = "";
        if (type.equals("AC")) instruction = "Optimal O(N) or O(N log N) solution.";
        else if (type.equals("WA")) instruction = "Wrong logic on edge cases.";
        else if (type.equals("TLE")) instruction = "Slow brute-force solution.";

        String systemPrompt = "You are a Competitive Programmer. Write a " + type + " solution in C++.";
        String userPrompt = "### PROBLEM\n" + problem.toString() + "\n\n" +
                "### TASK\n" + instruction + "\nOutput ONLY raw C++ code. No markdown.";

        String payload = buildPayloadWithSystem(TEXT_MODEL, systemPrompt, userPrompt);
        String code = sendRequestWithRetry(payload);
        return validateAndFixCppCode(code, "solution");
    }

    /**
     * Gửi code lỗi cho AI sửa
     */
    public String fixCodeWithAI(String originalCode, String errorMessage, Problem problem, String codeType) throws Exception {
        String systemPrompt = "You are a C++ Debugging Expert. Fix the compilation error.";
        String userPrompt = "### BUGGY CODE (" + codeType + ")\n```cpp\n" + originalCode + "\n```\n\n" +
                "### ERROR\n" + errorMessage + "\n\n### TASK\nFix it now. Output ONLY raw C++ code.";

        String payload = buildPayloadWithSystem(TEXT_MODEL, systemPrompt, userPrompt);
        String fixedCode = sendRequestWithRetry(payload);
        return validateAndFixCppCode(fixedCode, codeType);
    }

    /**
     * Validate và auto-fix các lỗi phổ biến trong code C++
     */
    private String validateAndFixCppCode(String code, String codeType) {
        if (code == null || code.trim().isEmpty()) return code;

        StringBuilder errors = new StringBuilder();
        String fixedCode = code;

        if ("generator".equals(codeType)) {
            fixedCode = fixedCode.replaceAll("(inf|ouf|ans)\\.(next|read)[A-Za-z0-9]*", "rnd.next");
        }

        // 1. NGHIÊM CẤM pow() cho số nguyên - Tự động đổi pow(2, n) và cảnh báo các pow khác
        if (fixedCode.contains("pow(")) {
            fixedCode = fixedCode.replaceAll("(?i)pow\\(\\s*(?:2|2LL|2\\.0)\\s*,\\s*([^\\)]+?)\\s*\\)", "(1LL << $1)");
            if (fixedCode.contains("pow(")) {
                errors.append("- CẢNH BÁO CỰC NGUY HIỂM: Phát hiện hàm pow(). pow() trả về double, gây mất chính xác cho số lớn. Hãy dùng vòng lặp hoặc lũy thừa nhanh!\n");
            }
        }

        // 2. Kiểm tra tràn số với bit shift (n > 62)
        if (fixedCode.contains("<<")) {
            errors.append("- CẢNH BÁO: Kiểm tra kỹ giới hạn dịch bit. 1LL << n chỉ chạy đúng với n < 63. Nếu N=200 sẽ bị TRÀN SỐ!\n");
        }

        fixedCode = fixedCode.replaceAll("(\\d+LL)\\s{2,}([\\(A-Za-z])", "$1 * $2");
        fixedCode = fixedCode.replaceAll("(\\d+LL)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*([+\\-*/]|\\)|;|,)", "$1 * $2 $3");
        fixedCode = fixedCode.replaceAll("(\\d+)\\s+([A-Z_][A-Za-z0-9_]*)\\s*([+\\-*/]|\\)|;|,|$)", "$1LL * $2 $3");
        
        fixedCode = fixRndNextAmbiguity(fixedCode);

        if (fixedCode.contains("int main(int argc, char* argv)")) {
            fixedCode = fixedCode.replace("int main(int argc, char* argv)", "int main(int argc, char* argv[])");
        }

        if (!fixedCode.contains("bits/stdc++.h")) fixedCode = "#include <bits/stdc++.h>\n" + fixedCode;
        if (!fixedCode.contains("using namespace std")) {
            int firstInclude = fixedCode.lastIndexOf("#include");
            if (firstInclude != -1) {
                int nextLine = fixedCode.indexOf('\n', firstInclude);
                fixedCode = fixedCode.substring(0, nextLine + 1) + "using namespace std;\n" + fixedCode.substring(nextLine + 1);
            }
        }

        if (errors.length() > 0) {
            System.err.println("\n=== CODE VALIDATION REPORT ===\n" + errors.toString() + "===============================\n");
        }
        return fixedCode;
    }

    private String fixRndNextAmbiguity(String code) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?i)rnd\\.next\\(([^,]+),\\s*([^)]+)\\)");
        java.util.regex.Matcher matcher = pattern.matcher(code);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String f = matcher.group(1).trim(), s = matcher.group(2).trim();
            boolean fL = f.toLowerCase().endsWith("l"), sL = s.toLowerCase().endsWith("l");
            String rep;
            if (!fL && sL) rep = f.matches("\\d+") ? "rnd.next(" + f + "LL, " + s + ")" : "rnd.next((long long)" + f + ", " + s + ")";
            else if (fL && !sL) rep = s.matches("\\d+") ? "rnd.next(" + f + ", " + s + "LL)" : "rnd.next(" + f + ", (long long)" + s + ")";
            else rep = matcher.group(0);
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(rep));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String sendRequestWithRetry(String jsonPayload) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + this.apiKey)
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                String body = response.body();

                if (status == 200) {
                    JsonObject json = gson.fromJson(body, JsonObject.class);
                    return stripMarkdown(json.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString());
                } else {
                    // THÔNG BÁO LỖI CHI TIẾT
                    if (status == 429) {
                        System.err.println("[HỆ THỐNG] Lỗi 429: Bạn đang dùng quá nhanh (Rate Limit). Đang đợi 60 giây để thử lại (Lần " + (i + 1) + "/" + MAX_RETRIES + ")...");
                        Thread.sleep(60000);
                    } else if (status == 401) {
                        System.err.println("[HỆ THỐNG] Lỗi 401: API Key của bạn không hợp lệ hoặc đã bị thu hồi.");
                        throw new Exception("Sai API Key.");
                    } else if (status == 402 || status == 403) {
                        System.err.println("[HỆ THỐNG] Lỗi " + status + ": Tài khoản của bạn đã HẾT QUOTA hoặc HẾT TIỀN.");
                        throw new Exception("Hết hạn mức sử dụng API.");
                    } else {
                        System.err.println("[HỆ THỐNG] Lỗi API (Status " + status + "): " + body);
                        Thread.sleep(2000L * (i + 1));
                    }
                }
            } catch (Exception e) {
                if (e.getMessage() != null && (e.getMessage().contains("Key") || e.getMessage().contains("hạn mức"))) throw e;
                Thread.sleep(2000L * (i + 1));
            }
        }
        throw new Exception("Thất bại sau " + MAX_RETRIES + " lần thử. Vui lòng kiểm tra console.");
    }

    private String stripMarkdown(String text) {
        if (text == null) return "";
        // 1. Xóa các khối code fence
        text = text.replaceAll("(?s)```[a-zA-Z]*\\n", "").replaceAll("```", "");
        // 2. Xóa các header markdown
        text = text.replaceAll("(?m)^#{1,6}\\s+(?!include)", "");
        
        // 3. THÔNG MINH: Nếu đây là yêu cầu JSON, hãy cố gắng bóc tách khối { ... }
        if (text.contains("{") && text.contains("}")) {
            int start = text.indexOf("{");
            int end = text.lastIndexOf("}");
            if (start != -1 && end != -1 && end > start) {
                return text.substring(start, end + 1).trim();
            }
        }
        
        return text.trim();
    }

    private String buildPayload(String model, String prompt, String extra, String img) {
        JsonObject p = new JsonObject();
        p.addProperty("model", model);
        JsonArray m = new JsonArray();
        JsonObject u = new JsonObject();
        u.addProperty("role", "user");
        u.addProperty("content", prompt + (extra != null ? "\n" + extra : ""));
        m.add(u); p.add("messages", m);
        return gson.toJson(p);
    }

    private String buildPayloadWithSystem(String model, String sys, String user) {
        JsonObject p = new JsonObject();
        p.addProperty("model", model);
        JsonArray m = new JsonArray();
        JsonObject sM = new JsonObject(); sM.addProperty("role", "system"); sM.addProperty("content", sys);
        JsonObject uM = new JsonObject(); uM.addProperty("role", "user"); uM.addProperty("content", user);
        m.add(sM); m.add(uM); p.add("messages", m);
        return gson.toJson(p);
    }
}
