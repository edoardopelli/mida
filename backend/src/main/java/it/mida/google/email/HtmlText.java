package it.mida.google.email;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Converte il corpo HTML di un'email in testo leggibile, senza conservare
 * markup o script.
 */
public final class HtmlText {

    private HtmlText() {
    }

    public static String toText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document document = Jsoup.parse(html);
        document.select("script, style, head, meta, title, noscript").remove();
        document.select("br").append("\\n");
        for (Element block : document.select("p, div, li, tr, h1, h2, h3, h4, h5, h6, blockquote")) {
            block.prepend("\\n");
            block.append("\\n");
        }
        String text = document.body().wholeText();
        return text
                .replace("\\n", "\n")
                .replace("\r\n", "\n")
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }
}
