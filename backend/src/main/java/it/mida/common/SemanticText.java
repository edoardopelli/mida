package it.mida.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Normalizzazione e impronte usate per riconoscere lo stesso dato DidUP tra
 * esportazioni diverse.
 *
 * <p>DidUP non include un identificativo stabile per singolo compito o ora del
 * diario. L'identità viene quindi ricostruita dal contenuto: maiuscole,
 * spazi, punteggiatura e accenti vengono normalizzati prima del confronto. Il
 * testo originale resta comunque quello mostrato all'utente.</p>
 */
public final class SemanticText {

    private static final double REVISION_THRESHOLD = 0.75;

    private SemanticText() {
    }

    /**
     * Forma canonica usata per hash e confronti: Unicode NFKC, minuscole,
     * solo lettere e numeri, spazi singoli.
     */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    public static String digest(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                digest.update(normalize(part).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0x1F);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 non disponibile", ex);
        }
    }

    /**
     * Punteggio di revisione tra due descrizioni dello stesso slot DidUP.
     * Restituisce un valore negativo quando le descrizioni rappresentano due
     * compiti distinti.
     *
     * <p>Regole:</p>
     * <ul>
     *   <li>testo identico dopo normalizzazione: stessa revisione;</li>
     *   <li>una descrizione contiene l'altra (testo abbastanza lungo):
     *       estensione o correzione, quindi stessa revisione;</li>
     *   <li>stessi numeri e forte somiglianza lessicale: stessa revisione;</li>
     *   <li>numeri diversi senza contenimento: compiti distinti, così
     *       "pagina 10" e "pagina 11" non vengono mai fusi.</li>
     * </ul>
     */
    public static double revisionScore(String existing, String incoming) {
        String left = normalize(existing);
        String right = normalize(incoming);
        if (left.equals(right)) {
            return 1.0;
        }
        if (left.isEmpty() || right.isEmpty()) {
            return -1.0;
        }

        boolean contains = left.contains(right) || right.contains(left);
        if (contains && Math.min(left.length(), right.length()) >= 12) {
            return 0.99;
        }

        Set<String> leftTokens = tokens(left);
        Set<String> rightTokens = tokens(right);
        if (!numericTokens(leftTokens).equals(numericTokens(rightTokens))) {
            return -1.0;
        }
        double score = jaccard(leftTokens, rightTokens);
        return score >= REVISION_THRESHOLD ? score : -1.0;
    }

    public static boolean sameOrRevision(String existing, String incoming) {
        return revisionScore(existing, incoming) >= 0;
    }

    private static Set<String> tokens(String normalized) {
        Set<String> tokens = new LinkedHashSet<>();
        if (normalized.isBlank()) {
            return tokens;
        }
        for (String token : normalized.split(" ")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static Set<String> numericTokens(Set<String> tokens) {
        Set<String> numeric = new LinkedHashSet<>();
        for (String token : tokens) {
            if (token.chars().anyMatch(Character::isDigit)) {
                numeric.add(token);
            }
        }
        return numeric;
    }

    private static double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() && right.isEmpty()) {
            return 1.0;
        }
        Set<String> intersection = new LinkedHashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new LinkedHashSet<>(left);
        union.addAll(right);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }
}
