export type RealtimeEnvelope<T = unknown> = {
  type: string
  occurredAt: string
  payload: T
}

type Listener<T = unknown> = (event: RealtimeEnvelope<T>) => void
type ConnectionListener = (connected: boolean) => void

const realtimeWebSocketUrl = () => {
  const configuredBaseURL = import.meta.env.VITE_API_BASE_URL?.trim()
  const httpBase =
    configuredBaseURL && /^https?:\/\//i.test(configuredBaseURL)
      ? configuredBaseURL
      : window.location.origin
  const base = new URL(httpBase, window.location.origin)
  base.protocol = base.protocol === 'https:' ? 'wss:' : 'ws:'
  base.pathname = '/api/v1/realtime/ws'
  base.search = 'platform=WEB'
  base.hash = ''
  return base.toString()
}

class RealtimeClient {
  private socket: WebSocket | null = null
  private listeners = new Map<string, Set<Listener>>()
  private reconnectTimer: number | null = null
  private heartbeatTimer: number | null = null
  private reconnectAttempts = 0
  private stopped = true
  private connected = false
  private connectionListeners = new Set<ConnectionListener>()

  connect() {
    if (
      this.socket?.readyState === WebSocket.OPEN ||
      this.socket?.readyState === WebSocket.CONNECTING
    )
      return

    this.stopped = false
    const url = realtimeWebSocketUrl()
    const socket = new WebSocket(url)
    this.socket = socket

    socket.onopen = () => {
      this.reconnectAttempts = 0
      this.setConnected(true)
      this.startHeartbeat()
    }

    socket.onmessage = (message) => {
      try {
        const event = JSON.parse(message.data) as RealtimeEnvelope
        this.emit(event)
      } catch {
        // Ignore malformed server frames; the socket remains usable.
      }
    }

    socket.onclose = (event) => {
      console.warn('[RealtimeDebug] SOCKET_CLOSED', {
        code: event.code,
        reason: event.reason,
        wasClean: event.wasClean,
        url,
      })
      this.setConnected(false)
      this.stopHeartbeat()
      if (this.socket === socket) this.socket = null
      if (!this.stopped) this.scheduleReconnect()
    }

    socket.onerror = () => {
      console.error('[RealtimeDebug] SOCKET_ERROR', { url })
      socket.close()
    }
  }

  disconnect() {
    this.stopped = true
    this.setConnected(false)
    this.clearReconnect()
    this.stopHeartbeat()
    this.socket?.close()
    this.socket = null
  }

  isConnected() {
    return this.connected
  }

  subscribeConnection(listener: ConnectionListener) {
    this.connectionListeners.add(listener)
    listener(this.connected)

    return () => {
      this.connectionListeners.delete(listener)
    }
  }

  subscribe<T>(type: string, listener: Listener<T>) {
    const listeners = this.listeners.get(type) ?? new Set<Listener>()
    listeners.add(listener as Listener)
    this.listeners.set(type, listeners)

    return () => {
      const current = this.listeners.get(type)
      current?.delete(listener as Listener)
      if (current?.size === 0) this.listeners.delete(type)
    }
  }

  private setConnected(connected: boolean) {
    if (this.connected === connected) return
    this.connected = connected
    this.connectionListeners.forEach((listener) => listener(connected))
  }

  private emit(event: RealtimeEnvelope) {
    this.listeners.get(event.type)?.forEach((listener) => listener(event))
    this.listeners.get('*')?.forEach((listener) => listener(event))
  }

  private scheduleReconnect() {
    this.clearReconnect()
    const delay = Math.min(30_000, 1_000 * 2 ** Math.min(this.reconnectAttempts, 5))
    this.reconnectAttempts += 1
    this.reconnectTimer = window.setTimeout(() => this.connect(), delay)
  }

  private clearReconnect() {
    if (this.reconnectTimer !== null) {
      window.clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
  }

  private startHeartbeat() {
    this.stopHeartbeat()
    this.heartbeatTimer = window.setInterval(() => {
      if (this.socket?.readyState === WebSocket.OPEN) this.socket.send('ping')
    }, 20_000)
  }

  private stopHeartbeat() {
    if (this.heartbeatTimer !== null) {
      window.clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }
}

export const realtimeClient = new RealtimeClient()
