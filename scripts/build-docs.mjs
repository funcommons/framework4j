#!/usr/bin/env node
/**
 * 一键构建文档站 → 部署到 demo 静态资源
 *
 * 3 步：
 *   1. 复制各模块 README.md → docs/modules/*.md（替代软链接，跨平台可移植）
 *   2. VitePress 编译静态 HTML → docs/.vitepress/dist/
 *   3. 复制 dist/ → framework4j-demo/src/main/resources/static/docs/
 *
 * 用法：npm run docs:deploy
 */
import { copyFileSync, readFileSync, writeFileSync, mkdirSync, rmSync, existsSync, readdirSync } from 'node:fs';
import { join, resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execSync } from 'node:child_process';

const __dirname = dirname(fileURLToPath(import.meta.url));
const root = resolve(__dirname, '..');

// 模块 → README 路径映射
const modules = {
  'api':          'framework4j-api/README.md',
  'web':          'framework4j-web/README.md',
  'accesstoken':  'framework4j-accesstoken/README.md',
  'signature':    'framework4j-signature/README.md',
  'rate-limit':   'framework4j-rate-limit/README.md',
  'cache':        'framework4j-cache/README.md',
  'audit':        'framework4j-audit/README.md',
  'sensitive':    'framework4j-sensitive/README.md',
  'idempotency':  'framework4j-idempotency/README.md',
  'redis':        'framework4j-redis/README.md',
  'datasource':   'framework4j-datasource/README.md',
  'sql-tracing':  'framework4j-sql-tracing/README.md',
  'id':           'framework4j-id/README.md',
  'datetime':     'framework4j-datetime/README.md',
};

const docsModulesDir = join(root, 'docs', 'modules');
const distDir = join(root, 'docs', '.vitepress', 'dist');
const targetDir = join(root, 'framework4j-demo', 'src', 'main', 'resources', 'static');

// 当前 SDK 版本（根 pom）——生成页里 framework4j 依赖示例统一刷成现版本，
// 避免各模块 README 快照式版本号（1.2.8 等）漂移
const rootPom = readFileSync(join(root, 'pom.xml'), 'utf-8');
const sdkVersion = (rootPom.match(/<version>([\d.]+)<\/version>/) || [])[1] || 'UNKNOWN';

// 把 framework4j 自身依赖示例的版本号刷成当前版本（不动第三方依赖示例）
function refreshSdkVersions(content) {
  return content.replace(
    /(<groupId>(?:com\.github\.funcommons\.framework4j|fun\.commons)<\/groupId>\s*<artifactId>framework4j-[a-z0-9-]+<\/artifactId>\s*<version>)[^<]+(<\/version>)/g,
    `$1${sdkVersion}$2`
  );
}

// === Step 1: 复制模块所有 .md → docs/modules ===
console.log('\n📝 Step 1: 复制模块文档 → docs/modules/');
mkdirSync(docsModulesDir, { recursive: true });

// 转义函数：代码块外的 XML/HTML 标签转义
function escapeNonCodeBlocks(content) {
  const parts = content.split(/(```[\s\S]*?```)/g);
  return parts.map((part, i) => {
    if (part.startsWith('```')) return part;
    return part.replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }).join('');
}

for (const [name, readmePath] of Object.entries(modules)) {
  const src = join(root, readmePath);
  const dest = join(docsModulesDir, `${name}.md`);
  if (existsSync(src)) {
    try { rmSync(dest); } catch {}
    const content = readFileSync(src, 'utf-8');
    writeFileSync(dest, escapeNonCodeBlocks(refreshSdkVersions(content)));
    console.log(`  ✅ ${name}.md`);

    // 复制同目录下其他 .md 文档（非 README.md）
    const modDir = dirname(src);
    for (const file of readdirSync(modDir)) {
      if (!file.endsWith('.md') || file === 'README.md') continue;
      const extraSrc = join(modDir, file);
      const extraDest = join(docsModulesDir, `${name}-${file.replace(/\.md$/, '').toLowerCase().replace(/\s+/g, '-')}.md`);
      const extraContent = readFileSync(extraSrc, 'utf-8');
      writeFileSync(extraDest, escapeNonCodeBlocks(refreshSdkVersions(extraContent)));
      console.log(`  ✅ ${name}-${file.replace(/\.md$/, '').toLowerCase().replace(/\s+/g, '-')}.md`);
    }
  } else {
    console.log(`  ⚠️  ${name}.md (README not found: ${readmePath})`);
  }
}

// === Step 1b: 统一 skill（skills/framework4j/）→ docs/skills/ 站点镜像（v1.8 起，issue：skill 单源）===
// 单源 = skills/framework4j/references/<module>.md；docs/skills/fwk4j-<module>.md 是其站点镜像。
// 旧 skills/fwk4j-<module>/ 多目录结构已于 v1.8 删除——此段若还在同步旧路径即 stale。
const docsSkillsDir = join(root, 'docs', 'skills');
mkdirSync(docsSkillsDir, { recursive: true });
const unifiedSkillDir = join(root, 'skills', 'framework4j');
if (existsSync(unifiedSkillDir)) {
  console.log('\n📝 Step 1b: skills/framework4j → docs/skills/ 镜像同步');

  // SKILL.md → docs/skills/fwk4j-sdk.md（去 frontmatter，站点正文）
  const skillEntry = join(unifiedSkillDir, 'SKILL.md');
  if (existsSync(skillEntry)) {
    const body = readFileSync(skillEntry, 'utf-8').replace(/^---[\s\S]*?---\n/, '');
    writeFileSync(join(docsSkillsDir, 'fwk4j-sdk.md'), body);
    console.log('  ✅ fwk4j-sdk.md (SKILL.md)');
  }

  // references/<module>.md → docs/skills/fwk4j-<module>.md
  const refsDir = join(unifiedSkillDir, 'references');
  if (existsSync(refsDir)) {
    for (const file of readdirSync(refsDir)) {
      if (!file.endsWith('.md')) continue;
      const module = file.replace(/\.md$/, '');
      const content = readFileSync(join(refsDir, file), 'utf-8');
      writeFileSync(join(docsSkillsDir, `fwk4j-${module}.md`), content);
      console.log(`  ✅ fwk4j-${module}.md`);
    }
  }
}

// === Step 2: VitePress 编译 ===
// 参数化（v1.9 在线文档站，GitHub Pages 部署）：
//   --base=/framework4j/   项目页子路径（funcommons.github.io/framework4j/）；
//                          缺省 '/' —— demo 内嵌站点根路径，行为不变
//   --skip-demo            跳过 Step 3（CI 部署 Pages 时不需要写 demo 静态资源）
const args = process.argv.slice(2);
const baseArg = args.find(a => a.startsWith('--base='));
const base = baseArg ? baseArg.split('=')[1] : '/';
const skipDemo = args.includes('--skip-demo');

console.log('\n🔨 Step 2: VitePress 编译静态 HTML (base=' + base + ')');
try {
  execSync(`npx vitepress build docs --base=${base}`, { cwd: root, stdio: 'pipe' });
  console.log('  ✅ 编译成功');
} catch (e) {
  console.error('  ❌ 编译失败:', e.stderr?.toString() || e.stdout?.toString() || e.message);
  process.exit(1);
}

if (!existsSync(distDir)) {
  console.error('❌ VitePress build 失败：dist/ 目录不存在');
  process.exit(1);
}

// === Step 3: 复制 dist → demo 静态资源（--skip-demo 时跳过）===
if (skipDemo) {
  console.log('\n⏭️  Step 3: --skip-demo，跳过 demo 静态资源复制');
  console.log('\n🎉 完成！Pages 产物位于 docs/.vitepress/dist\n');
} else {
  console.log('\n📦 Step 3: 复制 dist/ → demo/static/docs/');
  rmSync(targetDir, { recursive: true, force: true });
  mkdirSync(targetDir, { recursive: true });

  function copyDir(src, dest) {
    for (const entry of readdirSync(src, { withFileTypes: true })) {
      const srcPath = join(src, entry.name);
      const destPath = join(dest, entry.name);
      if (entry.isDirectory()) {
        mkdirSync(destPath, { recursive: true });
        copyDir(srcPath, destPath);
      } else {
        copyFileSync(srcPath, destPath);
      }
    }
  }
  copyDir(distDir, targetDir);

  const fileCount = readdirSync(targetDir).length;
  console.log(`  ✅ 已复制到 ${targetDir.replace(root + '/', '')} (${fileCount} 个顶层文件/目录)`);

  console.log('\n🎉 完成！启动 demo 后访问 http://localhost:8080/\n');
}
