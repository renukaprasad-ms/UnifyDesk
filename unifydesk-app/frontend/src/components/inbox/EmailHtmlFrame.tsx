import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

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
    html, body {
      width: 100%;
      max-width: 100%;
      margin: 0;
      padding: 0;
      overflow-x: hidden;
      overflow-y: hidden;
      overflow-wrap: anywhere;
      box-sizing: border-box;
    }
    body { padding: 12px; }
    *, *::before, *::after { box-sizing: border-box; }
    img { max-width: 100% !important; height: auto !important; }
    table { max-width: 100% !important; }
    pre { white-space: pre-wrap; word-break: break-word; }
  `
  doc.head.append(responsiveStyle)

  return `<!doctype html>${doc.documentElement.outerHTML}`
}

export default function EmailHtmlFrame({ html, title }: { html: string; title: string }) {
  const srcDoc = useMemo(() => prepareEmailHtml(html), [html])
  const frameRef = useRef<HTMLIFrameElement>(null)
  const resizeObserverRef = useRef<ResizeObserver | null>(null)
  const [height, setHeight] = useState(160)

  const syncHeight = useCallback(() => {
    const frame = frameRef.current
    const document = frame?.contentDocument
    if (!document) return

    const nextHeight = Math.max(
      160,
      document.documentElement?.scrollHeight ?? 0,
      document.body?.scrollHeight ?? 0,
      document.documentElement?.offsetHeight ?? 0,
      document.body?.offsetHeight ?? 0,
    )

    setHeight(nextHeight)
  }, [])

  const handleLoad = useCallback(() => {
    resizeObserverRef.current?.disconnect()
    syncHeight()

    const document = frameRef.current?.contentDocument
    if (!document?.body || typeof ResizeObserver === 'undefined') return

    const observer = new ResizeObserver(() => syncHeight())
    observer.observe(document.documentElement)
    observer.observe(document.body)
    resizeObserverRef.current = observer

    document.querySelectorAll('img').forEach((image) => {
      image.addEventListener('load', syncHeight, { once: true })
      image.addEventListener('error', syncHeight, { once: true })
    })
  }, [syncHeight])

  useEffect(() => {
    setHeight(160)
    return () => resizeObserverRef.current?.disconnect()
  }, [srcDoc])

  return (
    <iframe
      ref={frameRef}
      title={title}
      srcDoc={srcDoc}
      sandbox="allow-same-origin"
      referrerPolicy="no-referrer"
      scrolling="no"
      onLoad={handleLoad}
      style={{ height: `${height}px` }}
      className="block w-full overflow-hidden rounded-xl border border-slate-100 bg-white"
    />
  )
}
