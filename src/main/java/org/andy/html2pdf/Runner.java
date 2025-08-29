package org.andy.html2pdf;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.PageSizeUnits;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Aufruf: java -jar html2pdf-runner.jar <htmlIn> <pdfOut> [baseUrlOrDash]
 * Exitcodes: 0 ok, 1 Usage, 2 IO, 3 Renderfehler
 */
public final class Runner {
	
	//###################################################################################################################################################
	// public Teil
	//###################################################################################################################################################
	
	public static void main(String[] args) {
		if (args.length < 2 || args.length > 3) {
			System.err.println("Usage: java -jar html2pdf-runner.jar <htmlIn> <pdfOut> [baseUrl|-]");
			System.exit(1);
		}
		Path htmlIn = Path.of(args[0]);
		Path pdfOut = Path.of(args[1]);
		String baseUrl = args.length >= 3 && !"-".equals(args[2]) ? args[2] : null;

		try {
			render(htmlIn, pdfOut.toFile(), baseUrl);
		} catch (IOException e) {
			e.printStackTrace(System.err);
			System.exit(2);
		} catch (Exception e) {
			e.printStackTrace(System.err);
			System.exit(3);
		}
	}
	
	//###################################################################################################################################################
	// private Teil
	//###################################################################################################################################################
	
	private static void render(Path htmlIn, File outPdf, String baseUrl) throws Exception {
		String html = Files.readString(htmlIn, StandardCharsets.UTF_8);
		String body = normalizeBody(html); // <body> bereinigen für sauberes XHTML
		String xhtml = buildXhtml(body, null); // oder zusätzliches CSS übergeben
		try (OutputStream os = new FileOutputStream(outPdf)) {
			PdfRendererBuilder b = new PdfRendererBuilder();
			b.useFastMode();
			b.useDefaultPageSize(210, 297, PageSizeUnits.MM);
			b.withHtmlContent(xhtml, baseUrl);
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

	    String[] voidTags = {"br","hr","img","meta","link","input","source","col","base","area","embed","param","track","wbr"};
	    for (String t : voidTags)
	        h = h.replaceAll("(?i)<" + t + "(\\s[^>]*)?>", "<" + t + "$1/>");

	    h = h.replaceAll("(?is)<p>\\s*</p>", "<p>&#160;</p>");
	    return h; // nur der bereinigte Body-Inhalt
	}
	
	private static String buildXhtml(String bodyContent, String extraCss) {
	    return """
	    <?xml version="1.0" encoding="UTF-8"?>
	    <html xmlns="http://www.w3.org/1999/xhtml"><head>
	      <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
	      <style>
	        @page { size: 210mm 297mm; margin: 130pt 55pt 60pt 55pt; } /* (top, right, bottom, left) */
	        @page:first { margin-top: 130pt; }                         /* Optional: Platz für Kopf auf Seite 1 */
	        html, body { margin:0; padding:0; }
	        body { font-family: Arial, sans-serif; font-size: 10pt; }
	        %s
	      </style>
	    </head><body>%s</body></html>
	    """.formatted(extraCss == null ? "" : extraCss, bodyContent);
	}

}
