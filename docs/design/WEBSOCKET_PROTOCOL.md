# WebSocket Protocol (Deferred)

**Status**: Deferred indefinitely with multiplayer (2026-06-08). This protocol design is retained for reference if multiplayer returns. Current single-player modes (Daily Challenge, Free Play) use REST only.

## Connection
- Endpoint: `wss://api.trivia501.com/ws?token={JWT_TOKEN}`
- Protocol: STOMP over WebSocket
- Heartbeat: 30-second ping/pong

## Message Format
```json
{
  "type": "MESSAGE_TYPE",
  "gameId": "uuid",
  "payload": { /* message-specific data */ }
}
```

## Critical Messages (designed, not implemented)

**Client → Server**:
- `SUBMIT_ANSWER`: Submit player name for validation
- `REQUEST_REFRESH`: Request new question (only at game start)
- `VOTE_REFRESH`: Vote on opponent's refresh request

**Server → Client**:
- `GAME_STATE`: Full game state update (scores, turn, timer)
- `ANSWER_RESULT`: Validation result (valid/bust/invalid)
- `TURN_TIMEOUT`: Timeout event (timer reduction)
- `GAME_OVER`: Game complete (winner, final scores)

## Reconnection Handling
- 30-second grace period on disconnect
- Server maintains game state during grace period
- Full game state sync on reconnect
