package test.arryn.satchel.platform;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The first rule of Platform-Bound Code § The check: a class with a direct Forge listener carries
 * {@code @PlatformBound} with a non-empty reason. The scanner reads a source tree as text and needs no
 * Forge classes and no parser library, so it runs under a plain {@code gradle test}. It is one file with
 * only JDK imports, so another project's test can use a copy of it and point it at its own source.
 *
 * <p>
 * <b>A listener</b> is the annotation {@code @SubscribeEvent}, the annotation
 * {@code @Mod.EventBusSubscriber} (or {@code @EventBusSubscriber}), or a call to {@code .addListener(}
 * or {@code .addGenericListener(}. Comments, string literals, char literals and text blocks are blanked
 * before the search, so a Javadoc that names {@code @SubscribeEvent} is not a listener.
 *
 * <p>
 * <b>The marker</b> may sit on the class that declares the listener or on any class enclosing it. An
 * empty reason fails. A reason that is not a string literal (a constant) counts as present, since the
 * scanner cannot evaluate it.
 *
 * <p>
 * One offender is reported per class, naming its first listener's line and every listener form it uses.
 */
public final class PlatformBoundScanner {

    /**
     * A class that breaks the rule.
     *
     * @param file     the source file, relative to the scanned root, with forward slashes
     * @param line     the line of the class's first listener
     * @param type     the class, with its enclosing classes ({@code Outer.Inner})
     * @param listener the listener forms the class uses
     * @param problem  what is wrong with its marker
     */
    public record Offender(String file, int line, String type, String listener, String problem) {
        @Override
        public String toString() {
            return file + ":" + line + "  " + type + "  [" + listener + "]  " + problem;
        }
    }

    /**
     * @param offenders      every class that breaks the rule
     * @param listenerClasses every class that has a direct listener, marked or not, as {@code file:Type}; a
     *                       caller asserts it is not empty, so a scan that read nothing cannot pass
     */
    public record Result(List<Offender> offenders, List<String> listenerClasses) {
    }

    private PlatformBoundScanner() {
    }

    /** The offenders under {@code sourceRoot}. */
    public static List<Offender> scan(Path sourceRoot) {
        return scanAll(sourceRoot).offenders();
    }

    /** Every {@code .java} file under {@code sourceRoot}. */
    public static Result scanAll(Path sourceRoot) {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            files = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the source root " + sourceRoot, e);
        }

        List<Offender> offenders = new ArrayList<>();
        List<String> listenerClasses = new ArrayList<>();
        for (Path file : files) {
            String name = sourceRoot.relativize(file).toString().replace('\\', '/');
            String source;
            try {
                source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read " + file, e);
            }
            Result one = scanSource(name, source);
            offenders.addAll(one.offenders());
            listenerClasses.addAll(one.listenerClasses());
        }
        return new Result(offenders, listenerClasses);
    }

    /** One file's text, named {@code name} in the report. */
    public static Result scanSource(String name, String source) {
        Stripped stripped = strip(source);
        List<Type> types = types(stripped);
        List<Token> tokens = listenerTokens(stripped.code());

        // One group per innermost class; a listener outside any class groups under null.
        Map<Type, List<Token>> groups = new LinkedHashMap<>();
        for (Token token : tokens) {
            groups.computeIfAbsent(innermost(types, token.offset()), k -> new ArrayList<>()).add(token);
        }

        List<Offender> offenders = new ArrayList<>();
        List<String> listenerClasses = new ArrayList<>();
        for (Map.Entry<Type, List<Token>> group : groups.entrySet()) {
            Type owner = group.getKey();
            List<Token> found = group.getValue();
            int line = lineOf(source, found.get(0).offset());
            String forms = String.join(", ", distinctKinds(found));

            if (owner == null) {
                listenerClasses.add(name + ":<outside any class>");
                offenders.add(new Offender(name, line, "<outside any class>", forms,
                        "a listener outside any class declaration"));
                continue;
            }

            List<Type> chain = enclosing(types, owner);
            String typeName = displayName(chain);
            listenerClasses.add(name + ":" + typeName);

            boolean marked = false;
            boolean markedWithoutReason = false;
            for (Type type : chain) {
                for (Annotation annotation : type.annotations) {
                    if (!annotation.name().equals("PlatformBound")) continue;
                    if (hasReason(annotation.args())) marked = true;
                    else markedWithoutReason = true;
                }
            }
            if (!marked) {
                offenders.add(new Offender(name, line, typeName, forms,
                        markedWithoutReason ? "@PlatformBound has no reason" : "no @PlatformBound"));
            }
        }
        return new Result(offenders, listenerClasses);
    }

    // =================================================================
    // Stripping: comments and literals out, offsets and lines kept
    // =================================================================

    /**
     * @param text the source with comments blanked and literals kept, so a marker's reason can be read
     * @param code the source with comments and the insides of literals blanked, so a search sees only code
     */
    private record Stripped(String text, String code) {
    }

    private static Stripped strip(String src) {
        int n = src.length();
        StringBuilder text = new StringBuilder(n);
        StringBuilder code = new StringBuilder(n);

        int i = 0;
        while (i < n) {
            char c = src.charAt(i);
            char next = i + 1 < n ? src.charAt(i + 1) : '\0';

            if (c == '/' && next == '/') {
                while (i < n && src.charAt(i) != '\n' && src.charAt(i) != '\r') {
                    blank(text, code, src.charAt(i));
                    i++;
                }
            } else if (c == '/' && next == '*') {
                blank(text, code, '/');
                blank(text, code, '*');
                i += 2;
                while (i < n && !(src.charAt(i) == '*' && i + 1 < n && src.charAt(i + 1) == '/')) {
                    blank(text, code, src.charAt(i));
                    i++;
                }
                if (i < n) {
                    blank(text, code, '*');
                    blank(text, code, '/');
                    i += 2;
                }
            } else if (c == '"' && src.startsWith("\"\"\"", i)) {
                for (int k = 0; k < 3; k++) delimiter(text, code, '"');
                i += 3;
                while (i < n) {
                    char t = src.charAt(i);
                    if (t == '\\') {
                        literal(text, code, t);
                        i++;
                        if (i < n) {
                            literal(text, code, src.charAt(i));
                            i++;
                        }
                    } else if (src.startsWith("\"\"\"", i)) {
                        for (int k = 0; k < 3; k++) delimiter(text, code, '"');
                        i += 3;
                        break;
                    } else {
                        literal(text, code, t);
                        i++;
                    }
                }
            } else if (c == '"' || c == '\'') {
                char quote = c;
                delimiter(text, code, quote);
                i++;
                while (i < n) {
                    char t = src.charAt(i);
                    if (t == '\\') {
                        literal(text, code, t);
                        i++;
                        if (i < n) {
                            literal(text, code, src.charAt(i));
                            i++;
                        }
                    } else if (t == quote) {
                        delimiter(text, code, quote);
                        i++;
                        break;
                    } else if (t == '\n' || t == '\r') {
                        break;   // a literal cannot span lines: an unbalanced quote ends here
                    } else {
                        literal(text, code, t);
                        i++;
                    }
                }
            } else {
                text.append(c);
                code.append(c);
                i++;
            }
        }
        return new Stripped(text.toString(), code.toString());
    }

    private static boolean newline(char c) {
        return c == '\n' || c == '\r';
    }

    /** Comment text: a space in both copies, newlines kept so lines still count. */
    private static void blank(StringBuilder text, StringBuilder code, char c) {
        char out = newline(c) ? c : ' ';
        text.append(out);
        code.append(out);
    }

    /** Literal text: kept in {@code text}, blanked in {@code code}. */
    private static void literal(StringBuilder text, StringBuilder code, char c) {
        text.append(c);
        code.append(newline(c) ? c : ' ');
    }

    /** A quote mark: kept in both. */
    private static void delimiter(StringBuilder text, StringBuilder code, char c) {
        text.append(c);
        code.append(c);
    }

    private static int lineOf(String source, int offset) {
        int line = 1;
        int end = Math.min(offset, source.length());
        for (int i = 0; i < end; i++) {
            if (source.charAt(i) == '\n') line++;
        }
        return line;
    }

    // =================================================================
    // Type declarations
    // =================================================================

    private record Annotation(String name, String args) {
    }

    /** A class, interface, enum, record or annotation type: where its header starts and its body ends. */
    private static final class Type {
        final String name;
        final int declStart;
        final int bodyEnd;
        final List<Annotation> annotations;

        Type(String name, int declStart, int bodyEnd, List<Annotation> annotations) {
            this.name = name;
            this.declStart = declStart;
            this.bodyEnd = bodyEnd;
            this.annotations = annotations;
        }
    }

    private static final Pattern TYPE_DECLARATION = Pattern.compile(
            "(?<![\\w.@$])(?:(?:class|interface|enum)\\s+([A-Za-z_$][\\w$]*)|record\\s+([A-Za-z_$][\\w$]*)\\s*[(<])");

    private static final Pattern ANNOTATION_TYPE_DECLARATION = Pattern.compile(
            "@\\s*interface\\s+([A-Za-z_$][\\w$]*)");

    private static final Pattern ANNOTATION = Pattern.compile(
            "@\\s*((?:[A-Za-z_$][\\w$]*\\s*\\.\\s*)*[A-Za-z_$][\\w$]*)");

    private static List<Type> types(Stripped s) {
        String code = s.code();
        List<Type> types = new ArrayList<>();

        Matcher m = TYPE_DECLARATION.matcher(code);
        while (m.find()) {
            String name = m.group(1) != null ? m.group(1) : m.group(2);
            // A record's match ends inside its parameter list.
            int parens = code.charAt(m.end() - 1) == '(' ? 1 : 0;
            addType(types, s, name, m.start(), m.end(), parens);
        }
        Matcher a = ANNOTATION_TYPE_DECLARATION.matcher(code);
        while (a.find()) {
            addType(types, s, a.group(1), a.start(), a.end(), 0);
        }
        return types;
    }

    private static void addType(List<Type> types, Stripped s, String name, int keywordStart, int from, int parens) {
        String code = s.code();
        int body = bodyStart(code, from, parens);
        if (body < 0) return;   // a "class" that ends in ';' before any body is not a declaration

        int declStart = declarationStart(code, keywordStart);
        int bodyEnd = matchingBrace(code, body);
        List<Annotation> annotations = annotations(s.text().substring(declStart, keywordStart));
        types.add(new Type(name, declStart, bodyEnd, annotations));
    }

    /** The '{' that opens a declaration's body, or -1 if a ';' comes first. */
    private static int bodyStart(String code, int from, int parens) {
        int depth = parens;
        for (int i = from; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (depth <= 0 && c == ';') return -1;
            else if (depth <= 0 && c == '{') return i;
        }
        return -1;
    }

    private static int matchingBrace(String code, int open) {
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return code.length() - 1;   // unbalanced: the body runs to the end of the file
    }

    /**
     * Where a declaration's modifiers and annotations begin: just after the previous ';', '{' or '}'. A brace
     * inside an annotation's parentheses ({@code @Anno({1, 2})}) does not count.
     */
    private static int declarationStart(String code, int keywordStart) {
        int depth = 0;
        for (int i = keywordStart - 1; i >= 0; i--) {
            char c = code.charAt(i);
            if (c == ')') depth++;
            else if (c == '(') depth--;
            else if (depth <= 0 && (c == ';' || c == '{' || c == '}')) return i + 1;
        }
        return 0;
    }

    private static List<Annotation> annotations(String header) {
        List<Annotation> found = new ArrayList<>();
        Matcher m = ANNOTATION.matcher(header);
        int pos = 0;
        while (pos < header.length() && m.find(pos)) {
            String full = m.group(1).replaceAll("\\s+", "");
            String simple = full.substring(full.lastIndexOf('.') + 1);
            int end = m.end();
            String args = "";

            int after = end;
            while (after < header.length() && Character.isWhitespace(header.charAt(after))) after++;
            if (after < header.length() && header.charAt(after) == '(') {
                int close = matchingParen(header, after);
                args = header.substring(after + 1, close);
                end = Math.min(close + 1, header.length());
            }
            found.add(new Annotation(simple, args));
            pos = end;
        }
        return found;
    }

    /** The ')' that closes the '(' at {@code open}, or the end of the text if there is none. */
    private static int matchingParen(String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return text.length();
    }

    private static final Pattern REASON = Pattern.compile(
            "\\breason\\s*=\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|([^,)\\s][^,)]*))");

    /** Whether the marker's arguments give a reason: a string with something in it, or an expression. */
    private static boolean hasReason(String args) {
        Matcher m = REASON.matcher(args);
        if (!m.find()) return false;
        if (m.group(1) != null) return !m.group(1).trim().isEmpty();
        return true;
    }

    /** The innermost type whose header or body holds {@code offset}, or null if none does. */
    private static Type innermost(List<Type> types, int offset) {
        Type best = null;
        for (Type type : types) {
            if (type.declStart <= offset && offset <= type.bodyEnd
                    && (best == null || type.declStart > best.declStart)) {
                best = type;
            }
        }
        return best;
    }

    /** {@code type} and every type that encloses it, outermost first. */
    private static List<Type> enclosing(List<Type> types, Type type) {
        List<Type> chain = new ArrayList<>();
        for (Type candidate : types) {
            if (candidate.declStart <= type.declStart && type.bodyEnd <= candidate.bodyEnd) chain.add(candidate);
        }
        chain.sort(Comparator.comparingInt(t -> t.declStart));
        return chain;
    }

    private static String displayName(List<Type> chain) {
        StringBuilder name = new StringBuilder();
        for (Type type : chain) {
            if (name.length() > 0) name.append('.');
            name.append(type.name);
        }
        return name.toString();
    }

    // =================================================================
    // Listeners
    // =================================================================

    private record Token(int offset, String kind) {
    }

    private static final Pattern LISTENER_ANNOTATION = Pattern.compile(
            "@\\s*(?:[A-Za-z_$][\\w$]*\\s*\\.\\s*)*(?:SubscribeEvent|EventBusSubscriber)(?![\\w$])");

    private static final Pattern LISTENER_CALL = Pattern.compile(
            "\\.\\s*(addListener|addGenericListener)\\s*\\(");

    private static List<Token> listenerTokens(String code) {
        List<Token> tokens = new ArrayList<>();

        Matcher annotation = LISTENER_ANNOTATION.matcher(code);
        while (annotation.find()) {
            tokens.add(new Token(annotation.start(), annotation.group().replaceAll("\\s+", "")));
        }
        Matcher call = LISTENER_CALL.matcher(code);
        while (call.find()) {
            tokens.add(new Token(call.start(), call.group(1) + "("));
        }
        tokens.sort(Comparator.comparingInt(Token::offset));
        return tokens;
    }

    private static List<String> distinctKinds(List<Token> tokens) {
        List<String> kinds = new ArrayList<>();
        for (Token token : tokens) {
            if (!kinds.contains(token.kind())) kinds.add(token.kind());
        }
        return kinds;
    }
}
