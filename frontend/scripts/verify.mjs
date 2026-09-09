/**
 * 离线静态校验脚本（不依赖 esbuild / 打包器）。
 *
 * 背景：CI 或受限沙箱环境里可能无法启动 esbuild 的子进程服务，导致 `vite build` 不可用。
 * 该脚本用 @vue/compiler-sfc 做等价的语法层校验：
 *   1. 每个 .vue 的 template / script / style 是否能被编译器解析
 *   2. <script setup> 能否编译为合法的 ESM（再用 node --check 复核语法）
 *   3. 模板表达式与 script 的编译结果是否报错
 *   4. 所有 import 路径（@/ 别名、相对路径、npm 包）是否能解析到真实文件
 *
 * 用法：node scripts/verify.mjs
 */
import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const sfc = require('@vue/compiler-sfc')

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const SRC = path.join(ROOT, 'src')

const errors = []
const warnings = []
const stats = { vue: 0, js: 0, imports: 0 }

const walk = (dir, out = []) => {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) walk(full, out)
    else out.push(full)
  }
  return out
}

const EXTENSIONS = ['', '.js', '.mjs', '.vue', '.json', '/index.js', '/index.mjs', '/index.vue']

/** 解析 import 说明符，返回命中的真实文件（null 表示外部依赖） */
const resolveSpecifier = (spec, fromFile) => {
  if (spec.startsWith('@/')) {
    const base = path.join(SRC, spec.slice(2))
    for (const ext of EXTENSIONS) {
      if (fs.existsSync(base + ext) && fs.statSync(base + ext).isFile()) return base + ext
    }
    return false
  }
  if (spec.startsWith('.')) {
    const base = path.resolve(path.dirname(fromFile), spec)
    for (const ext of EXTENSIONS) {
      if (fs.existsSync(base + ext) && fs.statSync(base + ext).isFile()) return base + ext
    }
    return false
  }
  // 外部依赖：只要 node_modules 里能找到就算通过
  // 注意两点：element-plus 的 ESM 产物是 .mjs；其 package.json 的 exports 未暴露
  // 深层子路径（ERR_PACKAGE_PATH_NOT_EXPORTED），但 Vite 仍能按文件路径解析，
  // 因此这里先试 require.resolve，再退回文件系统探测。
  for (const candidate of [spec, `${spec}.mjs`, `${spec}.js`, `${spec}/index.js`]) {
    try {
      require.resolve(candidate, { paths: [ROOT] })
      return null
    } catch (e) {
      if (fs.existsSync(path.join(ROOT, 'node_modules', candidate))) return null
    }
  }
  return false
}

const IMPORT_RE = /(?:import\s[^'"]*from\s*|import\s*|export\s[^'"]*from\s*|require\s*\(\s*)['"]([^'"]+)['"]/g

const checkImports = (code, file) => {
  for (const match of code.matchAll(IMPORT_RE)) {
    const spec = match[1]
    if (!spec || spec.endsWith('.css')) continue
    stats.imports += 1
    const resolved = resolveSpecifier(spec, file)
    if (resolved === false) {
      errors.push(`${path.relative(ROOT, file)}: 无法解析 import "${spec}"`)
    }
  }
}

const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'cg-verify-'))

const checkJsSyntax = (code, label) => {
  const tmp = path.join(tmpDir, 'chunk.mjs')
  fs.writeFileSync(tmp, code, 'utf8')
  try {
    execFileSync(process.execPath, ['--check', tmp], { stdio: 'inherit' })
  } catch (e) {
    errors.push(`${label}: 编译后的 JS 语法不合法`)
  }
}

const files = walk(SRC)

for (const file of files) {
  const rel = path.relative(ROOT, file)

  if (file.endsWith('.vue')) {
    stats.vue += 1
    const source = fs.readFileSync(file, 'utf8')
    const { descriptor, errors: parseErrors } = sfc.parse(source, { filename: file })

    parseErrors.forEach((err) => errors.push(`${rel}: SFC 解析失败 → ${err.message}`))

    // script / script setup
    if (descriptor.script || descriptor.scriptSetup) {
      try {
        const compiled = sfc.compileScript(descriptor, { id: rel })
        checkImports(compiled.content, file)
        checkJsSyntax(compiled.content, `${rel} <script>`)
      } catch (err) {
        errors.push(`${rel}: <script> 编译失败 → ${err.message}`)
      }
    }

    // template
    if (descriptor.template) {
      try {
        const result = sfc.compileTemplate({
          source: descriptor.template.content,
          filename: file,
          id: rel,
          compilerOptions: { isCustomElement: () => false }
        })
        result.errors.forEach((err) =>
          errors.push(`${rel}: <template> 编译失败 → ${err.message || err}`)
        )
        result.tips?.forEach((tip) => warnings.push(`${rel}: ${tip.message || tip}`))
      } catch (err) {
        errors.push(`${rel}: <template> 编译异常 → ${err.message}`)
      }
    }

    // style
    descriptor.styles.forEach((style, index) => {
      if (!style.content.trim()) warnings.push(`${rel}: 第 ${index + 1} 个 style 块为空`)
    })
    continue
  }

  if (file.endsWith('.js') || file.endsWith('.mjs')) {
    stats.js += 1
    const source = fs.readFileSync(file, 'utf8')
    checkImports(source, file)
    checkJsSyntax(source, rel)
  }
}

fs.rmSync(tmpDir, { recursive: true, force: true })

console.log(`\n扫描：${stats.vue} 个 .vue ｜ ${stats.js} 个 .js ｜ ${stats.imports} 条 import`)

if (warnings.length) {
  console.log(`\n提示（${warnings.length}）：`)
  warnings.forEach((msg) => console.log(`  · ${msg}`))
}

if (errors.length) {
  console.log(`\n错误（${errors.length}）：`)
  errors.forEach((msg) => console.log(`  ✗ ${msg}`))
  process.exit(1)
}

console.log('\n静态校验通过：模板、脚本、样式与 import 路径均无问题。')
