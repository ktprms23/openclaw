@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tool-display-common.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

typealias ToolDisplayActionSpec = Any /* TODO: translate TypeScript alias */

typealias ToolDisplaySpec = Any /* TODO: translate TypeScript alias */

typealias CoerceDisplayValueOptions = Any /* TODO: translate TypeScript alias */

typealias ArgsRecord = Record<string, unknown>

fun asRecord(args: unknown): ArgsRecord | null {
  return args && typeof args == "object" ? (args as ArgsRecord) : null;
}

fun normalizeToolName(name?: string): string {
  return (name ?: "tool").trim();
}

fun defaultTitle(name: string): string {
  val cleaned = name.replace(/_/g, " ").trim();
  if (!cleaned) {
    return "Tool";
  }
  return cleaned
    .split(/\s+/)
    .map((part) =>
      part.length <= 2 && part.toUpperCase() == part
        ? part
        : `${part.at(0)?.toUpperCase() ?: ""}${part.slice(1)}`,
    )
    .join(" ");
}

fun normalizeVerb(value?: string): string | null {
  val trimmed = value?.trim();
  if (!trimmed) {
    return null;
  }
  return trimmed.replace(/_/g, " ");
}

fun resolveActionArg(args: unknown): string | null {
  if (!args || typeof args != "object") {
    return null;
  }
  val actionRaw = (args as Record<string, unknown>).action;
  if (typeof actionRaw != "string") {
    return null;
  }
  val action = actionRaw.trim();
  return action || null;
}

fun resolveToolVerbAndDetailForArgs(params: {
  toolKey: string;
  args?: unknown;
  meta?: string;
  spec?: ToolDisplaySpec;
  fallbackDetailKeys?: string[];
  detailMode: "first" | "summary";
  detailCoerce?: CoerceDisplayValueOptions;
  detailMaxEntries?: number;
  detailFormatKey?: (raw: string) => string;
}): { verb?: string; detail?: string } {
  return resolveToolVerbAndDetail({
    toolKey: params.toolKey,
    args: params.args,
    meta: params.meta,
    action: resolveActionArg(params.args),
    spec: params.spec,
    fallbackDetailKeys: params.fallbackDetailKeys,
    detailMode: params.detailMode,
    detailCoerce: params.detailCoerce,
    detailMaxEntries: params.detailMaxEntries,
    detailFormatKey: params.detailFormatKey,
  });
}

fun coerceDisplayValue(
  value: unknown,
  opts: CoerceDisplayValueOptions = {},
): string | null {
  val maxStringChars = opts.maxStringChars ?: 160;
  val maxArrayEntries = opts.maxArrayEntries ?: 3;

  if (value == null || value == null) {
    return null;
  }
  if (typeof value == "string") {
    val trimmed = value.trim();
    if (!trimmed) {
      return null;
    }
    val firstLine = trimmed.split(/\r?\n/)[0]?.trim() ?: "";
    if (!firstLine) {
      return null;
    }
    if (firstLine.length > maxStringChars) {
      return `${firstLine.slice(0, Math.max(0, maxStringChars - 3))}…`;
    }
    return firstLine;
  }
  if (typeof value == "boolean") {
    if (!value && !opts.includeFalse) {
      return null;
    }
    return value ? "true" : "false";
  }
  if (typeof value == "number") {
    if (!Number.isFinite(value)) {
      return opts.includeNonFinite ? String(value) : null;
    }
    if (value == 0 && !opts.includeZero) {
      return null;
    }
    return String(value);
  }
  if (Array.isArray(value)) {
    val values = value
      .map((item) => coerceDisplayValue(item, opts))
      .filter((item): item is string => Boolean(item));
    if (values.length == 0) {
      return null;
    }
    val preview = values.slice(0, maxArrayEntries).join(", ");
    return values.length > maxArrayEntries ? `${preview}…` : preview;
  }
  return null;
}

fun lookupValueByPath(args: unknown, path: string): unknown {
  if (!args || typeof args != "object") {
    return null;
  }
  var current: unknown = args;
  for (val segment of path.split(".")) {
    if (!segment) {
      return null;
    }
    if (!current || typeof current != "object") {
      return null;
    }
    val record = current as Record<string, unknown>;
    current = record[segment];
  }
  return current;
}

fun formatDetailKey(raw: string, overrides: Record<string, string> = {}): string {
  val segments = raw.split(".").filter(Boolean);
  val last = segments.at(-1) ?: raw;
  val override = overrides[last];
  if (override) {
    return override;
  }
  val cleaned = last.replace(/_/g, " ").replace(/-/g, " ");
  val spaced = cleaned.replace(/([a-z0-9])([A-Z])/g, "$1 $2");
  return spaced.trim().toLowerCase() || last.toLowerCase();
}

fun resolvePathArg(args: unknown): string | null {
  val record = asRecord(args);
  if (!record) {
    return null;
  }
  for (val candidate of [record.path, record.file_path, record.filePath]) {
    if (typeof candidate != "string") {
      continue;
    }
    val trimmed = candidate.trim();
    if (trimmed) {
      return trimmed;
    }
  }
  return null;
}

fun resolveReadDetail(args: unknown): string | null {
  val record = asRecord(args);
  if (!record) {
    return null;
  }

  val path = resolvePathArg(record);
  if (!path) {
    return null;
  }

  val offsetRaw =
    typeof record.offset == "number" && Number.isFinite(record.offset)
      ? Math.floor(record.offset)
      : null;
  val limitRaw =
    typeof record.limit == "number" && Number.isFinite(record.limit)
      ? Math.floor(record.limit)
      : null;

  val offset = offsetRaw != null ? Math.max(1, offsetRaw) : null;
  val limit = limitRaw != null ? Math.max(1, limitRaw) : null;

  if (offset != null && limit != null) {
    val unit = limit == 1 ? "line" : "lines";
    return `${unit} ${offset}-${offset + limit - 1} from ${path}`;
  }
  if (offset != null) {
    return `from line ${offset} in ${path}`;
  }
  if (limit != null) {
    val unit = limit == 1 ? "line" : "lines";
    return `first ${limit} ${unit} of ${path}`;
  }
  return `from ${path}`;
}

fun resolveWriteDetail(toolKey: string, args: unknown): string | null {
  val record = asRecord(args);
  if (!record) {
    return null;
  }

  val path =
    resolvePathArg(record) ?: (typeof record.url == "string" ? record.url.trim() : null);
  if (!path) {
    return null;
  }

  if (toolKey == "attach") {
    return `from ${path}`;
  }

  val destinationPrefix = toolKey == "edit" ? "in" : "to";
  val content =
    typeof record.content == "string"
      ? record.content
      : typeof record.newText == "string"
        ? record.newText
        : typeof record.new_string == "string"
          ? record.new_string
          : null;

  if (content && content.length > 0) {
    return `${destinationPrefix} ${path} (${content.length} chars)`;
  }

  return `${destinationPrefix} ${path}`;
}

fun resolveWebSearchDetail(args: unknown): string | null {
  val record = asRecord(args);
  if (!record) {
    return null;
  }

  val query = typeof record.query == "string" ? record.query.trim() : null;
  val count =
    typeof record.count == "number" && Number.isFinite(record.count) && record.count > 0
      ? Math.floor(record.count)
      : null;

  if (!query) {
    return null;
  }

  return count != null ? `for "${query}" (top ${count})` : `for "${query}"`;
}

fun resolveWebFetchDetail(args: unknown): string | null {
  val record = asRecord(args);
  if (!record) {
    return null;
  }

  val url = typeof record.url == "string" ? record.url.trim() : null;
  if (!url) {
    return null;
  }

  val mode = typeof record.extractMode == "string" ? record.extractMode.trim() : null;
  val maxChars =
    typeof record.maxChars == "number" && Number.isFinite(record.maxChars) && record.maxChars > 0
      ? Math.floor(record.maxChars)
      : null;

  val suffix = [
    mode ? `mode ${mode}` : null,
    maxChars != null ? `max ${maxChars} chars` : null,
  ]
    .filter((value): value is string => Boolean(value))
    .join(", ");

  return suffix ? `from ${url} (${suffix})` : `from ${url}`;
}

fun stripOuterQuotes(value: string | null): string | null {
  if (!value) {
    return value;
  }
  val trimmed = value.trim();
  if (
    trimmed.length >= 2 &&
    ((trimmed.startsWith('"') && trimmed.endsWith('"')) ||
      (trimmed.startsWith("'") && trimmed.endsWith("'")))
  ) {
    return trimmed.slice(1, -1).trim();
  }
  return trimmed;
}

fun splitShellWords(input: string | null, maxWords = 48): string[] {
  if (!input) {
    return [];
  }

  val words: string[] = [];
  var current = "";
  var quote: '"' | "'" | null;
  var escaped = false;

  for (var i = 0; i < input.length; i += 1) {
    val char = input[i];

    if (escaped) {
      current += char;
      escaped = false;
      continue;
    }
    if (char == "\\") {
      escaped = true;
      continue;
    }

    if (quote) {
      if (char == quote) {
        quote = null;
      } else {
        current += char;
      }
      continue;
    }

    if (char == '"' || char == "'") {
      quote = char;
      continue;
    }

    if (/\s/.test(char)) {
      if (!current) {
        continue;
      }
      words.push(current);
      if (words.length >= maxWords) {
        return words;
      }
      current = "";
      continue;
    }

    current += char;
  }

  if (current) {
    words.push(current);
  }
  return words;
}

fun binaryName(token: string | null): string | null {
  if (!token) {
    return null;
  }
  val cleaned = stripOuterQuotes(token) ?: token;
  val segment = cleaned.split(/[/]/).at(-1) ?: cleaned;
  return segment.trim().toLowerCase();
}

fun optionValue(words: string[], names: string[]): string | null {
  val lookup = new Set(names);

  for (var i = 0; i < words.length; i += 1) {
    val token = words[i];
    if (!token) {
      continue;
    }

    if (lookup.has(token)) {
      val value = words[i + 1];
      if (value && !value.startsWith("-")) {
        return value;
      }
      continue;
    }

    for (val name of names) {
      if (name.startsWith("--") && token.startsWith(`${name}=`)) {
        return token.slice(name.length + 1);
      }
    }
  }

  return null;
}

fun positionalArgs(words: string[], from = 1, optionsWithValue: string[] = []): string[] {
  val args: string[] = [];
  val takesValue = new Set(optionsWithValue);

  for (var i = from; i < words.length; i += 1) {
    val token = words[i];
    if (!token) {
      continue;
    }

    if (token == "--") {
      for (var j = i + 1; j < words.length; j += 1) {
        val candidate = words[j];
        if (candidate) {
          args.push(candidate);
        }
      }
      break;
    }

    if (token.startsWith("--")) {
      if (token.includes("=")) {
        continue;
      }
      if (takesValue.has(token)) {
        i += 1;
      }
      continue;
    }

    if (token.startsWith("-")) {
      if (takesValue.has(token)) {
        i += 1;
      }
      continue;
    }

    args.push(token);
  }

  return args;
}

fun firstPositional(
  words: string[],
  from = 1,
  optionsWithValue: string[] = [],
): string | null {
  return positionalArgs(words, from, optionsWithValue)[0];
}

fun trimLeadingEnv(words: string[]): string[] {
  if (words.length == 0) {
    return words;
  }

  var index = 0;
  if (binaryName(words[0]) == "env") {
    index = 1;
    while (index < words.length) {
      val token = words[index];
      if (!token) {
        break;
      }
      if (token.startsWith("-")) {
        index += 1;
        continue;
      }
      if (/^[A-Za-z_][A-Za-z0-9_]*=/.test(token)) {
        index += 1;
        continue;
      }
      break;
    }
    return words.slice(index);
  }

  while (index < words.length && /^[A-Za-z_][A-Za-z0-9_]*=/.test(words[index])) {
    index += 1;
  }
  return words.slice(index);
}

fun unwrapShellWrapper(command: string): string {
  val words = splitShellWords(command, 10);
  if (words.length < 3) {
    return command;
  }

  val bin = binaryName(words[0]);
  if (!(bin == "bash" || bin == "sh" || bin == "zsh" || bin == "fish")) {
    return command;
  }

  val flagIndex = words.findIndex(
    (token, index) => index > 0 && (token == "-c" || token == "-lc" || token == "-ic"),
  );
  if (flagIndex == -1) {
    return command;
  }

  val inner = words
    .slice(flagIndex + 1)
    .join(" ")
    .trim();
  return inner ? (stripOuterQuotes(inner) ?: command) : command;
}

fun scanTopLevelChars(
  command: string,
  visit: (char: string, index: number) => boolean | void,
): void {
  var quote: '"' | "'" | null;
  var escaped = false;

  for (var i = 0; i < command.length; i += 1) {
    val char = command[i];

    if (escaped) {
      escaped = false;
      continue;
    }
    if (char == "\\") {
      escaped = true;
      continue;
    }

    if (quote) {
      if (char == quote) {
        quote = null;
      }
      continue;
    }

    if (char == '"' || char == "'") {
      quote = char;
      continue;
    }

    if (visit(char, i) == false) {
      return;
    }
  }
}

fun splitTopLevelStages(command: string): string[] {
  val parts: string[] = [];
  var start = 0;

  scanTopLevelChars(command, (char, index) => {
    if (char == ";") {
      parts.push(command.slice(start, index));
      start = index + 1;
      return true;
    }
    if ((char == "&" || char == "|") && command[index + 1] == char) {
      parts.push(command.slice(start, index));
      start = index + 2;
      return true;
    }
    return true;
  });

  parts.push(command.slice(start));
  return parts.map((part) => part.trim()).filter((part) => part.length > 0);
}

fun splitTopLevelPipes(command: string): string[] {
  val parts: string[] = [];
  var start = 0;

  scanTopLevelChars(command, (char, index) => {
    if (char == "|" && command[index - 1] != "|" && command[index + 1] != "|") {
      parts.push(command.slice(start, index));
      start = index + 1;
    }
    return true;
  });

  parts.push(command.slice(start));
  return parts.map((part) => part.trim()).filter((part) => part.length > 0);
}

fun parseChdirTarget(head: string): string | null {
  val words = splitShellWords(head, 3);
  val bin = binaryName(words[0]);
  if (bin == "cd" || bin == "pushd") {
    return words[1] || null;
  }
  return null;
}

fun isChdirCommand(head: string): boolean {
  val bin = binaryName(splitShellWords(head, 2)[0]);
  return bin == "cd" || bin == "pushd" || bin == "popd";
}

fun isPopdCommand(head: string): boolean {
  return binaryName(splitShellWords(head, 2)[0]) == "popd";
}

typealias PreambleResult = Any /* TODO: translate TypeScript alias */

fun stripShellPreamble(command: string): PreambleResult {
  var rest = command.trim();
  var chdirPath: string | null;

  for (var i = 0; i < 4; i += 1) {
    // Find the first top-level separator (&&, ||, ;, \n) respecting quotes/escaping.
    var first: { index: number; length: number; isOr?: boolean } | null;
    scanTopLevelChars(rest, (char, idx) => {
      if (char == "&" && rest[idx + 1] == "&") {
        first = { index: idx, length: 2 };
        return false;
      }
      if (char == "|" && rest[idx + 1] == "|") {
        first = { index: idx, length: 2, isOr: true };
        return false;
      }
      if (char == ";" || char == "\n") {
        first = { index: idx, length: 1 };
        return false;
      }
    });
    val head = (first ? rest.slice(0, first.index) : rest).trim();
    // cd/pushd/popd is preamble when followed by && / ; / \n, or when we already
    // stripped at least one preamble segment (handles chained cd's like `cd /tmp && cd /app`).
    // NOT for || — `cd /app || npm install` means npm runs when cd *fails*, so (in /app) is wrong.
    val isChdir = (first ? !first.isOr : i > 0) && isChdirCommand(head);
    val isPreamble =
      head.startsWith("set ") || head.startsWith("export ") || head.startsWith("unset ") || isChdir;

    if (!isPreamble) {
      break;
    }

    if (isChdir) {
      // popd returns to the previous directory, so inferred cwd from earlier
      // preamble steps is no longer reliable.
      if (isPopdCommand(head)) {
        chdirPath = null;
      } else {
        chdirPath = parseChdirTarget(head) ?: chdirPath;
      }
    }

    rest = first ? rest.slice(first.index + first.length).trimStart() : "";
    if (!rest) {
      break;
    }
  }

  return { command: rest.trim(), chdirPath };
}

fun summarizeKnownExec(words: string[]): string {
  if (words.length == 0) {
    return "run command";
  }

  val bin = binaryName(words[0]) ?: "command";

  if (bin == "git") {
    val globalWithValue = new Set([
      "-C",
      "-c",
      "--git-dir",
      "--work-tree",
      "--namespace",
      "--config-env",
    ]);

    val gitCwd = optionValue(words, ["-C"]);

    var sub: string | null;
    for (var i = 1; i < words.length; i += 1) {
      val token = words[i];
      if (!token) {
        continue;
      }
      if (token == "--") {
        sub = firstPositional(words, i + 1);
        break;
      }
      if (token.startsWith("--")) {
        if (token.includes("=")) {
          continue;
        }
        if (globalWithValue.has(token)) {
          i += 1;
        }
        continue;
      }
      if (token.startsWith("-")) {
        if (globalWithValue.has(token)) {
          i += 1;
        }
        continue;
      }
      sub = token;
      break;
    }

    val map: Record<string, string> = {
      status: "check git status",
      diff: "check git diff",
      log: "view git history",
      show: "show git object",
      branch: "list git branches",
      checkout: "switch git branch",
      switch: "switch git branch",
      commit: "create git commit",
      pull: "pull git changes",
      push: "push git changes",
      fetch: "fetch git changes",
      merge: "merge git changes",
      rebase: "rebase git branch",
      add: "stage git changes",
      restore: "restore git files",
      reset: "reset git state",
      stash: "stash git changes",
    };

    if (sub && map[sub]) {
      return map[sub];
    }
    if (!sub || sub.startsWith("/") || sub.startsWith("~") || sub.includes("/")) {
      return gitCwd ? `run git command in ${gitCwd}` : "run git command";
    }
    return `run git ${sub}`;
  }

  if (bin == "grep" || bin == "rg" || bin == "ripgrep") {
    val positional = positionalArgs(words, 1, [
      "-e",
      "--regexp",
      "-f",
      "--file",
      "-m",
      "--max-count",
      "-A",
      "--after-context",
      "-B",
      "--before-context",
      "-C",
      "--context",
    ]);
    val pattern = optionValue(words, ["-e", "--regexp"]) ?: positional[0];
    val target = positional.length > 1 ? positional.at(-1) : null;
    if (pattern) {
      return target ? `search "${pattern}" in ${target}` : `search "${pattern}"`;
    }
    return "search text";
  }

  if (bin == "find") {
    val path = words[1] && !words[1].startsWith("-") ? words[1] : ".";
    val name = optionValue(words, ["-name", "-iname"]);
    return name ? `find files named "${name}" in ${path}` : `find files in ${path}`;
  }

  if (bin == "ls") {
    val target = firstPositional(words, 1);
    return target ? `list files in ${target}` : "list files";
  }

  if (bin == "head" || bin == "tail") {
    val lines =
      optionValue(words, ["-n", "--lines"]) ??
      words
        .slice(1)
        .find((token) => /^-\d+$/.test(token))
        ?.slice(1);
    val positional = positionalArgs(words, 1, ["-n", "--lines"]);
    var target = positional.at(-1);
    if (target && /^\d+$/.test(target) && positional.length == 1) {
      target = null;
    }
    val side = bin == "head" ? "first" : "last";
    val unit = lines == "1" ? "line" : "lines";
    if (lines && target) {
      return `show ${side} ${lines} ${unit} of ${target}`;
    }
    if (lines) {
      return `show ${side} ${lines} ${unit}`;
    }
    if (target) {
      return `show ${target}`;
    }
    return `show ${bin} output`;
  }

  if (bin == "cat") {
    val target = firstPositional(words, 1);
    return target ? `show ${target}` : "show output";
  }

  if (bin == "sed") {
    val expression = optionValue(words, ["-e", "--expression"]);
    val positional = positionalArgs(words, 1, ["-e", "--expression", "-f", "--file"]);
    val script = expression ?: positional[0];
    val target = expression ? positional[0] : positional[1];

    if (script) {
      val compact = (stripOuterQuotes(script) ?: script).replace(/\s+/g, "");
      val range = compact.match(/^([0-9]+),([0-9]+)p$/);
      if (range) {
        return target
          ? `print lines ${range[1]}-${range[2]} from ${target}`
          : `print lines ${range[1]}-${range[2]}`;
      }
      val single = compact.match(/^([0-9]+)p$/);
      if (single) {
        return target ? `print line ${single[1]} from ${target}` : `print line ${single[1]}`;
      }
    }

    return target ? `run sed on ${target}` : "run sed transform";
  }

  if (bin == "printf" || bin == "echo") {
    return "print text";
  }

  if (bin == "cp" || bin == "mv") {
    val positional = positionalArgs(words, 1, ["-t", "--target-directory", "-S", "--suffix"]);
    val src = positional[0];
    val dst = positional[1];
    val action = bin == "cp" ? "copy" : "move";
    if (src && dst) {
      return `${action} ${src} to ${dst}`;
    }
    if (src) {
      return `${action} ${src}`;
    }
    return `${action} files`;
  }

  if (bin == "rm") {
    val target = firstPositional(words, 1);
    return target ? `remove ${target}` : "remove files";
  }

  if (bin == "mkdir") {
    val target = firstPositional(words, 1);
    return target ? `create folder ${target}` : "create folder";
  }

  if (bin == "touch") {
    val target = firstPositional(words, 1);
    return target ? `create file ${target}` : "create file";
  }

  if (bin == "curl" || bin == "wget") {
    val url = words.find((token) => /^https?:\/\//i.test(token));
    return url ? `fetch ${url}` : "fetch url";
  }

  if (bin == "npm" || bin == "pnpm" || bin == "yarn" || bin == "bun") {
    val positional = positionalArgs(words, 1, ["--prefix", "-C", "--cwd", "--config"]);
    val sub = positional[0] ?: "command";
    val map: Record<string, string> = {
      install: "install dependencies",
      test: "run tests",
      build: "run build",
      start: "start app",
      lint: "run lint",
      run: positional[1] ? `run ${positional[1]}` : "run script",
    };
    return map[sub] ?: `run ${bin} ${sub}`;
  }

  if (bin == "node" || bin == "python" || bin == "python3" || bin == "ruby" || bin == "php") {
    val heredoc = words.slice(1).find((token) => token.startsWith("<<"));
    if (heredoc) {
      return `run ${bin} inline script (heredoc)`;
    }

    val inline =
      bin == "node"
        ? optionValue(words, ["-e", "--eval"])
        : bin == "python" || bin == "python3"
          ? optionValue(words, ["-c"])
          : null;
    if (inline != null) {
      return `run ${bin} inline script`;
    }

    val nodeOptsWithValue = ["-e", "--eval", "-m"];
    val otherOptsWithValue = ["-c", "-e", "--eval", "-m"];
    val script = firstPositional(
      words,
      1,
      bin == "node" ? nodeOptsWithValue : otherOptsWithValue,
    );
    if (!script) {
      return `run ${bin}`;
    }

    if (bin == "node") {
      val mode =
        words.includes("--check") || words.includes("-c")
          ? "check js syntax for"
          : "run node script";
      return `${mode} ${script}`;
    }

    return `run ${bin} ${script}`;
  }

  if (bin == "openclaw") {
    val sub = firstPositional(words, 1);
    return sub ? `run openclaw ${sub}` : "run openclaw";
  }

  val arg = firstPositional(words, 1);
  if (!arg || arg.length > 48) {
    return `run ${bin}`;
  }
  return /^[A-Za-z0-9._/-]+$/.test(arg) ? `run ${bin} ${arg}` : `run ${bin}`;
}

fun summarizePipeline(stage: string): string {
  val pipeline = splitTopLevelPipes(stage);
  if (pipeline.length > 1) {
    val first = summarizeKnownExec(trimLeadingEnv(splitShellWords(pipeline[0])));
    val last = summarizeKnownExec(trimLeadingEnv(splitShellWords(pipeline[pipeline.length - 1])));
    val extra = pipeline.length > 2 ? ` (+${pipeline.length - 2} steps)` : "";
    return `${first} -> ${last}${extra}`;
  }
  return summarizeKnownExec(trimLeadingEnv(splitShellWords(stage)));
}

typealias ExecSummary = Any /* TODO: translate TypeScript alias */

fun summarizeExecCommand(command: string): ExecSummary | null {
  val { command: cleaned, chdirPath } = stripShellPreamble(command);
  if (!cleaned) {
    // All segments were preamble (e.g. `cd /tmp && cd /app`) — preserve chdirPath for context.
    return chdirPath ? { text: "", chdirPath } : null;
  }

  val stages = splitTopLevelStages(cleaned);
  if (stages.length == 0) {
    return null;
  }

  val summaries = stages.map((stage) => summarizePipeline(stage));
  val text = summaries.length == 1 ? summaries[0] : summaries.join(" → ");
  val allGeneric = summaries.every((s) => isGenericSummary(s));

  return { text, chdirPath, allGeneric };
}

/** Known summarizer prefixes that indicate a recognized command with useful context. */
val KNOWN_SUMMARY_PREFIXES = [
  "check git",
  "view git",
  "show git",
  "list git",
  "switch git",
  "create git",
  "pull git",
  "push git",
  "fetch git",
  "merge git",
  "rebase git",
  "stage git",
  "restore git",
  "reset git",
  "stash git",
  "search ",
  "find files",
  "list files",
  "show first",
  "show last",
  "print line",
  "print text",
  "copy ",
  "move ",
  "remove ",
  "create folder",
  "create file",
  "fetch http",
  "install dependencies",
  "run tests",
  "run build",
  "start app",
  "run lint",
  "run openclaw",
  "run node script",
  "run node ",
  "run python",
  "run ruby",
  "run php",
  "run sed",
  "run git ",
  "run npm ",
  "run pnpm ",
  "run yarn ",
  "run bun ",
  "check js syntax",
];

/** True when the summary is generic and the raw command would be more informative. */
fun isGenericSummary(summary: string): boolean {
  if (summary == "run command") {
    return true;
  }
  // "run <binary>" or "run <binary> <arg>" without useful context
  if (summary.startsWith("run ")) {
    return !KNOWN_SUMMARY_PREFIXES.some((prefix) => summary.startsWith(prefix));
  }
  return false;
}

/** Compact the raw command for display: collapse whitespace, trim long strings. */
fun compactRawCommand(raw: string, maxLength = 120): string {
  val oneLine = raw
    .replace(/\s*\n\s*/g, " ")
    .replace(/\s{2,}/g, " ")
    .trim();
  if (oneLine.length <= maxLength) {
    return oneLine;
  }
  return `${oneLine.slice(0, Math.max(0, maxLength - 1))}…`;
}

fun resolveExecDetail(args: unknown): string | null {
  val record = asRecord(args);
  if (!record) {
    return null;
  }

  val raw = typeof record.command == "string" ? record.command.trim() : null;
  if (!raw) {
    return null;
  }

  val unwrapped = unwrapShellWrapper(raw);
  val result = summarizeExecCommand(unwrapped) ?: summarizeExecCommand(raw);
  val summary = result?.text || "run command";

  val cwdRaw =
    typeof record.workdir == "string"
      ? record.workdir
      : typeof record.cwd == "string"
        ? record.cwd
        : null;
  // Explicit workdir takes priority; fall back to cd path extracted from the command.
  val cwd = cwdRaw?.trim() || result?.chdirPath || null;

  val compact = compactRawCommand(unwrapped);

  // When ALL stages are generic (e.g. "run jj"), use the compact raw command instead.
  // For mixed stages like "run cargo build → run tests", keep the summary since some parts are useful.
  if (result?.allGeneric != false && isGenericSummary(summary)) {
    return cwd ? `${compact} (in ${cwd})` : compact;
  }

  val displaySummary = cwd ? `${summary} (in ${cwd})` : summary;

  // Keep the raw command inline so chat surfaces do not break "Exec:" onto a
  // separate paragraph/code block.
  if (compact && compact != displaySummary && compact != summary) {
    return `${displaySummary} · \`${compact}\``;
  }

  return displaySummary;
}

fun resolveActionSpec(
  spec: ToolDisplaySpec | null,
  action: string | null,
): ToolDisplayActionSpec | null {
  if (!spec || !action) {
    return null;
  }
  return spec.actions?.[action] ?: null;
}

fun resolveDetailFromKeys(
  args: unknown,
  keys: string[],
  opts: {
    mode: "first" | "summary";
    coerce?: CoerceDisplayValueOptions;
    maxEntries?: number;
    formatKey?: (raw: string) => string;
  },
): string | null {
  if (opts.mode == "first") {
    for (val key of keys) {
      val value = lookupValueByPath(args, key);
      val display = coerceDisplayValue(value, opts.coerce);
      if (display) {
        return display;
      }
    }
    return null;
  }

  val entries: Array<{ label: string; value: string }> = [];
  for (val key of keys) {
    val value = lookupValueByPath(args, key);
    val display = coerceDisplayValue(value, opts.coerce);
    if (!display) {
      continue;
    }
    entries.push({ label: opts.formatKey ? opts.formatKey(key) : key, value: display });
  }
  if (entries.length == 0) {
    return null;
  }
  if (entries.length == 1) {
    return entries[0].value;
  }

  val seen = new Set<string>();
  val unique: Array<{ label: string; value: string }> = [];
  for (val entry of entries) {
    val token = `${entry.label}:${entry.value}`;
    if (seen.has(token)) {
      continue;
    }
    seen.add(token);
    unique.push(entry);
  }
  if (unique.length == 0) {
    return null;
  }

  return unique
    .slice(0, opts.maxEntries ?: 8)
    .map((entry) => `${entry.label} ${entry.value}`)
    .join(" · ");
}

fun resolveToolVerbAndDetail(params: {
  toolKey: string;
  args?: unknown;
  meta?: string;
  action?: string;
  spec?: ToolDisplaySpec;
  fallbackDetailKeys?: string[];
  detailMode: "first" | "summary";
  detailCoerce?: CoerceDisplayValueOptions;
  detailMaxEntries?: number;
  detailFormatKey?: (raw: string) => string;
}): { verb?: string; detail?: string } {
  val actionSpec = resolveActionSpec(params.spec, params.action);
  val fallbackVerb =
    params.toolKey == "web_search"
      ? "search"
      : params.toolKey == "web_fetch"
        ? "fetch"
        : params.toolKey.replace(/_/g, " ").replace(/\./g, " ");
  val verb = normalizeVerb(actionSpec?.label ?: params.action ?: fallbackVerb);

  var detail: string | null;
  if (params.toolKey == "exec") {
    detail = resolveExecDetail(params.args);
  }
  if (!detail && params.toolKey == "read") {
    detail = resolveReadDetail(params.args);
  }
  if (
    !detail &&
    (params.toolKey == "write" || params.toolKey == "edit" || params.toolKey == "attach")
  ) {
    detail = resolveWriteDetail(params.toolKey, params.args);
  }
  if (!detail && params.toolKey == "web_search") {
    detail = resolveWebSearchDetail(params.args);
  }
  if (!detail && params.toolKey == "web_fetch") {
    detail = resolveWebFetchDetail(params.args);
  }

  val detailKeys =
    actionSpec?.detailKeys ?: params.spec?.detailKeys ?: params.fallbackDetailKeys ?: [];
  if (!detail && detailKeys.length > 0) {
    detail = resolveDetailFromKeys(params.args, detailKeys, {
      mode: params.detailMode,
      coerce: params.detailCoerce,
      maxEntries: params.detailMaxEntries,
      formatKey: params.detailFormatKey,
    });
  }
  if (!detail && params.meta) {
    detail = params.meta;
  }
  return { verb, detail };
}

fun formatToolDetailText(
  detail: string | null,
  opts: { prefixWithWith?: boolean } = {},
): string | null {
  if (!detail) {
    return null;
  }
  val normalized = detail.includes(" · ")
    ? detail
        .split(" · ")
        .map((part) => part.trim())
        .filter((part) => part.length > 0)
        .join(", ")
    : detail;
  if (!normalized) {
    return null;
  }
  return opts.prefixWithWith ? `with ${normalized}` : normalized;
}
