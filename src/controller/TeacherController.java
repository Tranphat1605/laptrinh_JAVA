package controller;

import bll.AIService;
import entity.Problem;

import java.io.File;
import javax.swing.SwingUtilities;

/**
 * Controller điều phối logic AI cho TeacherFrame.
 * View chỉ gọi method ở đây và implement Listener tương ứng.
 */
public class TeacherController {

    public interface AnalysisListener {
        void onStart();
        void onComplete(Problem problem);
        void onError(String message);
    }

    public interface GenerationListener {
        void onStart();
        void onComplete(String generatorCode, String checkerCode);
        void onError(String message);
    }

    public interface SampleCodeListener {
        void onStart();
        void onComplete(String acCode, String waCode, String tleCode);
        void onError(String message);
    }

    private final AIService aiService;

    public TeacherController(String apiKey) {
        this.aiService = new AIService(apiKey);
    }

    /** Phân tích đề bài (text/ảnh). Callback gọi trên EDT. */
    public void analyzeProblem(String text, File imageFile, AnalysisListener listener) {
        SwingUtilities.invokeLater(listener::onStart);
        new Thread(() -> {
            try {
                Problem p = aiService.analyzeProblem(text, imageFile);
                SwingUtilities.invokeLater(() -> listener.onComplete(p));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> listener.onError(e.getMessage()));
            }
        }).start();
    }

    /** Sinh Generator C++ và Checker C++ (Tùy biến theo đặc điểm đề bài). */
    public void generateTestcaseAndChecker(Problem problem, GenerationListener listener) {
        SwingUtilities.invokeLater(listener::onStart);
        new Thread(() -> {
            try {
                // 1. Sinh Generator
                String gen = aiService.generateGeneratorCode(problem);
                
                // 2. Kiểm tra tính cần thiết của Checker
                boolean needsChecker = aiService.checkIfCheckerIsNeeded(problem);
                String check;
                if (needsChecker) {
                    check = aiService.generateChecker(problem);
                } else {
                    check = "// [HỆ THỐNG]: AI xác định bài toán này có đáp án duy nhất.\n" +
                            "// Không cần Custom Checker. Hệ thống sẽ dùng so khớp chính xác (Exact Match).\n" +
                            "// Nếu bạn vẫn muốn dùng Checker, hãy bấm nút 'Ép buộc sinh Checker'.";
                }
                
                SwingUtilities.invokeLater(() -> listener.onComplete(gen, check));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> listener.onError(e.getMessage()));
            }
        }).start();
    }

    /** Ép AI phải sinh Checker dù đánh giá ban đầu là không cần. */
    public void forceGenerateChecker(Problem problem, GenerationListener listener) {
        SwingUtilities.invokeLater(listener::onStart);
        new Thread(() -> {
            try {
                String check = aiService.generateChecker(problem);
                SwingUtilities.invokeLater(() -> listener.onComplete(null, check));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> listener.onError(e.getMessage()));
            }
        }).start();
    }

    /** Sinh code mẫu AC, WA và TLE. */
    public void generateSampleCodes(Problem problem, SampleCodeListener listener) {
        SwingUtilities.invokeLater(listener::onStart);
        new Thread(() -> {
            try {
                String ac = aiService.generateSampleCode(problem, "AC");
                String wa = aiService.generateSampleCode(problem, "WA");
                String tle = aiService.generateSampleCode(problem, "TLE");
                SwingUtilities.invokeLater(() -> listener.onComplete(ac, wa, tle));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> listener.onError(e.getMessage()));
            }
        }).start();
    }
}
