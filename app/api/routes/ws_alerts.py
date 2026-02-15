from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from app.ws.alerts_ws import manager, decode_token_role


router = APIRouter()


@router.websocket("/ws/alerts")
async def ws_alerts(websocket: WebSocket):
    token = websocket.query_params.get("token")
    role = decode_token_role(token or "")
    await manager.connect(websocket, role)
    try:
        while True:
            # keep connection open; ignore any incoming messages
            await websocket.receive_text()
    except WebSocketDisconnect:
        await manager.disconnect(websocket)
