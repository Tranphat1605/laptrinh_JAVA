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
        void onComplete(String acCode, String waCode);
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

    /** Sinh Generator C++ và Checker C++. */
    public void generateTestcaseAndChecker(Problem problem, GenerationListener listener) {
        SwingUtilities.invokeLater(listener::onStart);
        new Thread(() -> {
            try {
                String gen   = aiService.generateGeneratorCode(problem);
                String check = aiService.generateChecker(problem);
                SwingUtilities.invokeLater(() -> listener.onComplete(gen, check));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> listener.onError(e.getMessage()));
            }
        }).start();
    }

    /** Sinh code mẫu AC và WA. */
    public void generateSampleCodes(Problem problem, SampleCodeListener listener) {
        SwingUtilities.invokeLater(listener::onStart);
        new Thread(() -> {
            try {
                String ac = aiService.generateSampleCode(problem, "AC");
                String wa = aiService.generateSampleCode(problem, "WA");
                SwingUtilities.invokeLater(() -> listener.onComplete(ac, wa));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> listener.onError(e.getMessage()));
            }
        }).start();
    }
}
