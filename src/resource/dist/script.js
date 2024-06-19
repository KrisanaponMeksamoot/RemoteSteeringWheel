var con = new WebSocket(`wss://${location.host}/ws`);
var alog = document.getElementById("log");
var astat = document.getElementById("stat");

const ros = new RelativeOrientationSensor({frequency: 20});
Promise.all([
  navigator.permissions.query({ name: "accelerometer" }),
  navigator.permissions.query({ name: "gyroscope" }),
]).then((results) => {
  if (results.every((result) => result.state === "granted")) {
    ros.start();
  } else {
    alog.innerText = "No permissions to use RelativeOrientationSensor.";
  }
});

var sending = false;

let yprPack = new ArrayBuffer(16);
new Uint32Array(yprPack)[0] = 2;
ros.addEventListener("reading", () => {
    let qa = ros.quaternion;
    let q = {x: qa[0], y: qa[1], z: qa[2], w: qa[3]};
    var yaw = Math.atan2(2.0*(q.y*q.z + q.w*q.x), q.w*q.w - q.x*q.x - q.y*q.y + q.z*q.z);
    var pitch = Math.asin(-2.0*(q.x*q.z - q.w*q.y));
    var roll = Math.atan2(2.0*(q.x*q.y + q.w*q.z), q.w*q.w + q.x*q.x - q.y*q.y - q.z*q.z);
    astat.innerText = `Orientation ${(pitch*180/Math.PI).toFixed(2)}, ${(roll*180/Math.PI).toFixed(2)}, ${(yaw*180/Math.PI).toFixed(2)}`;
    if (!sending)
        return;
    let fa = new Float32Array(yprPack, 4);
    fa[0] = yaw;
    fa[1] = pitch;
    fa[2] = roll;
    con.send(yprPack);
});

document.getElementById("togglesend").addEventListener("click", (e) => {
    if (con.readyState == WebSocket.CONNECTING)
        return;
    if (sending) {
        stopSend();
    } else {
        if (con.readyState == WebSocket.CLOSED) {
            con = new WebSocket(`wss://${location.host}/ws`);
            init_con();
            e.target.innerText = "Reconnecting";
            e.target.disabled = true;
            con.addEventListener("open", () => {
                e.target.innerText = "Stop";
                sending = true;
                e.target.disabled = false;
                lockOrientation();
            });
            con.addEventListener("error", () => {
                e.target.innerText = "Reconnect";
                e.target.disabled = false;
            });
        } else {
            e.target.innerText = "Stop";
            sending = true;
            lockOrientation();
        }
    }
});

function stopSend() {
    document.getElementById("togglesend").innerText = "Start";
    sending = false;
    con.send(new Uint32Array([1]));
    screen.orientation.unlock();
    alog.innerText = "unlocked orientation";
}

async function lockOrientation() {
    try {
        await document.body.requestFullscreen();
        await screen.orientation.lock("landscape");
        alog.innerText = "locked orientation";
    } catch (e) {
        alog.innerText = e.message;
    }
}

function init_con() {
    con.addEventListener("close", (ce) => {
        document.getElementById("togglesend").innerText = "Reconnect";
        sending = false;
        screen.orientation.unlock();
        alog.innerText = "unlocked orientation";
    });
}

init_con();

let keyPac = new ArrayBuffer(9);
new Uint32Array(keyPac, 0, 1)[0] = 3;
document.getElementById("key_w").addEventListener("pointerdown", (e) => {
    if (!sending)
        return;
    new Uint32Array(keyPac, 4, 1)[0] = 87;
    new Uint8Array(keyPac, 8, 1)[0] = 1;
    con.send(keyPac);
});
document.getElementById("key_w").addEventListener("pointerup", (e) => {
    if (!sending)
        return;
    new Uint32Array(keyPac, 4, 1)[0] = 87;
    new Uint8Array(keyPac, 8, 1)[0] = 0;
    con.send(keyPac);
});
document.getElementById("key_s").addEventListener("pointerdown", (e) => {
    if (!sending)
        return;
    new Uint32Array(keyPac, 4, 1)[0] = 83;
    new Uint8Array(keyPac, 8, 1)[0] = 1;
    con.send(keyPac);
});
document.getElementById("key_s").addEventListener("pointerup", (e) => {
    if (!sending)
        return;
    new Uint32Array(keyPac, 4, 1)[0] = 83;
    new Uint8Array(keyPac, 8, 1)[0] = 0;
    con.send(keyPac);
});