import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * i18n 种子数据生成器。
 *
 * <p>读取 backend properties、frontend TS、V103 菜单 en 翻译，输出幂等 INSERT 语句到 stdout。
 * 运行后重定向输出追加到 V104__i18n_init.sql。
 *
 * <p>编译运行（在 backend 目录）：
 *
 * <pre>
 * javac -d /tmp/i18ngen modules/i18n/src/main/scripts/I18nSeedGenerator.java
 * java -cp /tmp/i18ngen I18nSeedGenerator > /tmp/seed.sql
 * </pre>
 */
public class I18nSeedGenerator {

  static class Entry {
    String key;
    String locale;
    String module;
    String value;

    Entry(String key, String locale, String module, String value) {
      this.key = key;
      this.locale = locale;
      this.module = module;
      this.value = value;
    }
  }

  static final Set<String> seen = new HashSet<>();
  static final List<Entry> entries = new ArrayList<>();

  static void add(String key, String locale, String module, String value) {
    if (key == null || key.isBlank() || value == null || value.isBlank()) {
      return;
    }
    String dedupe = key + "\u0000" + locale;
    if (!seen.add(dedupe)) {
      return;
    }
    entries.add(new Entry(key, locale, module, value));
  }

  static String backendModule(String key) {
    if (key.startsWith("validation.app.")) {
      return "openapp";
    }
    if (key.startsWith("validation.user.")
        || key.startsWith("validation.role.")
        || key.startsWith("validation.menu.")
        || key.startsWith("validation.config.")
        || key.startsWith("validation.unit.")
        || key.startsWith("validation.login.")) {
      return "sys";
    }
    if (key.startsWith("sys.")) {
      return "sys";
    }
    return "platform-common";
  }

  // key like common.save, sys.user.username -> module
  static String frontendModule(String key) {
    if (key.startsWith("sys.")) {
      return "sys";
    }
    if (key.startsWith("i18n.")) {
      return "i18n";
    }
    return "frontend";
  }

  static void loadProperties(Path file, String locale) throws IOException {
    if (!Files.exists(file)) {
      return;
    }
    for (String line : Files.readAllLines(file)) {
      line = line.trim();
      if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
        continue;
      }
      int eq = line.indexOf('=');
      if (eq < 0) {
        continue;
      }
      String key = line.substring(0, eq).trim();
      String value = line.substring(eq + 1).trim();
      add(key, locale, backendModule(key), value);
    }
  }

  // Parse a TS object literal file, flattening keys with the given prefix.
  static void parseTs(Path file, String locale, String prefix, String module) throws IOException {
    if (!Files.exists(file)) {
      return;
    }
    String content = Files.readString(file);
    walk(content, prefix, locale, module);
  }

  // Recursive-descent-ish walk over the raw content with a key-stack.
  static void walk(String content, String basePrefix, String locale, String module) {
    int i = 0;
    int n = content.length();
    List<String> stack = new ArrayList<>();
    stack.add(basePrefix);
    while (i < n) {
      char c = content.charAt(i);
      if (c == '/' && i + 1 < n && content.charAt(i + 1) == '/') {
        // line comment: skip to end of line
        int nl = content.indexOf('\n', i);
        i = nl < 0 ? n : nl + 1;
        continue;
      }
      if (c == '{') {
        // find the preceding key token (identifier or quoted) ending right before ':' before this {
        String key = keyBefore(content, i);
        if (key != null) {
          stack.add(stack.get(stack.size() - 1) + key + ".");
        } else {
          stack.add(stack.get(stack.size() - 1)); // the root object literal (export default {)
        }
        i++;
        continue;
      }
      if (c == '}') {
        if (stack.size() > 1) {
          stack.remove(stack.size() - 1);
        }
        i++;
        continue;
      }
      if (c == '\'') {
        // could be a quoted value OR a quoted key. Check if this string is followed by ':' (key) or
        // not (value).
        int end = findStringEnd(content, i);
        if (end < 0) {
          break;
        }
        String str = unescape(content.substring(i + 1, end));
        // look ahead past closing quote for ':'
        int j = end + 1;
        while (j < n && Character.isWhitespace(content.charAt(j))) {
          j++;
        }
        if (j < n && content.charAt(j) == ':') {
          // it is a quoted key; the opening brace / value handled on later iterations.
          // mark this key as the next push target; but our '{' handler already extracts keyBefore.
          // For quoted-key leaf values (no nesting), handle by detecting value after ':'.
          // Find the value following ':'.
          int colon = j;
          int k = colon + 1;
          while (k < n && Character.isWhitespace(content.charAt(k))) {
            k++;
          }
          if (k < n && content.charAt(k) != '{') {
            String value = readValue(content, k);
            if (value != null) {
              add(stack.get(stack.size() - 1) + str, locale, module, value);
            }
          }
          // if next is '{', the '{' handler will push using keyBefore which finds this quoted key
          i = end + 1;
          continue;
        }
        // otherwise it is a value; but we need the preceding key. The identifier-key leaf case is
        // handled when we detect an identifier. Skip the string.
        i = end + 1;
        continue;
      }
      if (Character.isJavaIdentifierStart(c)) {
        // read identifier
        int start = i;
        while (i < n && Character.isJavaIdentifierPart(content.charAt(i))) {
          i++;
        }
        String ident = content.substring(start, i);
        // look for ':' after optional whitespace
        int j = i;
        while (j < n && Character.isWhitespace(content.charAt(j))) {
          j++;
        }
        if (j < n && content.charAt(j) == ':') {
          int k = j + 1;
          while (k < n && Character.isWhitespace(content.charAt(k))) {
            k++;
          }
          if (k < n && content.charAt(k) != '{') {
            String value = readValue(content, k);
            if (value != null) {
              add(stack.get(stack.size() - 1) + ident, locale, module, value);
            }
          }
          // if '{' follows, the '{' handler will push. Continue scanning from current i (the ident
          // end) so the '{' handler sees it.
          continue;
        }
        continue;
      }
      i++;
    }
  }

  static String keyBefore(String content, int braceIdx) {
    // scan backwards skipping whitespace; expect ':' then a key token (ident or quoted)
    int j = braceIdx - 1;
    while (j >= 0 && Character.isWhitespace(content.charAt(j))) {
      j--;
    }
    if (j < 0 || content.charAt(j) != ':') {
      return null;
    }
    j--;
    while (j >= 0 && Character.isWhitespace(content.charAt(j))) {
      j--;
    }
    if (j < 0) {
      return null;
    }
    if (content.charAt(j) == '\'') {
      int end = j;
      j--;
      while (j >= 0 && content.charAt(j) != '\'') {
        j--;
      }
      if (j < 0) {
        return null;
      }
      return content.substring(j + 1, end);
    }
    int end = j + 1;
    while (j >= 0 && Character.isJavaIdentifierPart(content.charAt(j))) {
      j--;
    }
    return content.substring(j + 1, end);
  }

  static int findStringEnd(String content, int start) {
    // start points at opening quote
    int i = start + 1;
    while (i < content.length()) {
      char c = content.charAt(i);
      if (c == '\\' && i + 1 < content.length()) {
        i += 2;
        continue;
      }
      if (c == '\'') {
        return i;
      }
      i++;
    }
    return -1;
  }

  static String readValue(String content, int k) {
    if (k >= content.length()) {
      return null;
    }
    char c = content.charAt(k);
    if (c == '\'') {
      int end = findStringEnd(content, k);
      if (end < 0) {
        return null;
      }
      return unescape(content.substring(k + 1, end));
    }
    return null;
  }

  static String unescape(String s) {
    return s.replace("\\'", "'").replace("\\\\", "\\").replace("\\n", "\n").replace("\\t", "\t");
  }

  static String sqlEscape(String s) {
    return s.replace("'", "''");
  }

  // V103 en menu translations (menu_id -> en display name), transcribed from the migration.
  static final LinkedHashMap<Long, String> EN_MENUS = new LinkedHashMap<>();

  static {
    Object[][] data = {
      {1L, "System"}, {2L, "Users"}, {3L, "Add User"}, {4L, "Edit User"},
      {5L, "Delete User"}, {6L, "Reset Password"}, {7L, "Assign Roles"},
      {8L, "Unlock User"}, {10L, "Roles"}, {11L, "Add Role"}, {12L, "Edit Role"},
      {13L, "Delete Role"}, {14L, "Permissions"}, {20L, "Menus"}, {21L, "Add Menu"},
      {22L, "Edit Menu"}, {23L, "Delete Menu"}, {30L, "Units"}, {31L, "Add Unit"},
      {32L, "Edit Unit"}, {33L, "Delete Unit"}, {40L, "Config"}, {41L, "Add Config"},
      {42L, "Edit Config"}, {50L, "Sessions"}, {51L, "Audit Log"}, {55L, "Publish"}
    };
    for (Object[] d : data) {
      EN_MENUS.put((Long) d[0], (String) d[1]);
    }
  }

  public static void main(String[] args) throws IOException {
    Path repoRoot = Paths.get(args.length > 0 ? args[0] : ".");
    Path beProps = repoRoot.resolve("backend/platform-common/src/main/resources/i18n");
    Path feLocales = repoRoot.resolve("frontend/src/i18n/locales");

    // 1. backend properties
    loadProperties(beProps.resolve("messages.properties"), "zh-CN");
    loadProperties(beProps.resolve("messages_en.properties"), "en");

    // 2. frontend TS files (exclude index.ts aggregation and translation.ts legacy)
    String[] tsFiles = {
      "common", "validation", "route", "login", "layout", "dashboard", "notFound", "error", "i18n",
      "sys/user", "sys/role", "sys/menu", "sys/unit", "sys/config", "sys/message",
      "sys/session", "sys/audit", "sys/app"
    };
    for (String name : tsFiles) {
      String prefix;
      String module;
      if (name.startsWith("sys/")) {
        prefix = "sys." + name.substring(4) + ".";
        module = "sys";
      } else if (name.equals("i18n")) {
        prefix = "i18n.";
        module = "i18n";
      } else {
        prefix = name + ".";
        module = "frontend";
      }
      parseTs(feLocales.resolve("zh-CN/" + name + ".ts"), "zh-CN", prefix, module);
      parseTs(feLocales.resolve("en/" + name + ".ts"), "en", prefix, module);
    }

    // 3. i18n-module runtime service keys (not in any source file)
    add("i18n.message.not.found", "zh-CN", "i18n", "翻译不存在: {0}");
    add("i18n.message.not.found", "en", "i18n", "Translation not found: {0}");
    add("i18n.import.unknown.keys", "zh-CN", "i18n", "导入失败，包含未知 key: {0}");
    add("i18n.import.unknown.keys", "en", "i18n", "Import failed, contains unknown keys: {0}");

    // 4. menu en translations (from V103)
    for (Map.Entry<Long, String> e : EN_MENUS.entrySet()) {
      add("sys.menu." + e.getKey() + ".name", "en", "sys", e.getValue());
    }

    // 5. emit SQL
    StringBuilder sb = new StringBuilder();
    sb.append("-- ===== Generated i18n seed data (").append(entries.size()).append(" rows) =====\n");
    for (Entry e : entries) {
      // zh-CN menu name keys are seeded at runtime via SELECT from sys_menu (see below block);
      // skip emitting static zh-CN sys.menu.<n>.name rows to avoid redundancy — they are generated
      // by the dynamic INSERT...SELECT in the SQL file.
      if (e.locale.equals("zh-CN") && e.key.matches("sys\\.menu\\.\\d+\\.name")) {
        continue;
      }
      sb.append(
          String.format(
              "INSERT INTO i18n_message (message_key, locale, module, value) "
                  + "SELECT '%s', '%s', '%s', '%s' "
                  + "WHERE NOT EXISTS (SELECT 1 FROM i18n_message m "
                  + "WHERE m.message_key = '%s' AND m.locale = '%s');\n",
              sqlEscape(e.key),
              e.locale,
              sqlEscape(e.module),
              sqlEscape(e.value),
              sqlEscape(e.key),
              e.locale));
    }

    // zh-CN menu names: dynamic from sys_menu
    sb.append("\n-- zh-CN menu names: seeded from sys_menu.menu_name at runtime\n");
    sb.append(
        "INSERT INTO i18n_message (message_key, locale, module, value) "
            + "SELECT 'sys.menu.' || sm.id || '.name', 'zh-CN', 'sys', sm.menu_name "
            + "FROM sys_menu sm "
            + "WHERE NOT EXISTS (SELECT 1 FROM i18n_message m "
            + "WHERE m.message_key = 'sys.menu.' || sm.id || '.name' AND m.locale = 'zh-CN');\n");

    System.out.print(sb);
    System.err.println("Generated " + entries.size() + " seed entries");
  }
}
