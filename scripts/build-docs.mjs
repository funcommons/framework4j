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

// === Step 1: 复制 README → docs/modules ===
console.log('\n📝 Step 1: 复制模块 README → docs/modules/');
mkdirSync(docsModulesDir, { recursive: true });
for (const [name, readmePath] of Object.entries(modules)) {
  const src = join(root, readmePath);
  const dest = join(docsModulesDir, `${name}.md`);
  if (existsSync(src)) {
    try { rmSync(dest); } catch {}
    // 仅转义代码块外的 XML/HTML 标签（防 Vue 误解析），代码块内保持原样
    const content = readFileSync(src, 'utf-8');
    const parts = content.split(/(```[\s\S]*?```)/g);
    const processed = parts.map((part, i) => {
      if (part.startsWith('```')) return part; // 代码块不动
      return part.replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }).join('');
    writeFileSync(dest, processed);
    console.log(`  ✅ ${name}.md`);
  } else {
    console.log(`  ⚠️  ${name}.md (README not found: ${readmePath})`);
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
