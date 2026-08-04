package com.zhishiyun.kb.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 LLM 流式输出拆成「正文回答」与「推荐追问」两段。
 * 约定标记：
 * <<<FOLLOWUPS>>>
 * 追问1
 * 追问2
 * <<<END>>>
 */
final class AnswerFollowupSplitter {

    static final String START = "<<<FOLLOWUPS>>>";
    static final String END = "<<<END>>>";

    @FunctionalInterface
    interface AnswerEmitter {
        void emit(String part) throws Exception;
    }

    private final StringBuilder hold = new StringBuilder();
    private final StringBuilder answer = new StringBuilder();
    private final StringBuilder followupRaw = new StringBuilder();
    private boolean followupMode;

    void onChunk(String chunk, AnswerEmitter emitAnswer) throws Exception {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        hold.append(chunk);
        while (true) {
            if (!followupMode) {
                int idx = indexOfIgnoreCase(hold, START);
                if (idx >= 0) {
                    String before = hold.substring(0, idx);
                    if (!before.isEmpty()) {
                        answer.append(before);
                        emitAnswer.emit(before);
                    }
                    hold.delete(0, idx + START.length());
                    followupMode = true;
                    continue;
                }
                int keep = START.length() - 1;
                if (hold.length() > keep) {
                    String emit = hold.substring(0, hold.length() - keep);
                    answer.append(emit);
                    emitAnswer.emit(emit);
                    hold.delete(0, hold.length() - keep);
                }
                break;
            }
            int end = indexOfIgnoreCase(hold, END);
            if (end >= 0) {
                followupRaw.append(hold, 0, end);
                hold.setLength(0);
                break;
            }
            followupRaw.append(hold);
            hold.setLength(0);
            break;
        }
    }

    /** 流结束时冲刷残留（未识别到标记时全部视为正文）。 */
    void finish(AnswerEmitter emitAnswer) throws Exception {
        if (!followupMode && hold.length() > 0) {
            answer.append(hold);
            emitAnswer.emit(hold.toString());
            hold.setLength(0);
        } else if (followupMode && hold.length() > 0) {
            followupRaw.append(hold);
            hold.setLength(0);
        }
    }

    String answerText() {
        return answer.toString().trim();
    }

    List<String> followUps(int limit) {
        List<String> list = new ArrayList<String>();
        String raw = followupRaw.toString().replace("\r\n", "\n").trim();
        if (raw.isEmpty()) {
            return list;
        }
        for (String line : raw.split("\n")) {
            String t = line.trim()
                    .replaceFirst("^[0-9]+[.)、]\\s*", "")
                    .replaceFirst("^[-*•]\\s*", "")
                    .trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.equalsIgnoreCase(START) || t.equalsIgnoreCase(END)) {
                continue;
            }
            list.add(t);
            if (list.size() >= limit) {
                break;
            }
        }
        return list;
    }

    private static int indexOfIgnoreCase(StringBuilder sb, String needle) {
        String hay = sb.toString();
        return hay.toLowerCase().indexOf(needle.toLowerCase());
    }
}
