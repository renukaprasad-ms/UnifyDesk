import fs from 'node:fs'
import path from 'node:path'
import ts from 'typescript'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../src')
const violations = []
let checked = 0
function visit(node, callback) {
  callback(node)
  ts.forEachChild(node, child => visit(child, callback))
}
function scan(directory) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const file = path.join(directory, entry.name)
    if (entry.isDirectory()) { scan(file); continue }
    if (!/\.tsx?$/.test(file) || /\.(test|spec)\./.test(file) || entry.name === 'useSnackbarState.ts') continue
    checked++
    const source = ts.createSourceFile(file, fs.readFileSync(file, 'utf8'), ts.ScriptTarget.Latest, true)
    const report = (node, message) => {
      const position = source.getLineAndCharacterOfPosition(node.getStart(source))
      violations.push(`${path.relative(root, file)}:${position.line + 1} ${message}`)
    }
    visit(source, node => {
      if (ts.isCallExpression(node) && ['alert', 'window.alert'].includes(node.expression.getText(source))) {
        report(node, 'Use the shared snackbar for notifications.')
      }
      if (!ts.isVariableDeclaration(node) || !ts.isArrayBindingPattern(node.name) || !node.initializer || !ts.isCallExpression(node.initializer)) return
      if (!['useState', 'React.useState'].includes(node.initializer.expression.getText(source))) return
      const name = node.name.elements[0]?.name?.getText(source)
      const initial = node.initializer.arguments[0]
      if (name && /^(error|notice|success|warning|feedback|successMsg)$|(?:Error|Notice|Warning)$/.test(name) && initial && (ts.isStringLiteral(initial) || initial.kind === ts.SyntaxKind.NullKeyword)) {
        report(node, 'Use useSnackbarState for action feedback; keep field validation in its field control.')
      }
    })
  }
}
scan(root)
if (violations.length) {
  console.error(violations.join('\n'))
  process.exitCode = 1
} else console.log(`Feedback checks passed across ${checked} frontend source files.`)
