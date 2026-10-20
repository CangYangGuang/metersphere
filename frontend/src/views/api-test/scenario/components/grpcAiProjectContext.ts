const SUPPORTED_EXTENSIONS = new Set([
  'pcp',
  'pce',
  'proto',
  'json',
  'json5',
  'yaml',
  'yml',
  'md',
  'txt',
  'csv',
  'xml',
  'dbc',
  'ini',
  'conf',
  'properties',
]);

const IGNORED_DIRECTORIES = new Set([
  '.git',
  '.idea',
  '.pytest_cache',
  '.ruff_cache',
  '.vs',
  '.vscode',
  '__pycache__',
  'build',
  'dist',
  'logs',
  'node_modules',
  'target',
]);

const MAX_DISCOVERED_FILES = 800;
const MAX_FILE_BYTES = 2 * 1024 * 1024;
const MAX_CONTEXT_CHARS = 160_000;
const MAX_SINGLE_FILE_CHARS = 32_000;

export interface ProjectFileHandle {
  kind: 'file';
  name: string;
  getFile(): Promise<File>;
}

export interface ProjectDirectoryHandle {
  kind: 'directory';
  name: string;
  values(): AsyncIterableIterator<ProjectDirectoryHandle | ProjectFileHandle>;
  queryPermission?(descriptor: { mode: 'read' }): Promise<PermissionState>;
  requestPermission?(descriptor: { mode: 'read' }): Promise<PermissionState>;
}

export interface ProjectContextResult {
  text: string;
  discoveredFileCount: number;
  selectedFiles: string[];
  truncated: boolean;
}

interface ProjectFileCandidate {
  path: string;
  extension: string;
  file: File;
  content: string;
  score: number;
}

function extensionOf(name: string) {
  const index = name.lastIndexOf('.');
  return index < 0 ? '' : name.slice(index + 1).toLowerCase();
}

function queryTokens(query: string) {
  const normalized = query.toLowerCase();
  const words = normalized.match(/[a-z0-9_.-]{2,}|[\p{Script=Han}]{2,}/gu) || [];
  const result = new Set(words);
  words
    .filter((word) => /[\p{Script=Han}]/u.test(word) && word.length > 2)
    .forEach((word) => {
      for (let index = 0; index < word.length - 1; index += 1) {
        result.add(word.slice(index, index + 2));
      }
    });
  return Array.from(result).slice(0, 80);
}

function extensionScore(extension: string) {
  if (['pcp', 'pce'].includes(extension)) return 30;
  if (extension === 'proto') return 26;
  if (['json', 'json5', 'yaml', 'yml'].includes(extension)) return 20;
  if (['md', 'txt', 'csv'].includes(extension)) return 15;
  if (['xml', 'ini', 'conf', 'properties'].includes(extension)) return 10;
  return 4;
}

function relevantExcerpt(content: string, tokens: string[], maxLength: number) {
  if (content.length <= maxLength) return content;
  const normalized = content.toLowerCase();
  const positions = tokens
    .map((token) => normalized.indexOf(token))
    .filter((position) => position >= 0)
    .slice(0, 8);
  if (!positions.length) return content.slice(0, maxLength);

  const windowSize = Math.max(1200, Math.floor(maxLength / positions.length));
  return positions
    .map((position) => {
      const start = Math.max(0, position - Math.floor(windowSize / 3));
      return content.slice(start, start + windowSize);
    })
    .join('\n...\n')
    .slice(0, maxLength);
}

async function collectFiles(
  directory: ProjectDirectoryHandle,
  prefix: string,
  result: Array<{ path: string; handle: ProjectFileHandle }>
) {
  if (result.length >= MAX_DISCOVERED_FILES) return;
  // Directory handles expose only an async iterator; sequential traversal keeps memory bounded.
  /* eslint-disable no-restricted-syntax, no-continue */
  for await (const entry of directory.values()) {
    if (result.length >= MAX_DISCOVERED_FILES) break;
    const path = prefix ? `${prefix}/${entry.name}` : entry.name;
    if (entry.kind === 'directory') {
      if (!IGNORED_DIRECTORIES.has(entry.name.toLowerCase())) {
        await collectFiles(entry, path, result);
      }
      continue;
    }
    if (SUPPORTED_EXTENSIONS.has(extensionOf(entry.name))) {
      result.push({ path, handle: entry });
    }
  }
  /* eslint-enable no-restricted-syntax, no-continue */
}

export async function ensureProjectDirectoryPermission(handle: ProjectDirectoryHandle, request = false) {
  if (!handle.queryPermission) return true;
  const descriptor = { mode: 'read' } as const;
  const current = await handle.queryPermission(descriptor);
  if (current === 'granted') return true;
  if (!request || !handle.requestPermission) return false;
  return (await handle.requestPermission(descriptor)) === 'granted';
}

export async function buildGrpcAiProjectContext(
  directory: ProjectDirectoryHandle,
  query: string
): Promise<ProjectContextResult> {
  const discovered: Array<{ path: string; handle: ProjectFileHandle }> = [];
  await collectFiles(directory, '', discovered);
  const tokens = queryTokens(query);
  const candidates: ProjectFileCandidate[] = [];

  // Files are intentionally read one at a time to avoid loading hundreds of project files concurrently.
  /* eslint-disable no-restricted-syntax, no-await-in-loop, no-continue */
  for (const item of discovered) {
    try {
      const file = await item.handle.getFile();
      if (!file.size || file.size > MAX_FILE_BYTES) continue;
      const content = (await file.text()).replace(/^\uFEFF/, '');
      if (!content.trim() || content.slice(0, 2048).includes('\0')) continue;
      const normalizedPath = item.path.toLowerCase();
      const normalizedContent = content.toLowerCase();
      let score = extensionScore(extensionOf(file.name));
      tokens.forEach((token) => {
        if (normalizedPath.includes(token)) score += 12;
        if (normalizedContent.includes(token)) score += 4;
      });
      candidates.push({
        path: item.path,
        extension: extensionOf(file.name),
        file,
        content,
        score,
      });
    } catch {
      // A single locked or unreadable project file must not block the remaining context.
    }
  }
  /* eslint-enable no-restricted-syntax, no-await-in-loop, no-continue */

  candidates.sort((left, right) => right.score - left.score || left.path.localeCompare(right.path));
  const selectedFiles: string[] = [];
  const sections: string[] = [];
  let usedChars = 0;
  // Selection stops as soon as the prompt budget is exhausted.
  // eslint-disable-next-line no-restricted-syntax
  for (const candidate of candidates.slice(0, 24)) {
    const remaining = MAX_CONTEXT_CHARS - usedChars;
    if (remaining < 500) break;
    const excerpt = relevantExcerpt(candidate.content, tokens, Math.min(MAX_SINGLE_FILE_CHARS, remaining));
    const section = `===== 本机项目文件：${candidate.path} =====\n${excerpt}`;
    sections.push(section);
    selectedFiles.push(candidate.path);
    usedChars += section.length + 2;
  }

  return {
    text: sections.join('\n\n').slice(0, MAX_CONTEXT_CHARS),
    discoveredFileCount: discovered.length,
    selectedFiles,
    truncated: discovered.length >= MAX_DISCOVERED_FILES || usedChars >= MAX_CONTEXT_CHARS,
  };
}
