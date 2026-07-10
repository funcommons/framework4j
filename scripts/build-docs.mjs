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
    writeFileSync(dest, escapeNonCodeBlocks(content));
    console.log(`  ✅ ${name}.md`);

    // 复制同目录下其他 .md 文档（非 README.md）
    const modDir = dirname(src);
    for (const file of readdirSync(modDir)) {
      if (!file.endsWith('.md') || file === 'README.md') continue;
      const extraSrc = join(modDir, file);
      const extraDest = join(docsModulesDir, `${name}-${file.replace(/\.md$/, '').toLowerCase().replace(/\s+/g, '-')}.md`);
      const extraContent = readFileSync(extraSrc, 'utf-8');
      writeFileSync(extraDest, escapeNonCodeBlocks(extraContent));
      console.log(`  ✅ ${name}-${file.replace(/\.md$/, '').toLowerCase().replace(/\s+/g, '-')}.md`);
    }
  } else {
    console.log(`  ⚠️  ${name}.md (README not found: ${readmePath})`);
  }
}

// === Step 1b: 复制 skills/SKILL.md → docs/skills/ + 同步模块 README → skills/README.md ===
const docsSkillsDir = join(root, 'docs', 'skills');
mkdirSync(docsSkillsDir, { recursive: true });
const skillsDir = join(root, 'skills');
if (existsSync(skillsDir)) {
  console.log('\n📝 Step 1b: 同步 skill 文档 + 复制到 docs/skills/');

  // skill 名称 → 模块名称 映射
  const skillToModule = {
    'fwk4j-api': 'framework4j-api',
    'fwk4j-web': 'framework4j-web',
    'fwk4j-accesstoken': 'framework4j-accesstoken',
    'fwk4j-signature': 'framework4j-signature',
    'fwk4j-rate-limit': 'framework4j-rate-limit',
    'fwk4j-cache': 'framework4j-cache',
    'fwk4j-audit': 'framework4j-audit',
    'fwk4j-sensitive': 'framework4j-sensitive',
    'fwk4j-idempotency': 'framework4j-idempotency',
    'fwk4j-redis': 'framework4j-redis',
    'fwk4j-datasource': 'framework4j-datasource',
    'fwk4j-sql-tracing': 'framework4j-sql-tracing',
    'fwk4j-id': 'framework4j-id',
    'fwk4j-datetime': 'framework4j-datetime',
    'fwk4j-sdk': null, // 总入口无对应模块
  };

  for (const dir of readdirSync(skillsDir)) {
    if (!dir.startsWith('fwk4j-')) continue;

    // 1b-1: SKILL.md → docs/skills/（去 frontmatter）
    const skillFile = join(skillsDir, dir, 'SKILL.md');
    if (existsSync(skillFile)) {
      const content = readFileSync(skillFile, 'utf-8');
      const body = content.replace(/^---[\s\S]*?---\n/, '');
      const dest = join(docsSkillsDir, `${dir}.md`);
      writeFileSync(dest, body);
    }

    // 1b-2: 模块所有 .md → skills/{dir}/（README.md + 额外文档同步）
    const moduleName = skillToModule[dir];
    if (moduleName) {
      const modDir = join(root, moduleName);
      if (existsSync(modDir)) {
        for (const file of readdirSync(modDir)) {
          if (!file.endsWith('.md')) continue;
          const src = join(modDir, file);
          const dest = join(skillsDir, dir, file);
          copyFileSync(src, dest);
        }
      }
    }

    console.log(`  ✅ ${dir}/ (SKILL.md + 全部 .md)`);
  }
}

// === Step 2: VitePress 编译 ===
console.log('\n🔨 Step 2: VitePress 编译静态 HTML');
try {
  execSync('npx vitepress build docs', { cwd: root, stdio: 'pipe' });
  console.log('  ✅ 编译成功');
} catch (e) {
  console.error('  ❌ 编译失败:', e.stderr?.toString() || e.stdout?.toString() || e.message);
  process.exit(1);
}

if (!existsSync(distDir)) {
  console.error('❌ VitePress build 失败：dist/ 目录不存在');
  process.exit(1);
}

// === Step 3: 复制 dist → demo 静态资源 ===
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
