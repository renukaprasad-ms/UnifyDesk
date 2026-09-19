export type RealtimeEnvelope<T = unknown> = {
  type: string
  occurredAt: string
  payload: T
}

type Listener<T = unknown> = (event: RealtimeEnvelope<T>) => void
type ConnectionListener = (connected: boolean) => void

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
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const url = `${protocol}//${window.location.host}/api/v1/realtime/ws?platform=WEB`
    console.info('[WS_DIAG] connect_attempt', {
      url,
      reconnectAttempt: this.reconnectAttempts,
      previousReadyState: this.socket?.readyState ?? null,
    })
    const socket = new WebSocket(url)
    this.socket = socket

    socket.onopen = () => {
      console.info('[WS_DIAG] open', {
        url,
        protocol: socket.protocol || null,
        extensions: socket.extensions || null,
      })
      this.reconnectAttempts = 0
      this.setConnected(true)
      this.startHeartbeat()
    }

    socket.onmessage = (message) => {
      try {
        const event = JSON.parse(message.data) as RealtimeEnvelope
        if (event.type === 'async.job.progress') {
          const payload = event.payload as {
            jobId?: string
            status?: string
            progressPercent?: number
          }
          console.info('[WS_DIAG] async_job_event', {
            type: event.type,
            jobId: payload?.jobId,
            status: payload?.status,
            progressPercent: payload?.progressPercent,
          })
        } else {
          console.debug('[WS_DIAG] message', { type: event.type })
        }
        this.emit(event)
      } catch (error) {
        console.warn('[WS_DIAG] malformed_message', {
          reason: error instanceof Error ? error.message : 'unknown',
        })
      }
    }

    socket.onclose = (event) => {
      console.warn('[WS_DIAG] close', {
        code: event.code,
        reason: event.reason || null,
        wasClean: event.wasClean,
        stopped: this.stopped,
      })
      this.setConnected(false)
      this.stopHeartbeat()
      if (this.socket === socket) this.socket = null
      if (!this.stopped) this.scheduleReconnect()
    }

    socket.onerror = () => {
      console.error('[WS_DIAG] error', {
        readyState: socket.readyState,
        url,
      })
      socket.close()
    }
  }

  disconnect() {
    console.info('[WS_DIAG] disconnect_requested')
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
    console.info('[WS_DIAG] reconnect_scheduled', {
      delayMs: delay,
      attempt: this.reconnectAttempts,
    })
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
