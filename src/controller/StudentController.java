package controller;

import bll.SandboxService;
import entity.ExecutionResult;

import javax.swing.SwingUtilities;

/**
 * Controller điều phối logic cho StudentFrame.
 * Chứa nội dung đề bài và xử lý nộp code.
 */
public class StudentController {

    public interface SubmitListener {
        void onStart();
        void onComplete(ExecutionResult result);
        void onError(String message);
    }

    private static final String[] DESCRIPTIONS = {
        "Mô tả:\nNhập vào 2 số nguyên A và B cách nhau bởi khoảng trắng.\nIn ra tổng A + B.\n\n"
        + "Giới hạn:\n-10^9 <= A, B <= 10^9\n\nVí dụ Input:\n1 2\nVí dụ Output:\n3",

        "Mô tả:\nNhập vào số nguyên dương N. Kiểm tra N có phải là số nguyên tố không.\n"
        + "In ra YES hoặc NO.\n\nVí dụ Input:\n7\nVí dụ Output:\nYES"
    };

    private static final String[] MOCK_INPUTS = { "1 2\n", "7\n" };

    private final SandboxService sandboxService;

    public StudentController() {
        this.sandboxService = new SandboxService();
    }

    /** Trả về mô tả đề bài theo index — đồng bộ, không cần Thread. */
    public String getDescription(int problemIndex) {
        if (problemIndex >= 0 && problemIndex < DESCRIPTIONS.length) {
            return DESCRIPTIONS[problemIndex];
        }
        return "";
    }

    /** Nộp code và chấm trên sandbox. Callback gọi trên EDT. */
    public void submitCode(String code, String lang, int problemIndex, SubmitListener listener) {
        SwingUtilities.invokeLater(listener::onStart);
        new Thread(() -> {
            try {
                String input = (problemIndex >= 0 && problemIndex < MOCK_INPUTS.length)
                        ? MOCK_INPUTS[problemIndex] : "";
                ExecutionResult result = sandboxService.executeCode(code, lang, input, 2000L);
                SwingUtilities.invokeLater(() -> listener.onComplete(result));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> listener.onError(e.getMessage()));
            }
        }).start();
    }
}
