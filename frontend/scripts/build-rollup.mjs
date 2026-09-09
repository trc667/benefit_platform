/**
 * 备用生产构建脚本（Rollup + @vitejs/plugin-vue，完全不走 esbuild）。
 *
 * 为什么存在：esbuild 以子进程服务方式运行，需要管道 stdio。受限沙箱
 * （本项目的执行环境）会拒绝 Node 以 stdio:'pipe' 方式 spawn 子进程，
 * 导致 `vite build` 在加载配置阶段就报 `spawn EPERM`。
 * 本脚本用 Rollup 直接驱动 Vue SFC 编译，产出与 vite build 等价的 dist/：
 *   - 入口 index.html（自动注入带 hash 的产物）
 *   - 业务代码 + vendor 分包（vue / element-plus 独立 chunk）
 *   - 合并后的单文件 CSS
 *
 * 正常环境请优先用 `npm run build`（vite build）。
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'
import { rollup } from 'rollup'
import vuePlugin from '@vitejs/plugin-vue'

const require = createRequire(import.meta.url)
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const SRC = path.join(ROOT, 'src')
const DIST = path.join(ROOT, 'dist')

const started = Date.now()

/* ---------------- 解析器 ---------------- */

const EXTENSIONS = ['', '.js', '.mjs', '.vue', '.json', '/index.js', '/index.mjs', '/index.vue']

const probe = (base) => {
  for (const ext of EXTENSIONS) {
    const candidate = base + ext
    if (fs.existsSync(candidate) && fs.statSync(candidate).isFile()) return candidate
  }
  return null
}

/** 在 exports 字段里按 subpath 找 ESM 入口（支持 "./*" 通配） */
const resolveExportMap = (exportsField, subPath) => {
  if (!exportsField) return null
  const key = `./${subPath}`
  const direct = exportsField[key]
  const wildcardKey = Object.keys(exportsField).find((k) => k.endsWith('/*') && key.startsWith(k.slice(0, -1)))
  const entry = direct ?? (wildcardKey ? exportsField[wildcardKey] : null)
  if (!entry) return null
  if (typeof entry === 'string') {
    // 通配映射：把 * 替换回真实子路径
    if (wildcardKey && entry.includes('*')) {
      return entry.replace('*', key.slice(wildcardKey.length - 1))
    }
    return entry
  }
  if (entry.import) return typeof entry.import === 'string' ? entry.import : entry.import.default
  if (entry.default) return typeof entry.default === 'string' ? entry.default : entry.default.default
  return null
}

/**
 * 解析 npm 包入口，优先 ESM 产物。
 * 顺序：exports 子路径 → exports.import → module → esm/ 目录 → main。
 * 例如 element-plus 走 exports.import，lodash-unified 走 exports.import，
 * @floating-ui/utils 的子路径走 exports 通配，
 * dayjs 没有 module 字段但有 esm/ 目录（main 指向 UMD，直接打包会丢 default 导出）。
 */
const resolvePackage = (spec) => {
  const parts = spec.split('/')
  const pkgName = spec.startsWith('@') ? parts.slice(0, 2).join('/') : parts[0]
  const subPath = spec.slice(pkgName.length).replace(/^\//, '')
  const pkgDir = path.join(ROOT, 'node_modules', pkgName)
  if (!fs.existsSync(pkgDir)) return null

  const pkgJson = JSON.parse(fs.readFileSync(path.join(pkgDir, 'package.json'), 'utf8'))

  // 子路径导入（如 element-plus/es/locale/lang/zh-cn、@floating-ui/utils/dom）
  if (subPath) {
    // dayjs 的 plugin / locale 子路径在根目录是 CJS/UMD，ESM 版本在 esm/ 下，
    // 且目录结构是 esm/plugin/customParseFormat/index.js（注意不是 .js 同名文件）。
    if (/^(plugin|locale)\//.test(subPath)) {
      const bare = subPath.replace(/\.js$/, '')
      const esmHit = probe(path.join(pkgDir, 'esm', subPath)) || probe(path.join(pkgDir, 'esm', bare))
      if (esmHit) return esmHit
    }
    const mapped = resolveExportMap(pkgJson.exports, subPath)
    if (mapped) {
      const hit = probe(path.join(pkgDir, mapped))
      if (hit) return hit
    }
    return probe(path.join(pkgDir, subPath))
  }

  const candidates = []
  const importEntry = pkgJson.exports?.import
  if (typeof importEntry === 'string') candidates.push(importEntry)
  else if (importEntry?.default) candidates.push(importEntry.default)
  if (pkgJson.module) candidates.push(pkgJson.module)
  candidates.push('esm/index.js', 'index.mjs')
  if (pkgJson.main) candidates.push(pkgJson.main)
  candidates.push('index.js')

  for (const entry of candidates) {
    const hit = probe(path.join(pkgDir, entry))
    if (hit) return hit
  }
  return null
}

/** 源码里的 .css / .scss 导入统一交给样式插件 */
const isStyleFile = (id) => /\.(css|scss|sass|less|styl)$/.test(id)

/**
 * 拆掉 Vite 风格的查询串，例如
 *   src/components/StudentLayout.vue?vue&type=style&index=0&scoped=xxx&lang.css
 * → 真实文件 + 查询参数。
 */
const splitId = (id) => {
  const index = id.indexOf('?')
  return index === -1 ? { file: id, query: '' } : { file: id.slice(0, index), query: id.slice(index + 1) }
}

/* ---------------- 插件 ---------------- */

/**
 * 浏览器版入口覆盖。
 * axios 的默认入口会引入 lib/platform/node/**（依赖 form-data / http 等 Node 模块），
 * 浏览器构建必须走官方打包好的自包含 ESM 产物。
 */
const BROWSER_ENTRIES = {
  axios: 'node_modules/axios/dist/esm/axios.js'
}

const browserEntry = (spec) => {
  const entry = BROWSER_ENTRIES[spec]
  if (!entry) return null
  const file = path.join(ROOT, entry)
  return fs.existsSync(file) ? file : null
}

/** 收集并合并全部 CSS，最终产出 dist/assets/*.css */
const cssPlugin = () => {
  const chunks = new Map()
  return {
    name: 'cg:css',
    resolveId(source, importer) {
      const { file } = splitId(source)
      if (!isStyleFile(file)) return null
      if (source.startsWith('@/')) return path.join(SRC, source.slice(2))
      if (source.startsWith('.')) return path.resolve(path.dirname(importer), source)
      return resolvePackage(source) || null
    },
    load(id) {
      const { file, query } = splitId(id)
      if (!isStyleFile(file)) return null
      // SFC 的样式模块必须交给 @vitejs/plugin-vue 的 load + transform 编译
      // （它会按 scoped 属性补 data-v-xxx 选择器），这里不能抢先返回内容
      if (query) return null
      chunks.set(file, fs.readFileSync(file, 'utf8'))
      // 普通 .css 文件：内容收集后返回空模块
      return 'export default ""'
    },
    transform(code, id) {
      // 插件链上本插件的 transform 在 plugin-vue 之后执行，
      // 此时 style 模块已经是编译好的纯 CSS，收集并替换为空模块。
      // 注意 SFC 样式模块的文件部分是 .vue，样式标记在 query 里（type=style）。
      const { file, query } = splitId(id)
      const isSfcStyle = query.includes('type=style')
      if (!isSfcStyle && !isStyleFile(file)) return null
      chunks.set(query ? `${file}#${query}` : file, code)
      return 'export default ""'
    },
    generateBundle() {
      const css = [...chunks.values()].join('\n')
      if (!css.trim()) return
      this.emitFile({
        type: 'asset',
        fileName: 'assets/index.css',
        source: css
      })
    }
  }
}

/** import.meta.env 的最小替身，避免浏览器里取到 undefined */
const definePlugin = (defines) => {
  const pattern = new RegExp(
    `\\b(${Object.keys(defines)
      .map((key) => key.replace(/[.$]/g, '\\$&'))
      .join('|')})\\b`,
    'g'
  )
  return {
    name: 'cg:define',
    transform(code) {
      if (!code.includes('import.meta.env')) return null
      return code.replace(pattern, (match) => JSON.stringify(defines[match]))
    }
  }
}

/* ---------------- 构建 ---------------- */

const out = process.argv.includes('--out') ? process.argv[process.argv.indexOf('--out') + 1] : DIST
fs.rmSync(out, { recursive: true, force: true })

const bundle = await rollup({
  input: path.join(SRC, 'main.js'),
  treeshake: true,
  onwarn(warning, warn) {
    // 第三方包（element-plus）的内部提示不影响产物，降噪但不吞掉源码问题
    if (warning.code === 'CIRCULAR_DEPENDENCY' && warning.ids?.some((id) => id.includes('node_modules'))) return
    if (warning.code === 'SOURCEMAP_ERROR') return
    warn(warning)
  },
  plugins: [
    vuePlugin({ isProduction: true, reactivityTransform: false }),
    definePlugin({
      'import.meta.env.DEV': false,
      'import.meta.env.PROD': true,
      'import.meta.env.MODE': 'production',
      'import.meta.env.BASE_URL': '/'
    }),
    cssPlugin(),
    {
      name: 'cg:resolve',
      resolveId(source, importer) {
        if (isStyleFile(source)) return null // 交给 css 插件
        if (!importer) return probe(path.join(SRC, source)) || browserEntry(source) || resolvePackage(source)
        if (source.startsWith('@/')) return probe(path.join(SRC, source.slice(2)))
        if (source.startsWith('.')) return probe(path.resolve(path.dirname(importer), source))
        if (source.startsWith('node:')) return { id: source, external: true }
        return browserEntry(source) || resolvePackage(source)
      }
    }
  ]
})

const { output } = await bundle.generate({
  format: 'es',
  dir: out,
  entryFileNames: 'assets/[name]-[hash].js',
  chunkFileNames: 'assets/[name]-[hash].js',
  assetFileNames: 'assets/[name]-[hash][extname]',
  // 第三方依赖单独成块，业务代码改动不会让整包缓存失效。
  // vue / pinia / vue-router / vue-demi 互相引用，必须合并到同一块，
  // 否则 Rollup 会报 "Circular chunk: vendor -> vue -> vendor"。
  manualChunks(id) {
    if (!id.includes('node_modules')) return undefined
    if (/node_modules[\\/](element-plus|@element-plus)[\\/]/.test(id)) return 'element'
    return 'vendor'
  }
})

await bundle.close()

// 落盘
const written = []
for (const item of output) {
  const target = path.join(out, item.fileName)
  fs.mkdirSync(path.dirname(target), { recursive: true })
  if (item.type === 'asset') {
    fs.writeFileSync(target, item.source)
  } else {
    fs.writeFileSync(target, item.code)
  }
  written.push({ file: item.fileName, size: Buffer.byteLength(item.type === 'asset' ? item.source : item.code) })
}

// index.html：注入产物并改相对路径，方便本地直接打开
const html = fs
  .readFileSync(path.join(ROOT, 'index.html'), 'utf8')
  .replace(
    /\s*<script type="module" src="\/src\/main\.js"><\/script>/,
    '\n    <script type="module" crossorigin src="./assets/main.js"></script>'
  )
  .replace(/<link rel="stylesheet"[^>]*>\s*/g, '')

const hasCss = written.some((item) => item.file.endsWith('.css'))
const entryFile = written.find((item) => item.file.startsWith('assets/main-'))?.file
const finalHtml = html
  .replace('./assets/main.js', `./${entryFile || 'assets/main.js'}`)
  .replace('</head>', hasCss ? '    <link rel="stylesheet" href="./assets/index.css" />\n  </head>' : '  </head>')

fs.writeFileSync(path.join(out, 'index.html'), finalHtml)

/* ---------------- 报告 ---------------- */

const kb = (bytes) => `${(bytes / 1024).toFixed(2)} kB`
const gzipSize = (bytes) => `${(bytes / 1024 * 0.32).toFixed(2)} kB` // 粗略估算

console.log(`\n输出目录：${path.relative(ROOT, out) || '.'}`)
written
  .sort((a, b) => b.size - a.size)
  .forEach((item) => console.log(`  ${item.file.padEnd(46)} ${kb(item.size).padStart(10)}`))
console.log(`\n共 ${written.length} 个文件，${kb(written.reduce((sum, item) => sum + item.size, 0))}，耗时 ${(
  (Date.now() - started) / 1000
).toFixed(1)}s`)
