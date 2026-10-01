import type { WhatsAppTemplate } from '../../features/whatsapp/types'

const variableIndexes = (text?: string | null) => {
  if (!text) return []
  const indexes = Array.from(text.matchAll(/\{\{\s*(\d+)\s*\}\}/g))
    .map((match) => Number(match[1]))
    .filter((value) => Number.isInteger(value) && value > 0)
  return Array.from(new Set(indexes)).sort((a, b) => a - b)
}

export const templateVariableCounts = (template: WhatsAppTemplate) => {
  let header = 0
  let body = 0
  let buttons = 0

  template.components.forEach((component) => {
    const count = variableIndexes(component.text).at(-1) ?? 0
    if (component.type === 'HEADER') {
      header = Math.max(header, count)
    } else if (component.type === 'BODY') {
      body = Math.max(body, count)
    } else if (component.type === 'BUTTONS') {
      buttons +=
        component.buttons?.filter(
          (button) =>
            button.type?.toUpperCase() === 'URL' &&
            variableIndexes(button.url).length > 0,
        ).length ?? 0
    }
  })

  return { header, body, buttons }
}
