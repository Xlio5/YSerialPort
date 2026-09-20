package com.yujing.chuankou.serial;

public final class SerialLogBuffer {
    private final int maxChars;
    private final StringBuilder text = new StringBuilder();

    public SerialLogBuffer(int maxChars) {
        this.maxChars = Math.max(1, maxChars);
    }

    public Update append(String value) {
        String appended = value == null ? "" : value;
        text.append(appended);
        if (text.length() <= maxChars) {
            return Update.appendOnly(appended);
        }
        int keepFrom = Math.max(0, text.length() - maxChars / 2);
        text.delete(0, keepFrom);
        return Update.fullRefresh(text.toString());
    }

    public void clear() {
        text.setLength(0);
    }

    public String getText() {
        return text.toString();
    }

    public static final class Update {
        private final boolean fullRefresh;
        private final String appendedText;
        private final String fullText;

        private Update(boolean fullRefresh, String appendedText, String fullText) {
            this.fullRefresh = fullRefresh;
            this.appendedText = appendedText;
            this.fullText = fullText;
        }

        private static Update appendOnly(String appendedText) {
            return new Update(false, appendedText, "");
        }

        private static Update fullRefresh(String fullText) {
            return new Update(true, "", fullText);
        }

        public boolean requiresFullRefresh() {
            return fullRefresh;
        }

        public String getAppendedText() {
            return appendedText;
        }

        public String getFullText() {
            return fullText;
        }
    }
}
