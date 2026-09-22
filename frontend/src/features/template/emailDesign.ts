export type EmailLogoAlignment = 'LEFT' | 'CENTER' | 'RIGHT'
export type EmailTextAlignment = 'LEFT' | 'CENTER' | 'RIGHT'
export type EmailHeadingLevel = 'H1' | 'H2' | 'H3'
export type EmailBlockType = 'HEADING' | 'TEXT' | 'IMAGE' | 'BUTTON' | 'DIVIDER' | 'SPACER'

export type EmailDesignBlock = {
  id: string
  type: EmailBlockType
  text?: string
  level?: EmailHeadingLevel
  mediaId?: string | null
  altText?: string
  imageWidth?: number
  buttonText?: string
  buttonUrl?: string
  alignment?: EmailTextAlignment
  height?: number
}

export type EmailTemplateDesign = {
  version: 1
  backgroundColor: string
  contentBackgroundColor: string
  contentWidth: number
  brandColor: string
  textColor: string
  header: {
    logoMediaId?: string | null
    logoWidth: number
    logoAlignment: EmailLogoAlignment
    backgroundColor: string
    heading?: string
  }
  blocks: EmailDesignBlock[]
  footer: {
    companyName?: string
    address?: string
    privacyUrl?: string
    includeUnsubscribe: boolean
    backgroundColor: string
    textColor: string
  }
}

const newId = () =>
  typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `block-${Date.now()}-${Math.random().toString(16).slice(2)}`

export const defaultEmailDesign = (bodyText?: string | null): EmailTemplateDesign => ({
  version: 1,
  backgroundColor: '#f8fafc',
  contentBackgroundColor: '#ffffff',
  contentWidth: 600,
  brandColor: '#4f46e5',
  textColor: '#334155',
  header: {
    logoMediaId: null,
    logoWidth: 140,
    logoAlignment: 'CENTER',
    backgroundColor: '#ffffff',
    heading: '',
  },
  blocks: [
    {
      id: newId(),
      type: 'TEXT',
      text: bodyText?.trim() || 'Hi {{first_name}},\n\nWrite your email message here.',
      alignment: 'LEFT',
    },
  ],
  footer: {
    companyName: '',
    address: '',
    privacyUrl: '',
    includeUnsubscribe: true,
    backgroundColor: '#f8fafc',
    textColor: '#64748b',
  },
})

export const parseEmailDesign = (
  designJson?: string | null,
  fallbackBodyText?: string | null,
): EmailTemplateDesign => {
  if (!designJson) return defaultEmailDesign(fallbackBodyText)
  try {
    const parsed = JSON.parse(designJson) as Partial<EmailTemplateDesign>
    if (parsed.version !== 1 || !Array.isArray(parsed.blocks)) {
      return defaultEmailDesign(fallbackBodyText)
    }
    const fallback = defaultEmailDesign(fallbackBodyText)
    return {
      ...fallback,
      ...parsed,
      header: { ...fallback.header, ...(parsed.header ?? {}) },
      footer: { ...fallback.footer, ...(parsed.footer ?? {}) },
      blocks: parsed.blocks.map((block) => ({
        id: block.id || newId(),
        type: block.type,
        text: block.text,
        level: block.level,
        mediaId: block.mediaId,
        altText: block.altText,
        imageWidth: block.imageWidth,
        buttonText: block.buttonText,
        buttonUrl: block.buttonUrl,
        alignment: block.alignment,
        height: block.height,
      })),
    }
  } catch {
    return defaultEmailDesign(fallbackBodyText)
  }
}

export const createEmailBlock = (type: EmailBlockType): EmailDesignBlock => {
  if (type === 'HEADING') {
    return { id: newId(), type, text: 'Your heading', level: 'H2', alignment: 'LEFT' }
  }
  if (type === 'TEXT') {
    return { id: newId(), type, text: 'Write your message here.', alignment: 'LEFT' }
  }
  if (type === 'IMAGE') {
    return { id: newId(), type, mediaId: null, altText: '', imageWidth: 560, alignment: 'CENTER' }
  }
  if (type === 'BUTTON') {
    return {
      id: newId(),
      type,
      buttonText: 'Learn more',
      buttonUrl: 'https://example.com',
      alignment: 'CENTER',
    }
  }
  if (type === 'SPACER') {
    return { id: newId(), type, height: 24 }
  }
  return { id: newId(), type: 'DIVIDER' }
}

const escapeHtml = (value: string) =>
  value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;')

const nl2br = (value: string) => escapeHtml(value).replaceAll('\n', '<br />')

const align = (value?: EmailTextAlignment | EmailLogoAlignment) =>
  (value ?? 'LEFT').toLowerCase()

export const emailMediaIds = (design: EmailTemplateDesign) => {
  const ids = new Set<string>()
  if (design.header.logoMediaId) ids.add(design.header.logoMediaId)
  design.blocks.forEach((block) => {
    if (block.type === 'IMAGE' && block.mediaId) ids.add(block.mediaId)
  })
  return Array.from(ids)
}

export const generateEmailOutputs = (
  design: EmailTemplateDesign,
  mediaUrls: Record<string, string> = {},
) => {
  const textParts: string[] = []
  const htmlBlocks: string[] = []

  if (design.header.heading?.trim()) {
    textParts.push(design.header.heading.trim())
  }

  const logoHtml = design.header.logoMediaId
    ? `<div style="text-align:${align(design.header.logoAlignment)};padding:20px 24px 10px"><img src="${escapeHtml(
        mediaUrls[design.header.logoMediaId] ?? `media:${design.header.logoMediaId}`,
      )}" alt="Logo" style="display:inline-block;max-width:100%;width:${Math.max(
        40,
        Math.min(300, design.header.logoWidth),
      )}px;height:auto" /></div>`
    : ''

  const headingHtml = design.header.heading?.trim()
    ? `<div style="padding:8px 24px 18px;text-align:center;font-size:22px;font-weight:700;color:${escapeHtml(
        design.textColor,
      )}">${nl2br(design.header.heading.trim())}</div>`
    : ''

  design.blocks.forEach((block) => {
    if (block.type === 'HEADING') {
      const value = block.text?.trim() ?? ''
      if (!value) return
      textParts.push(value)
      const size = block.level === 'H1' ? 28 : block.level === 'H3' ? 18 : 22
      htmlBlocks.push(
        `<div style="padding:8px 24px;text-align:${align(
          block.alignment,
        )};font-size:${size}px;line-height:1.3;font-weight:700;color:${escapeHtml(
          design.textColor,
        )}">${nl2br(value)}</div>`,
      )
      return
    }

    if (block.type === 'TEXT') {
      const value = block.text?.trim() ?? ''
      if (!value) return
      textParts.push(value)
      htmlBlocks.push(
        `<div style="padding:8px 24px;text-align:${align(
          block.alignment,
        )};font-size:15px;line-height:1.65;color:${escapeHtml(
          design.textColor,
        )}">${nl2br(value)}</div>`,
      )
      return
    }

    if (block.type === 'IMAGE') {
      if (!block.mediaId) return
      const src = mediaUrls[block.mediaId] ?? `media:${block.mediaId}`
      htmlBlocks.push(
        `<div style="padding:12px 24px;text-align:${align(
          block.alignment,
        )}"><img src="${escapeHtml(src)}" alt="${escapeHtml(
          block.altText ?? '',
        )}" style="display:inline-block;max-width:100%;width:${Math.max(
          80,
          Math.min(560, block.imageWidth ?? 560),
        )}px;height:auto;border:0" /></div>`,
      )
      return
    }

    if (block.type === 'BUTTON') {
      const text = block.buttonText?.trim() || 'Learn more'
      const url = block.buttonUrl?.trim() || '#'
      textParts.push(`${text}: ${url}`)
      htmlBlocks.push(
        `<div style="padding:14px 24px;text-align:${align(
          block.alignment,
        )}"><a href="${escapeHtml(
          url,
        )}" style="display:inline-block;border-radius:8px;background:${escapeHtml(
          design.brandColor,
        )};color:#ffffff;text-decoration:none;font-size:14px;font-weight:700;padding:12px 20px">${escapeHtml(
          text,
        )}</a></div>`,
      )
      return
    }

    if (block.type === 'DIVIDER') {
      htmlBlocks.push(
        '<div style="padding:12px 24px"><div style="height:1px;background:#e2e8f0"></div></div>',
      )
      return
    }

    if (block.type === 'SPACER') {
      htmlBlocks.push(
        `<div style="height:${Math.max(8, Math.min(80, block.height ?? 24))}px"></div>`,
      )
    }
  })

  const footerText: string[] = []
  if (design.footer.companyName?.trim()) footerText.push(design.footer.companyName.trim())
  if (design.footer.address?.trim()) footerText.push(design.footer.address.trim())
  if (design.footer.privacyUrl?.trim()) footerText.push(`Privacy: ${design.footer.privacyUrl.trim()}`)
  textParts.push(...footerText)

  const footerHtml =
    footerText.length > 0 || design.footer.includeUnsubscribe
      ? `<div style="margin-top:20px;padding:20px 24px;text-align:center;background:${escapeHtml(
          design.footer.backgroundColor,
        )};color:${escapeHtml(
          design.footer.textColor,
        )};font-size:11px;line-height:1.6">${footerText
          .map((value) => `<div>${escapeHtml(value)}</div>`)
          .join('')}${
          design.footer.includeUnsubscribe
            ? '<div style="margin-top:8px"><a href="{{unsubscribe_url}}" style="color:inherit">Unsubscribe</a></div>'
            : ''
        }</div>`
      : ''

  const bodyHtml = `<!doctype html>
<html>
<head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="margin:0;padding:0;background:${escapeHtml(design.backgroundColor)}">
  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="background:${escapeHtml(
    design.backgroundColor,
  )};padding:24px 12px">
    <tr><td align="center">
      <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="max-width:${Math.max(
        320,
        Math.min(760, design.contentWidth),
      )}px;background:${escapeHtml(design.contentBackgroundColor)};border-radius:12px;overflow:hidden">
        <tr><td style="background:${escapeHtml(
          design.header.backgroundColor,
        )}">${logoHtml}${headingHtml}</td></tr>
        <tr><td>${htmlBlocks.join('')}</td></tr>
        <tr><td>${footerHtml}</td></tr>
      </table>
    </td></tr>
  </table>
</body>
</html>`

  return {
    bodyText: textParts.filter(Boolean).join('\n\n'),
    bodyHtml,
  }
}
