package org.andy.html2pdf;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.jsoup.nodes.Document.OutputSettings.Syntax;
import org.jsoup.helper.W3CDom;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Runner {
	
	//###################################################################################################################################################
	// public Teil
	//###################################################################################################################################################
	
    public static void main(String[] args) {
    	
        if (args.length != 3) {
            System.err.println("Usage: java -jar html2pdf-runner.jar <style> <htmlIn> <pdfOut>");
            System.exit(1);
        }
        
        String styleCss = args[0];
        Path htmlIn = Path.of(args[1]);
        Path pdfOut = Path.of(args[2]);
        
        try {
			doRender(styleCss, htmlIn, pdfOut);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(11);
		}
    }

	//###################################################################################################################################################
	// private Teil
	//###################################################################################################################################################

    private static void doRender(String css, Path inHtml, Path ouPdf) throws Exception {
    	
        String content = null;
		try {
			content = Files.readString(inHtml);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(12);
		}
    	
    	String raw = content;                       // aus WebView
		String cleaned = normalizeFragment(raw);    // Schritt 1
		String html = buildPrintHtml(css, cleaned); // Schritt 2, css wird mitgeliefert
		
		html = stripBomAndDoctype(html);
		org.w3c.dom.Document w3c = toW3c(html);
		
		var b = new com.openhtmltopdf.pdfboxout.PdfRendererBuilder();
		b.useFastMode();
		b.withW3cDocument(w3c, null);                // statt withHtmlContent(...)
		b.toStream(java.nio.file.Files.newOutputStream(ouPdf));
		b.run();
		
    }
    
	//###################################################################################################################################################
	// Hilfsmethoden
	//###################################################################################################################################################

    static String normalizeFragment(String fragment) {
	    org.jsoup.nodes.Document doc = Jsoup.parseBodyFragment(fragment);
	    // p-Elemente, die Block-Überschriften enthalten, auflösen
	    for (org.jsoup.nodes.Element p : doc.select("p:has(h1,h2,h3,h4,h5,h6,ul,ol,pre,blockquote)")) {
	        p.unwrap();
	    }
	    // leere p mit Höhe -> durch margin ersetzen
	    for (org.jsoup.nodes.Element p : doc.select("p[style]")) {
	        String s = p.attr("style");
	        if (p.text().isBlank() && s.matches("(?i).*height\\s*:\\s*\\d+(\\.\\d+)?(em|px).*")) {
	            p.after("<div style=\"margin:0.6em 0\"></div>");
	            p.remove();
	        }
	    }
	    return doc.body().html();
	}
    
    //###################################################################################################################################################
	
	static String buildPrintHtml(String css, String fragment) {
		String prefix = """
						<!doctype html><html><head><meta charset="UTF-8">
				      	<style>%s</style></head><body>
				      	<div id="content">%s</div>
				      	</body></html>
				      	""";
		
	    return prefix.formatted(css, fragment);
	}
	
	//###################################################################################################################################################
	
	static String stripBomAndDoctype(String s) {
        // BOM weg + HTML5-Doctype entfernen
        return s.replaceFirst("^\uFEFF", "")
                .replaceFirst("(?is)<!doctype[^>]*>", "");
    }
	
	//###################################################################################################################################################

	static org.w3c.dom.Document toW3c(String html) {
	    Document js = Jsoup.parse(html);
	    js.outputSettings()
	      .syntax(Syntax.xml)                    // XHTML
	      .escapeMode(Entities.EscapeMode.xhtml)
	      .charset(java.nio.charset.StandardCharsets.UTF_8);
	    return new W3CDom().fromJsoup(js);
	}
	
	//###################################################################################################################################################
    
}
