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

# 读取测试证书
with open("test_cert.der", "rb") as f:
    cert_bytes = f.read()

# 分包发送
MAX_CHUNK = 240
chunks = [cert_bytes[i:i+MAX_CHUNK] for i in range(0, len(cert_bytes), MAX_CHUNK)]

for i, chunk in enumerate(chunks):
    if i == 0:
        ins = 0x10  # 起始包
    elif i == len(chunks) - 1:
        ins = 0x12  # 结束包
    else:
        ins = 0x11  # 中间包
    send_apdu(ins, chunk)

print("证书传输完成")

