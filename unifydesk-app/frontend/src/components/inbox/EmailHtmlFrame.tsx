import { useMemo } from 'react'

const prepareEmailHtml = (html: string) => {
  const doc = new DOMParser().parseFromString(html, 'text/html')

  doc
    .querySelectorAll('script, iframe, object, embed, form, base, meta[http-equiv="refresh"]')
    .forEach((node) => node.remove())

  doc.querySelectorAll('*').forEach((element) => {
    Array.from(element.attributes).forEach((attribute) => {
      const name = attribute.name.toLowerCase()
      const value = attribute.value.trim().toLowerCase()

      if (name.startsWith('on')) element.removeAttribute(attribute.name)
      if ((name === 'href' || name === 'src') && value.startsWith('javascript:')) {
        element.removeAttribute(attribute.name)
      }
    })
  })

  const viewport = doc.createElement('meta')
  viewport.name = 'viewport'
  viewport.content = 'width=device-width, initial-scale=1'
  doc.head.prepend(viewport)

  const policy = doc.createElement('meta')
  policy.httpEquiv = 'Content-Security-Policy'
  policy.content =
    "default-src 'none'; img-src https: data:; style-src 'unsafe-inline'; font-src https: data:; media-src https: data:"
  doc.head.prepend(policy)

  const responsiveStyle = doc.createElement('style')
  responsiveStyle.textContent = `
    html, body { max-width: 100%; margin: 0; padding: 0; overflow-wrap: anywhere; }
    body { padding: 12px; box-sizing: border-box; }
    img { max-width: 100% !important; height: auto !important; }
    table { max-width: 100% !important; }
    pre { white-space: pre-wrap; word-break: break-word; }
  `
  doc.head.append(responsiveStyle)

  return `<!doctype html>${doc.documentElement.outerHTML}`
}

export default function EmailHtmlFrame({ html, title }: { html: string; title: string }) {
  const srcDoc = useMemo(() => prepareEmailHtml(html), [html])

  return (
    <iframe
      title={title}
      srcDoc={srcDoc}
      sandbox=""
      referrerPolicy="no-referrer"
      className="h-[480px] w-full rounded-xl border border-slate-100 bg-white sm:h-[600px]"
    />
  )
}
