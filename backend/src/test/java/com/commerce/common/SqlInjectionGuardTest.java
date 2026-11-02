package com.commerce.common;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Regression guard for SQL injection. It reads the backend source and fails when a query is built from anything other
 * than string literals plus a short, reviewed list of constant fragments, or when a native {@code @Query} appears.
 * User input must always reach the database as a bound parameter (":name" or "?1"), never inside the query text.
 *
 * If this fails after you add a query: bind the value with setParameter / @Param instead of concatenating it. Only add
 * a name to ALLOWED_FRAGMENTS when it is a compile-time constant or a server-built clause that contains no user input.
 */
class SqlInjectionGuardTest {
    /** Identifiers that may be concatenated into query text: server-built clauses and constants, never request data. */
    static final Set<String> ALLOWED_FRAGMENTS = Set.of("base", "order", "LIVE");

    private static final Pattern CALL = Pattern.compile("(createQuery|createNativeQuery|@Query)\\s*\\(");
    private static final Pattern IDENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    @Test
    void productionSourceBuildsQueriesOnlyFromLiteralsAndReviewedFragments() throws IOException {
        Path root = Path.of("src/main/java");
        assertTrue(Files.isDirectory(root), "Run from the backend module directory; not found: " + root.toAbsolutePath());
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                problems.addAll(scan(root.relativize(p).toString(), Files.readString(p)));
            }
        }
        assertTrue(problems.isEmpty(), "Possible SQL injection risk:\n" + String.join("\n", problems));
    }

    @Test
    void scannerAcceptsBoundParametersAndReviewedFragments() {
        String ok = """
                em.createQuery("select p from Product p where p.id = :id", Product.class).setParameter("id", id);
                em.createQuery("select count(p)" + base, Long.class);
                em.createQuery("select p" + base + order, Product.class);
                em.createNativeQuery("insert into t (a, b) values (?1, ?2)");
                @Query("select u from AppUser u where u.name = :n " + "and u.x = :x")
                """;
        assertEquals(List.of(), scan("Ok.java", ok));
    }

    @Test
    void scannerRejectsConcatenatedUserInput() {
        assertEquals(1, scan("Bad.java", "em.createQuery(\"select p from Product p where p.sku = '\" + sku + \"'\", Product.class);").size());
        assertEquals(1, scan("Bad.java", "em.createNativeQuery(\"select * from users where name = \" + name);").size());
        assertEquals(1, scan("Bad.java", "@Query(\"select u from AppUser u order by \" + sort)").size());
        assertEquals(1, scan("Bad.java", "em.createQuery(String.format(\"select p from Product p where p.sku = '%s'\", sku));").size());
    }

    @Test
    void scannerRejectsNativeRepositoryQueries() {
        assertEquals(1, scan("Bad.java", "@Query(value = \"select 1\", nativeQuery = true)").size());
    }

    // ---- scanner (pure, so it can be tested without touching the file system)

    static List<String> scan(String file, String src) {
        List<String> out = new ArrayList<>();
        Matcher m = CALL.matcher(src);
        while (m.find()) {
            boolean repoQuery = "@Query".equals(m.group(1));
            int open = m.end() - 1;
            String args = argument(src, open, !repoQuery);
            String code = stripLiterals(args);
            int line = 1 + (int) src.substring(0, m.start()).chars().filter(c -> c == '\n').count();
            if (repoQuery && code.matches("(?s).*nativeQuery\\s*=\\s*true.*")) {
                out.add(file + ":" + line + " native @Query needs a manual security review");
                continue;
            }
            Matcher id = IDENT.matcher(code);
            while (id.find()) {
                String name = id.group();
                boolean annotationKeyword = repoQuery && Set.of("value", "nativeQuery", "false").contains(name);
                if (!annotationKeyword && !ALLOWED_FRAGMENTS.contains(name)) {
                    out.add(file + ":" + line + " query text is built from '" + name + "' - bind it as a parameter instead");
                    break;
                }
            }
        }
        return out;
    }

    /** Text of the call's first argument (or the whole annotation body), honouring string literals and nested parentheses. */
    private static String argument(String s, int open, boolean stopAtComma) {
        int depth = 0;
        boolean inString = false;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (c == '\\') i++;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return s.substring(open + 1, i);
            } else if (c == ',' && depth == 1 && stopAtComma) return s.substring(open + 1, i);
        }
        return s.substring(open + 1);
    }

    private static String stripLiterals(String s) {
        StringBuilder b = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (c == '\\') i++;
                else if (c == '"') inString = false;
            } else if (c == '"') inString = true;
            else b.append(c);
        }
        return b.toString();
    }
}
