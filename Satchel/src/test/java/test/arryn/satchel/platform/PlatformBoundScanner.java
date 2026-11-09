package test.arryn.satchel.platform;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Both rules of Platform-Bound Code § The check. The first: a class with a direct Forge listener carries
 * {@code @PlatformBound} with a non-empty reason. The second, further down ({@link #scanImports}): every platform
 * import is on the authorized list, or its file carries the marker or extends a Satchel type. The scanner reads a source tree as text and needs no
 * Forge classes and no parser library, so it runs under a plain {@code gradle test}. It is one file with
 * only JDK imports, so another project's build can compile it from this tree (FrontierMode's
 * {@code syncPlatformCheck} does) and point it at its own source. Keep it that way: no import outside the JDK.
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
        List<Offender> offenders = new ArrayList<>();
        List<String> listenerClasses = new ArrayList<>();
        for (Map.Entry<String, String> file : readTree(sourceRoot).entrySet()) {
            Result one = scanSource(file.getKey(), file.getValue());
            offenders.addAll(one.offenders());
            listenerClasses.addAll(one.listenerClasses());
        }
        return new Result(offenders, listenerClasses);
    }

    /** Every {@code .java} file under {@code sourceRoot}: its name relative to the root, with forward slashes, to its text. */
    private static Map<String, String> readTree(Path sourceRoot) {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            files = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the source root " + sourceRoot, e);
        }

        Map<String, String> sources = new LinkedHashMap<>();
        for (Path file : files) {
            String name = sourceRoot.relativize(file).toString().replace('\\', '/');
            try {
                sources.put(name, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read " + file, e);
            }
        }
        return sources;
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

    /**
     * A class, interface, enum, record or annotation type: where its modifiers start, its keyword and its body
     * begin, and its body ends. The text from {@code keywordStart} to {@code bodyStart} is its header.
     */
    private static final class Type {
        final String name;
        final int declStart;
        final int keywordStart;
        final int bodyStart;
        final int bodyEnd;
        final List<Annotation> annotations;

        Type(String name, int declStart, int keywordStart, int bodyStart, int bodyEnd, List<Annotation> annotations) {
            this.name = name;
            this.declStart = declStart;
            this.keywordStart = keywordStart;
            this.bodyStart = bodyStart;
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
        types.add(new Type(name, declStart, keywordStart, body, bodyEnd, annotations));
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

    // =================================================================
    // The second rule: platform imports
    // =================================================================

    /**
     * A platform class a file imports or names, and the second rule says is not allowed there.
     *
     * <p>
     * The second rule of Platform-Bound Code § The check: every {@code net.minecraft} and {@code net.minecraftforge}
     * class a file imports is on the authorized list, or the file carries {@code @PlatformBound} with a reason on
     * any of its classes, or any of its classes extends or implements a Satchel type (a class under
     * {@code com.arryn.satchel}, or a class in the scanned tree that itself extends one). The unit is the outermost
     * class: an import of {@code Entity.RemovalReason} is an import of {@code Entity}, a static import of
     * {@code Blocks.STONE} is one of {@code Blocks}, and a fully qualified name in the code counts as an import. A
     * package wildcard import cannot be checked against a list of classes, so it never passes.
     *
     * @param file     the source file, relative to the scanned root, with forward slashes
     * @param line     the line of the first import or use of the class
     * @param imported the platform class ({@code net.minecraft.world.entity.Mob}), or the package import as written
     *                 ({@code net.minecraft.world.entity.*})
     * @param problem  why it is not allowed
     */
    public record ImportOffender(String file, int line, String imported, String problem) {
        /** The line a baseline file holds for this offender: the file, two spaces, the class. */
        public String baselineLine() {
            return file + "  " + imported;
        }

        @Override
        public String toString() {
            return file + ":" + line + "  " + imported + "  " + problem;
        }
    }

    /**
     * @param offenders       every platform class a file may not import
     * @param platformClasses how many platform classes the files import or name, allowed or not, counted once per
     *                        file; a caller asserts it is not zero, so a scan that read nothing cannot pass
     */
    public record ImportResult(List<ImportOffender> offenders, int platformClasses) {
    }

    /**
     * What a baseline of known offenders leaves.
     *
     * @param fresh offenders the baseline does not list
     * @param stale baseline lines that no longer offend, because the import went or the file gained a marker
     */
    public record BaselineDiff(List<ImportOffender> fresh, List<String> stale) {
    }

    /** Where a Satchel type lives. */
    private static final String SATCHEL_PACKAGE = "com.arryn.satchel.";

    private static final String PACKAGE_IMPORT_PROBLEM =
            "a package import cannot be checked against the list; import the classes by name";

    private static final Pattern IMPORT = Pattern.compile(
            "(?<![\\w$.])import\\s+(static\\s+)?([A-Za-z_$][\\w$]*(?:\\s*\\.\\s*[A-Za-z_$][\\w$]*)*)(\\s*\\.\\s*\\*)?\\s*;");

    private static final Pattern PACKAGE = Pattern.compile(
            "(?<![\\w$.])package\\s+([A-Za-z_$][\\w$]*(?:\\s*\\.\\s*[A-Za-z_$][\\w$]*)*)\\s*;");

    /** A fully qualified platform class in code: lower-case package segments, then the first class. */
    private static final Pattern QUALIFIED_PLATFORM = Pattern.compile(
            "(?<![\\w$.])net\\.(?:minecraftforge|minecraft)(?:\\.[a-z_]\\w*)*\\.[A-Z][\\w$]*");

    private static final Pattern HEADER_KEYWORD = Pattern.compile("\\b(extends|implements|permits)\\b");

    /** The import offenders under {@code sourceRoot}; {@code authorized} is the list's fully qualified class names. */
    public static ImportResult scanImports(Path sourceRoot, Set<String> authorized) {
        return scanImportSources(readTree(sourceRoot), authorized);
    }

    /**
     * The import offenders in {@code sources}, each file's name to its text. The files are read together, since
     * whether a class extends a Satchel type can run through another class in the same tree.
     */
    public static ImportResult scanImportSources(Map<String, String> sources, Set<String> authorized) {
        List<Unit> units = new ArrayList<>();
        for (Map.Entry<String, String> source : sources.entrySet()) {
            units.add(unit(source.getKey(), source.getValue()));
        }
        Hierarchy hierarchy = new Hierarchy(units);

        List<ImportOffender> offenders = new ArrayList<>();
        int platformClasses = 0;
        for (Unit unit : units) {
            platformClasses += unit.uses.size();
            if (unit.marked) continue;
            if (unit.declared.stream().anyMatch(hierarchy::extendsSatchel)) continue;

            String problem = unit.unreasoned
                    ? "not on the authorized list, and the file's @PlatformBound has no reason"
                    : "not on the authorized list, and the file neither carries @PlatformBound nor extends a Satchel type";
            for (PlatformUse use : unit.uses) {
                if (use.packageImport()) {
                    offenders.add(new ImportOffender(unit.name, lineOf(unit.source, use.offset()), use.imported(),
                            PACKAGE_IMPORT_PROBLEM));
                } else if (!authorized.contains(use.imported())) {
                    offenders.add(new ImportOffender(unit.name, lineOf(unit.source, use.offset()), use.imported(),
                            problem));
                }
            }
        }
        return new ImportResult(offenders, platformClasses);
    }

    /**
     * Sets {@code offenders} against a baseline of known ones ({@link #baselineLines}). The baseline may only
     * shrink: an offender it does not list is fresh, and a line that no longer offends is stale.
     */
    public static BaselineDiff againstBaseline(List<ImportOffender> offenders, Set<String> baseline) {
        Set<String> offending = new TreeSet<>();
        for (ImportOffender offender : offenders) offending.add(offender.baselineLine());

        List<ImportOffender> fresh = offenders.stream().filter(o -> !baseline.contains(o.baselineLine())).toList();
        List<String> stale = baseline.stream().filter(line -> !offending.contains(line)).sorted().toList();
        return new BaselineDiff(fresh, stale);
    }

    /**
     * The lines of a baseline file, each as {@code file  class}: blank lines and comments dropped, and anything
     * after the second word ignored.
     */
    public static Set<String> baselineLines(String text) {
        Set<String> lines = new TreeSet<>();
        for (String line : text.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            String[] words = trimmed.split("\\s+");
            if (words.length < 2) {
                throw new IllegalArgumentException("A baseline line is a file and a class, not '" + trimmed + "'");
            }
            lines.add(words[0] + "  " + words[1]);
        }
        return lines;
    }

    /** The class names in the authorized list's text: the first word of each line that is not blank or a comment. */
    public static Set<String> authorizedClasses(String text) {
        Set<String> classes = new TreeSet<>();
        for (String line : text.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            classes.add(trimmed.split("\\s+")[0]);
        }
        return classes;
    }

    /** The list as the build ships it, read from {@code /platform/authorized-classes.txt} on the test classpath. */
    public static Set<String> authorizedClasses() {
        try (InputStream in = PlatformBoundScanner.class.getResourceAsStream("/platform/authorized-classes.txt")) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource /platform/authorized-classes.txt");
            }
            return authorizedClasses(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // -----------------------------------------------------------------
    // One file, read for the second rule
    // -----------------------------------------------------------------

    /** The first use of a platform class in a file: {@code imported} is the class, or the package import as written. */
    private record PlatformUse(String imported, int offset, boolean packageImport) {
    }

    /** A type a file declares, by its fully qualified name, with the names its header extends or implements. */
    private record Declared(String fqn, Unit unit, Type type, List<String> supers) {
    }

    private static final class Unit {
        final String name;
        final String source;
        final String pkg;
        /** Simple name to fully qualified name, for the file's single-type imports. */
        final Map<String, String> imports = new HashMap<>();
        final List<PlatformUse> uses = new ArrayList<>();
        final List<Declared> declared = new ArrayList<>();
        /** Some class in the file carries {@code @PlatformBound} with a reason. */
        boolean marked;
        /** Some class in the file carries {@code @PlatformBound} with no reason. */
        boolean unreasoned;

        Unit(String name, String source, String pkg) {
            this.name = name;
            this.source = source;
            this.pkg = pkg;
        }
    }

    private static Unit unit(String name, String source) {
        Stripped stripped = strip(source);
        String code = stripped.code();
        Unit unit = new Unit(name, source, packageOf(code));

        // The first use of each platform class: an import, or a fully qualified name in the code.
        Map<String, PlatformUse> uses = new LinkedHashMap<>();
        List<int[]> importSpans = new ArrayList<>();

        Matcher imports = IMPORT.matcher(code);
        while (imports.find()) {
            importSpans.add(new int[]{imports.start(), imports.end()});
            boolean isStatic = imports.group(1) != null;
            boolean wildcard = imports.group(3) != null;
            String imported = imports.group(2).replaceAll("\\s+", "");
            if (!wildcard && !isStatic) {
                unit.imports.put(imported.substring(imported.lastIndexOf('.') + 1), imported);
            }
            if (!isPlatform(imported)) continue;

            String owner = outermostClass(imported);
            if (owner != null) {
                uses.putIfAbsent(owner, new PlatformUse(owner, imports.start(), false));
            } else {
                String shown = wildcard ? imported + ".*" : imported;
                uses.putIfAbsent(shown, new PlatformUse(shown, imports.start(), true));
            }
        }

        Matcher qualified = QUALIFIED_PLATFORM.matcher(code);
        while (qualified.find()) {
            if (insideAny(importSpans, qualified.start())) continue;
            uses.putIfAbsent(qualified.group(), new PlatformUse(qualified.group(), qualified.start(), false));
        }
        unit.uses.addAll(uses.values());
        unit.uses.sort(Comparator.comparingInt(PlatformUse::offset));

        List<Type> types = types(stripped);
        for (Type type : types) {
            String dotted = displayName(enclosing(types, type));
            unit.declared.add(new Declared(qualify(unit.pkg, dotted), unit, type,
                    supertypeNames(code.substring(type.keywordStart, type.bodyStart))));
            for (Annotation annotation : type.annotations) {
                if (!annotation.name().equals("PlatformBound")) continue;
                if (hasReason(annotation.args())) unit.marked = true;
                else unit.unreasoned = true;
            }
        }
        return unit;
    }

    private static String packageOf(String code) {
        Matcher m = PACKAGE.matcher(code);
        return m.find() ? m.group(1).replaceAll("\\s+", "") : "";
    }

    private static String qualify(String pkg, String name) {
        return pkg.isEmpty() ? name : pkg + "." + name;
    }

    private static boolean isPlatform(String name) {
        return name.startsWith("net.minecraft.") || name.startsWith("net.minecraftforge.");
    }

    /**
     * The name up to and including its first class, where a package segment starts lower-case and a class upper-case,
     * or null if no segment is a class (a package import).
     */
    private static String outermostClass(String name) {
        StringBuilder owner = new StringBuilder();
        for (String segment : name.split("\\.")) {
            if (owner.length() > 0) owner.append('.');
            owner.append(segment);
            if (!segment.isEmpty() && Character.isUpperCase(segment.charAt(0))) return owner.toString();
        }
        return null;
    }

    private static boolean insideAny(List<int[]> spans, int offset) {
        for (int[] span : spans) {
            if (span[0] <= offset && offset < span[1]) return true;
        }
        return false;
    }

    /**
     * The names a type's header lists after {@code extends} and {@code implements}, with generics and a record's
     * parameters dropped first so a bound ({@code T extends Mob}) is not read as a supertype.
     */
    private static List<String> supertypeNames(String header) {
        StringBuilder flat = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < header.length(); i++) {
            char c = header.charAt(i);
            if (c == '<' || c == '(') {
                depth++;
            } else if (c == '>' || c == ')') {
                if (depth > 0) depth--;
            } else if (depth == 0) {
                flat.append(c);
            }
        }

        String text = flat.toString();
        List<String> names = new ArrayList<>();
        Matcher keyword = HEADER_KEYWORD.matcher(text);
        String kind = null;
        int segmentStart = 0;
        while (true) {
            boolean found = keyword.find();
            int segmentEnd = found ? keyword.start() : text.length();
            if (kind != null && !kind.equals("permits")) {
                for (String part : text.substring(segmentStart, segmentEnd).split(",")) {
                    String name = part.replaceAll("\\s+", "");
                    if (!name.isEmpty()) names.add(name);
                }
            }
            if (!found) break;
            kind = keyword.group(1);
            segmentStart = keyword.end();
        }
        return names;
    }

    /** Whether a declared type extends a Satchel type, directly or through other types in the scanned tree. */
    private static final class Hierarchy {
        private final Map<String, List<Declared>> index = new HashMap<>();
        private final Map<Declared, Boolean> memo = new IdentityHashMap<>();

        Hierarchy(List<Unit> units) {
            for (Unit unit : units) {
                for (Declared declared : unit.declared) {
                    index.computeIfAbsent(declared.fqn(), k -> new ArrayList<>()).add(declared);
                }
            }
        }

        boolean extendsSatchel(Declared declared) {
            Boolean known = memo.get(declared);
            if (known != null) return known;

            memo.put(declared, false);   // a cycle in the source ends here and does not loop
            boolean result = false;
            for (String name : declared.supers()) {
                if (isSatchel(declared, name)) {
                    result = true;
                    break;
                }
            }
            memo.put(declared, result);
            return result;
        }

        /** Whether the supertype {@code name}, as written in {@code from}'s header, is or extends a Satchel type. */
        private boolean isSatchel(Declared from, String name) {
            if (name.startsWith(SATCHEL_PACKAGE)) return true;

            Unit unit = from.unit();
            int dot = name.indexOf('.');
            String first = dot < 0 ? name : name.substring(0, dot);
            String imported = unit.imports.get(first);
            if (imported != null) {
                String fqn = imported + name.substring(first.length());
                return fqn.startsWith(SATCHEL_PACKAGE) || inTree(fqn, from);
            }

            // Not imported: a fully qualified name, a class in this package, or a class declared in this file.
            if (inTree(name, from) || inTree(qualify(unit.pkg, name), from)) return true;
            if (dot < 0) {
                for (Declared sibling : unit.declared) {
                    if (sibling != from && sibling.type().name.equals(name) && extendsSatchel(sibling)) return true;
                }
            }
            return false;
        }

        private boolean inTree(String fqn, Declared from) {
            for (Declared other : index.getOrDefault(fqn, List.of())) {
                if (other != from && extendsSatchel(other)) return true;
            }
            return false;
        }
    }
}
