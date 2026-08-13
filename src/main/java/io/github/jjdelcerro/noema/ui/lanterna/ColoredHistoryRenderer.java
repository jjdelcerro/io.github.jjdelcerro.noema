package io.github.jjdelcerro.noema.ui.lanterna;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.ThemeStyle;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.gui2.TextBox.DefaultTextBoxRenderer;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class ColoredHistoryRenderer extends DefaultTextBoxRenderer {

    // Paleta de colores estilo Codex
    private static final TextColor COLOR_USER         = TextColor.Factory.fromString("#3FB950"); // Verde
    private static final TextColor COLOR_LOG          = TextColor.Factory.fromString("#8B949E"); // Gris tenue
    private static final TextColor COLOR_ERR          = TextColor.Factory.fromString("#F85149"); // Rojo error
    private static final TextColor COLOR_MODEL        = TextColor.Factory.fromString("#E1E4E8"); // Blanco brillante
    private static final TextColor COLOR_HEADING      = TextColor.Factory.fromString("#B8A0FF"); // Púrpura acento
    private static final TextColor COLOR_CODE_INLINE   = TextColor.Factory.fromString("#79C0FF"); // Cian código inline
    private static final TextColor COLOR_CODE_BLOCK    = TextColor.Factory.fromString("#A5D6FF"); // Azul pastel bloque

    // Colores de la barra de scroll vertical
    private static final TextColor COLOR_SCROLL_TRACK = TextColor.Factory.fromString("#292C34"); // Pista en gris muy oscuro
    private static final TextColor COLOR_SCROLL_THUMB = TextColor.Factory.fromString("#58A6FF"); // Indicador en cian brillante

    // Conmutadores de visibilidad de la terminal
    private boolean showSystemLogs = true;
    private boolean showErrorLogs = true;

    private static record VisualSegment(String text, TextColor color, EnumSet<SGR> modifiers) {}

    private static class VisualLine {
        final List<VisualSegment> segments = new ArrayList<>();

        int length() {
            int len = 0;
            for (VisualSegment s : segments) {
                len += s.text.length();
            }
            return len;
        }

        void addSegment(String text, TextColor color, EnumSet<SGR> modifiers) {
            if (text != null && !text.isEmpty()) {
                segments.add(new VisualSegment(text, color, modifiers != null ? EnumSet.copyOf(modifiers) : EnumSet.noneOf(SGR.class)));
            }
        }
    }

    public boolean isShowSystemLogs() {
        return showSystemLogs;
    }

    public void setShowSystemLogs(boolean showSystemLogs) {
        this.showSystemLogs = showSystemLogs;
    }

    public boolean isShowErrorLogs() {
        return showErrorLogs;
    }

    public void setShowErrorLogs(boolean showErrorLogs) {
        this.showErrorLogs = showErrorLogs;
    }

    @Override
    public TerminalSize getPreferredSize(TextBox textBox) {
        return new TerminalSize(80, Math.max(1, textBox.getLineCount()));
    }

    @Override
    public TerminalPosition getCursorLocation(TextBox textBox) {
        return null; // Ocultar cursor de edición en el historial
    }

    @Override
    public void drawComponent(TextGUIGraphics graphics, TextBox textBox) {
        ThemeStyle style = textBox.getThemeDefinition().getNormal();
        graphics.applyThemeStyle(style);
        graphics.setBackgroundColor(LanternaUtils.BG_DARK);
        graphics.fill(' ');

        String text = textBox.getText();
        if (text == null || text.isEmpty()) {
            return;
        }

        int width = graphics.getSize().getColumns();
        int height = graphics.getSize().getRows();

        List<Integer> paragraphToVisualIdxTemp = new ArrayList<>();
        List<VisualLine> tempVisualLines = buildVisualLines(text, Math.max(10, width), paragraphToVisualIdxTemp);

        boolean needsScrollbar = tempVisualLines.size() > height;
        int textWidth = needsScrollbar ? Math.max(10, width - 1) : Math.max(10, width);

        List<Integer> paragraphToVisualIdx = new ArrayList<>();
        List<VisualLine> visualLines = needsScrollbar 
                ? buildVisualLines(text, textWidth, paragraphToVisualIdx)
                : tempVisualLines;

        if (visualLines.isEmpty()) {
            return;
        }

        int caretRow = textBox.getCaretPosition().getRow();
        int targetVisualRow = visualLines.size() - 1;
        if (caretRow >= 0 && caretRow < paragraphToVisualIdx.size()) {
            targetVisualRow = paragraphToVisualIdx.get(caretRow);
        }

        int maxTopLine = Math.max(0, visualLines.size() - height);
        int topLine = Math.min(targetVisualRow, maxTopLine);
        if (targetVisualRow >= topLine + height) {
            topLine = Math.min(maxTopLine, targetVisualRow - height + 1);
        }
        topLine = Math.max(0, topLine);

        for (int row = 0; row < height; row++) {
            int lineIndex = topLine + row;
            if (lineIndex >= visualLines.size()) {
                break;
            }

            VisualLine vLine = visualLines.get(lineIndex);
            int col = 0;

            for (VisualSegment seg : vLine.segments) {
                if (col >= textWidth) {
                    break;
                }

                graphics.setForegroundColor(seg.color);

                if (!seg.modifiers.isEmpty()) {
                    graphics.enableModifiers(seg.modifiers.toArray(new SGR[0]));
                }

                String printText = seg.text;
                if (col + printText.length() > textWidth) {
                    printText = printText.substring(0, textWidth - col);
                }

                graphics.putString(col, row, printText);
                col += printText.length();

                if (!seg.modifiers.isEmpty()) {
                    graphics.disableModifiers(seg.modifiers.toArray(new SGR[0]));
                }
            }
        }

        if (needsScrollbar) {
            drawVerticalScrollbar(graphics, width - 1, height, visualLines.size(), topLine);
        }
    }

    private void drawVerticalScrollbar(TextGUIGraphics graphics, int scrollCol, int viewHeight, int totalLines, int topLine) {
        int thumbHeight = Math.max(1, (int) Math.round((double) viewHeight * viewHeight / totalLines));
        if (thumbHeight > viewHeight) {
            thumbHeight = viewHeight;
        }

        int maxTopLine = totalLines - viewHeight;
        double scrollRatio = (maxTopLine > 0) ? (double) topLine / maxTopLine : 0.0;

        int maxThumbTop = viewHeight - thumbHeight;
        int thumbTop = (int) Math.round(scrollRatio * maxThumbTop);
        thumbTop = Math.max(0, Math.min(thumbTop, maxThumbTop));

        for (int row = 0; row < viewHeight; row++) {
            boolean isThumb = (row >= thumbTop && row < thumbTop + thumbHeight);
            if (isThumb) {
                graphics.setForegroundColor(COLOR_SCROLL_THUMB);
                graphics.putString(scrollCol, row, "█");
            } else {
                graphics.setForegroundColor(COLOR_SCROLL_TRACK);
                graphics.putString(scrollCol, row, "│");
            }
        }
    }

    private List<VisualLine> buildVisualLines(String rawText, int maxLen, List<Integer> paragraphToVisualIdx) {
        List<VisualLine> visualLines = new ArrayList<>();
        String cleanText = rawText.replace("\r", "");
        String[] rawParagraphs = StringUtils.splitPreserveAllTokens(cleanText, "\n");

        boolean inCodeBlock = false;

        for (String rawParagraph : rawParagraphs) {
            paragraphToVisualIdx.add(visualLines.size());

            TextColor baseColor = COLOR_MODEL;
            String body = rawParagraph;

            // Aislamiento de mensajes por rol y filtrado de visibilidad
            if (rawParagraph.startsWith("[USR] ")) {
                inCodeBlock = false;
                baseColor = COLOR_USER;
                body = rawParagraph.substring(6);
            } else if (rawParagraph.startsWith("[SIS] ")) {
                inCodeBlock = false;
                if (!showSystemLogs) {
                    continue; // Filtrar mensajes del sistema si están desactivados
                }
                baseColor = COLOR_LOG;
                body = rawParagraph.substring(6);
            } else if (rawParagraph.startsWith("[ERR] ")) {
                inCodeBlock = false;
                if (!showErrorLogs) {
                    continue; // Filtrar mensajes de error si están desactivados
                }
                baseColor = COLOR_ERR;
                body = rawParagraph.substring(6);
            } else if (rawParagraph.startsWith("[RES] ")) {
                inCodeBlock = false;
                baseColor = COLOR_MODEL;
                body = rawParagraph.substring(6);
            }

            if (body.startsWith("```") || body.startsWith("~~~")) {
                inCodeBlock = !inCodeBlock;
                VisualLine fenceLine = new VisualLine();
                String lang = body.length() > 3 ? " [" + body.substring(3).trim() + "]" : "";
                fenceLine.addSegment("─── código" + lang + " ───", COLOR_LOG, EnumSet.of(SGR.ITALIC));
                visualLines.add(fenceLine);
                continue;
            }

            if (inCodeBlock) {
                wrapCodeLine(body, maxLen, COLOR_CODE_BLOCK, visualLines);
                continue;
            }

            if (body.isEmpty()) {
                VisualLine emptyLine = new VisualLine();
                emptyLine.addSegment("", baseColor, null);
                visualLines.add(emptyLine);
                continue;
            }

            List<VisualSegment> parsedSegments = parseParagraphSegments(body, baseColor);
            wrapSegments(parsedSegments, maxLen, visualLines);
        }

        return visualLines;
    }

    private List<VisualSegment> parseParagraphSegments(String body, TextColor baseColor) {
        List<VisualSegment> segments = new ArrayList<>();
        EnumSet<SGR> baseModifiers = EnumSet.noneOf(SGR.class);
        TextColor activeColor = baseColor;

        if (body.startsWith("# ") || body.startsWith("## ") || body.startsWith("### ")) {
            int firstSpace = body.indexOf(' ');
            body = body.substring(firstSpace + 1);
            activeColor = COLOR_HEADING;
            baseModifiers.add(SGR.BOLD);
            baseModifiers.add(SGR.UNDERLINE);
        } else if (body.startsWith("- ") || body.startsWith("* ")) {
            body = "• " + body.substring(2);
        }

        StringBuilder currentText = new StringBuilder();
        boolean bold = false;
        boolean italic = false;
        boolean code = false;

        int i = 0;
        while (i < body.length()) {
            if (!code && i + 1 < body.length() && (body.substring(i, i + 2).equals("**") || body.substring(i, i + 2).equals("__"))) {
                if (currentText.length() > 0) {
                    segments.add(new VisualSegment(currentText.toString(), activeColor, combineModifiers(baseModifiers, bold, italic, code)));
                    currentText.setLength(0);
                }
                bold = !bold;
                i += 2;
            } else if (!code && (body.charAt(i) == '*' || body.charAt(i) == '_')) {
                if (currentText.length() > 0) {
                    segments.add(new VisualSegment(currentText.toString(), activeColor, combineModifiers(baseModifiers, bold, italic, code)));
                    currentText.setLength(0);
                }
                italic = !italic;
                i++;
            } else if (body.charAt(i) == '`') {
                if (currentText.length() > 0) {
                    segments.add(new VisualSegment(currentText.toString(), activeColor, combineModifiers(baseModifiers, bold, italic, code)));
                    currentText.setLength(0);
                }
                code = !code;
                i++;
            } else {
                currentText.append(body.charAt(i));
                i++;
            }
        }

        if (currentText.length() > 0) {
            TextColor colorForSeg = code ? COLOR_CODE_INLINE : activeColor;
            segments.add(new VisualSegment(currentText.toString(), colorForSeg, combineModifiers(baseModifiers, bold, italic, code)));
        }

        return segments;
    }

    private EnumSet<SGR> combineModifiers(EnumSet<SGR> base, boolean bold, boolean italic, boolean code) {
        EnumSet<SGR> mods = EnumSet.copyOf(base);
        if (bold || code) {
            mods.add(SGR.BOLD);
        }
        if (italic) {
            mods.add(SGR.ITALIC);
        }
        return mods;
    }

    private void wrapCodeLine(String lineText, int maxLen, TextColor color, List<VisualLine> outLines) {
        if (lineText.length() <= maxLen) {
            VisualLine vLine = new VisualLine();
            vLine.addSegment(lineText, color, null);
            outLines.add(vLine);
            return;
        }

        int start = 0;
        while (start < lineText.length()) {
            int end = Math.min(start + maxLen, lineText.length());
            VisualLine vLine = new VisualLine();
            vLine.addSegment(lineText.substring(start, end), color, null);
            outLines.add(vLine);
            start = end;
        }
    }

    private void wrapSegments(List<VisualSegment> parsedSegments, int maxLen, List<VisualLine> outLines) {
        VisualLine currentLine = new VisualLine();

        for (VisualSegment seg : parsedSegments) {
            String[] words = seg.text.split(" ", -1);

            for (int w = 0; w < words.length; w++) {
                String word = words[w];

                while (word.length() > maxLen) {
                    int available = maxLen - currentLine.length();
                    if (available > 0) {
                        currentLine.addSegment(word.substring(0, available), seg.color, seg.modifiers);
                        word = word.substring(available);
                    }
                    outLines.add(currentLine);
                    currentLine = new VisualLine();
                }

                int needed = word.length() + (currentLine.length() > 0 ? 1 : 0);
                if (currentLine.length() + needed > maxLen && currentLine.length() > 0) {
                    outLines.add(currentLine);
                    currentLine = new VisualLine();
                }

                if (currentLine.length() > 0 && w > 0) {
                    currentLine.addSegment(" ", seg.color, seg.modifiers);
                }
                currentLine.addSegment(word, seg.color, seg.modifiers);
            }
        }

        if (currentLine.length() > 0 || outLines.isEmpty()) {
            outLines.add(currentLine);
        }
    }
}
