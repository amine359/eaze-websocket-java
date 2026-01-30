class EazeWebSocketClient {
    constructor(url) {
        this.url = url;
        this.ws = null;
        this.onMessageCallback = null;
        this.onOpenCallback = null;
        this.onCloseCallback = null;
        this.onErrorCallback = null;
    }

    connect() {
        this.ws = new WebSocket(this.url);
        this.ws.onopen = () => {
            console.log("Connected to " + this.url);
            if (this.onOpenCallback) this.onOpenCallback();
        };
        this.ws.onmessage = (event) => {
            if (this.onMessageCallback) this.onMessageCallback(event.data);
        };
        this.ws.onclose = (event) => {
            console.log("Disconnected from " + this.url);
            if (this.onCloseCallback) this.onCloseCallback(event.code, event.reason);
        };
        this.ws.onerror = (error) => {
            console.error("WebSocket Error: ", error);
            if (this.onErrorCallback) this.onErrorCallback(error);
        };
    }

    send(data) {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(data);
        } else {
            throw new Error("WebSocket is not open (ReadyState: " + (this.ws ? this.ws.readyState : 'null') + ")");
        }
    }

    close() {
        if (this.ws) {
            this.ws.close();
        }
    }

    onMessage(callback) {
        this.onMessageCallback = callback;
    }

    onOpen(callback) {
        this.onOpenCallback = callback;
    }

    onClose(callback) {
        this.onCloseCallback = callback;
    }

    onError(callback) {
        this.onErrorCallback = callback;
    }
}
