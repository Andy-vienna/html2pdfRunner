package org.andy.html2pdf;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.PageSizeUnits;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.parser.Parser;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Runner {
	
	private static String styleCss = null;
	
	//###################################################################################################################################################
	// public Teil
	//###################################################################################################################################################
	
    public static void main(String[] args) {
    	
        if (args.length != 3) {
            System.err.println("Usage: java -jar html2pdf-runner.jar <htmlIn> <pdfOut> <style>");
            System.exit(1);
        }
        Path htmlIn = Path.of(args[0]);
        Path pdfOut = Path.of(args[1]);
        styleCss = args[2];
        try {
            render(htmlIn, pdfOut.toFile());
        } catch (IOException e) {
            e.printStackTrace(System.err); System.exit(2);
        } catch (Exception e) {
            e.printStackTrace(System.err); System.exit(3);
        }
    }

    public static void doRender(String inHtml, String outPdf) {
    	
        try { render(Path.of(inHtml), Path.of(outPdf).toFile()); }
        catch (IOException e) { e.printStackTrace(System.err); System.exit(2); }
        catch (Exception e) { e.printStackTrace(System.err); System.exit(3); }
    }
    
	//###################################################################################################################################################
	// private Teil
	//###################################################################################################################################################

    private static void render(Path htmlIn, File outPdf) throws Exception {
    	
        String html = Files.readString(htmlIn, StandardCharsets.UTF_8);

        String body = normalizeBody(html);      // Body extrahieren/aufräumen
        body = autolinkHtml(body);              // URLs/Mails -> <a>
        String xhtml = buildXhtml(body);        // CSS + Wrapper

        // FINAL: wohlgeformtes XHTML erzwingen
        Document doc = Jsoup.parse(xhtml);
        doc.outputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .escapeMode(Entities.EscapeMode.xhtml)
                .charset(StandardCharsets.UTF_8)
                .prettyPrint(false);
        xhtml = doc.outerHtml();

        try (OutputStream os = new FileOutputStream(outPdf)) {
            PdfRendererBuilder b = new PdfRendererBuilder();
            b.useFastMode();
            b.useDefaultPageSize(210, 297, PageSizeUnits.MM);
            b.withHtmlContent(xhtml, null);
            b.useFont(new File("C:/Windows/Fonts/arial.ttf"), "Arial");
            b.toStream(os);
            b.run();
        }
    }
    
	//###################################################################################################################################################
	// Hilfsmethoden
	//###################################################################################################################################################

    private static String normalizeBody(String html) {
        String h = html;
        var m = java.util.regex.Pattern.compile("(?is).*?<body[^>]*>(.*)</body>.*").matcher(h);
        if (m.matches()) h = m.group(1);

        h = h.replaceAll("(?i)\\scontenteditable\\s*=\\s*\"?true\"?", "");
        h = h.replace("&nbsp;", "&#160;");

        // Void-Tags self-closing
        String[] voidTags = {"br","hr","img","meta","link","input","source","col","base","area","embed","param","track","wbr"};
        for (String t : voidTags)
            h = h.replaceAll("(?i)<" + t + "(\\s[^>]*)?>", "<" + t + "$1/>");

        // --- Neue Regeln gegen übermäßige Abstände ---

        // Leere p direkt VOR Blockelementen entfernen
        h = h.replaceAll("(?is)<p>\\s*</p>(\\s*<(?:h[1-6]|ul|ol|table|hr)[^>]*>)", "$1");
        // Leere p direkt NACH Blockelementen entfernen
        h = h.replaceAll("(?is)(</(?:h[1-6]|ul|ol|table|hr)>\\s*)<p>\\s*</p>", "$1");

        // Mehrfache leere p zu einem einzigen <br/> reduzieren
        h = h.replaceAll("(?is)(<p>\\s*</p>){2,}", "<br/>");

        // Verbleibende einzelne leere p komplett entfernen (oder falls gewünscht: in <br/> umwandeln)
        h = h.replaceAll("(?is)<p>\\s*</p>", "");

        return h;
    }

    private static String autolinkHtml(String bodyHtml) {
        Document doc = Jsoup.parseBodyFragment(bodyHtml);

        Pattern URL  = Pattern.compile("(?i)\\b((?:https?://|www\\.)[\\w.-]+(?:/[\\w./%#?=&+-]*)?)");
        Pattern MAIL = Pattern.compile("(?i)\\b([A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,})");

        NodeTraversor.traverse(new NodeVisitor() {
            @Override public void head(Node node, int depth) {
                if (!(node instanceof TextNode tn)) return;

                String text = tn.getWholeText();
                if (text.trim().isEmpty()) return;

                Element parent = tn.parent();
                if (parent == null) return;

                String tag = parent.normalName();
                if (tag.equals("a") || tag.equals("script") || tag.equals("style") || tag.equals("code") || tag.equals("pre"))
                    return;

                String replaced = linkify(text, MAIL, m -> "<a href=\"mailto:" + m.group(1) + "\">" + m.group(1) + "</a>");
                replaced = linkify(replaced, URL, m -> {
                    String u = m.group(1);
                    String href = u.startsWith("http") ? u : "https://" + u;
                    return "<a href=\"" + href + "\">" + u + "</a>";
                });

                if (!replaced.equals(text)) {
                    int idx = tn.siblingIndex();
                    var frag = Parser.parseFragment(replaced, parent, "");
                    tn.remove();
                    parent.insertChildren(idx, frag);
                }
            }
            @Override public void tail(Node node, int depth) { }
        }, doc.body());

        return doc.body().html();
    }

    private static String linkify(String input, Pattern p, java.util.function.Function<Matcher,String> repl) {
        StringBuffer sb = new StringBuffer();
        Matcher m = p.matcher(input);
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(repl.apply(m)));
        m.appendTail(sb);
        return sb.toString();
    }

    private static String buildXhtml(String bodyContent) {
        return styleCss.formatted(bodyContent);
    }
}
