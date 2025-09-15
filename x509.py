from smartcard.System import readers
from smartcard.util import toHexString

# 选择读卡器
r = readers()
if not r:
    raise Exception("没有检测到读卡器")
connection = r[0].createConnection()
connection.connect()

def send_apdu(ins, payload=b''):
    # CLA=0x00, INS=自定义, P1=0x00, P2=0x00
    apdu = [0x00, ins, 0x00, 0x00, len(payload)] + list(payload)
    print(f"发送 APDU: {toHexString(apdu)}")
    data, sw1, sw2 = connection.transmit(apdu)
    print(f"响应: {toHexString(data)}, SW1={sw1:02X}, SW2={sw2:02X}")
    return data, sw1, sw2

# SELECT AID APDU 指令：00 A4 04 00 05 F0 20 02 05 20
SELECT_AID = [0x00, 0xA4, 0x04, 0x00, 0x05, 0xF0, 0x20, 0x02, 0x05, 0x21]

def send_select_aid():
    try:
        print("📤 Sending SELECT AID...")
        response, sw1, sw2 = connection.transmit(SELECT_AID)
        print(f"📥 Received: {response} (SW1 SW2: {sw1:02X} {sw2:02X})")

        if sw1 == 0x90 and sw2 == 0x00:
            print("✅ Communication with HCE tag successful.")
        else:
            print("❌ HCE tag responded with error status.")
    except Exception as e:
        print(f"❌ Error during APDU exchange: {e}")

# 读取测试证书
with open("test_cert.der", "rb") as f:
    cert_bytes = f.read()

# 分包发送
MAX_CHUNK = 240
chunks = [bytes([i//MAX_CHUNK]) + cert_bytes[i:i+MAX_CHUNK]
		for i in range(0, len(cert_bytes), MAX_CHUNK)]

send_select_aid()

send_apdu(0x10, len(cert_bytes).to_bytes(4, byteorder='big'))

for i, chunk in enumerate(chunks):
    if i == len(chunks) - 1:
        ins = 0x12  # 结束包
    else:
        ins = 0x11  # 中间包
    send_apdu(ins, chunk)

print("证书传输完成")

