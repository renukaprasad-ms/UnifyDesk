export type RealtimeEnvelope<T = unknown> = {
  type: string
  occurredAt: string
  payload: T
}

type Listener<T = unknown> = (event: RealtimeEnvelope<T>) => void

class RealtimeClient {
  private socket: WebSocket | null = null
  private listeners = new Map<string, Set<Listener>>()
  private reconnectTimer: number | null = null
  private heartbeatTimer: number | null = null
  private reconnectAttempts = 0
  private stopped = true

  connect() {
    if (this.socket?.readyState === WebSocket.OPEN || this.socket?.readyState === WebSocket.CONNECTING)
      return

    this.stopped = false
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const url = `${protocol}//${window.location.host}/api/v1/realtime/ws?platform=WEB`
    const socket = new WebSocket(url)
    this.socket = socket

    socket.onopen = () => {
      this.reconnectAttempts = 0
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

    socket.onclose = () => {
      this.stopHeartbeat()
      if (this.socket === socket) this.socket = null
      if (!this.stopped) this.scheduleReconnect()
    }

    socket.onerror = () => {
      socket.close()
    }
  }

  disconnect() {
    this.stopped = true
    this.clearReconnect()
    this.stopHeartbeat()
    this.socket?.close()
    this.socket = null
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
