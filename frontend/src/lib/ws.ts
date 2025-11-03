import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

export function connectWS(onConnect: (client: Client) => void) {
  const client = new Client({
    webSocketFactory: () => new SockJS('http://localhost:8080/ws')
  })
  client.onConnect = () => onConnect(client)
  client.activate()
  return client
}


