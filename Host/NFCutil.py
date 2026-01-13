import time
from smartcard.Exceptions import CardConnectionException, NoCardException
from smartcard.System import readers
from smartcard.util import toHexString

from cryptography import x509
from cryptography.hazmat.primitives import serialization, hashes
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.serialization import load_pem_private_key

INS_INIT = 0xD0
INS_CONTINUE = 0xD1
INS_END = 0xD2

INS_RECV_INIT = 0xB0
INS_RECV_CONTINUE = 0xB1

INS_STATE = 0xB2
STATE_END = 0x0   # finish transfer, and the transfer item is CERT
STATE_PHASE1 = 0x1     # finish transfer, and the transfer item is ECDH pub
STATE_PHASE2 = 0x2


SEND_CERT = 0x1   # finish transfer, and the transfer item is CERT
SEND_DH = 0x2     # finish transfer, and the transfer item is ECDH pub
SEND_DH_SIGNATURE = 0x3

RECV_CLIENT_CERT = 0x1
RECV_CLIENT_DH = 0x2
RECV_CLIENT_DH_SIGNATURE = 0x3
RECV_AES_PWD = 0x4

INS_STATUS = 0x40

def wait_for_card():
    reader = readers()
    if not reader:
        print("❌ No smart card readers found.")
        return
    reader = reader[0]
    while True:
        try:
            connection = reader.createConnection()
            connection.connect()
            print("Phone connected (HCE card detected)")
            send_select_aid(connection, 0x21)
            return connection
        except NoCardException:
            # No card present; sleep and retry
            time.sleep(0.2)
        except CardConnectionException:
            print("No response, retrying.")
            # wait for HCE service
            time.sleep(0.2)
        except Exception as e:
            print(f"⚠️ Unexpected error: {e}")
            time.sleep(2)

def send_apdu(connection, ins, payload=b'1', p1=0x00):
    # CLA=0x00, INS=自定义, P1=0x00, P2=0x00
    apdu = [0x00, ins, p1, 0x00, len(payload)] + list(payload)
    # print(f"发送 APDU: {toHexString(apdu)}")

    data, sw1, sw2 = connection.transmit(apdu)
    # print(f"响应: {toHexString(data)}, SW1={sw1:02X}, SW2={sw2:02X}")
    return data, sw1, sw2

def send_item(connection, data, typecode):
    MAX_CHUNK = 240
    chunks = [bytes([i//MAX_CHUNK]) + data[i:i+MAX_CHUNK]
                for i in range(0, len(data), MAX_CHUNK)]

    send_apdu(connection, INS_INIT, len(data).to_bytes(4, byteorder='big'))
    
    # send packet size (0x10)
    for i, chunk in enumerate(chunks):
        if i == len(chunks) - 1:
            ins = INS_END  # end packet with typecode
        else:
            ins = INS_CONTINUE  # mid packet
        send_apdu(connection, ins, chunk, typecode)

def recv_item(connection, typecode):
    buffer = bytearray()

    data, sw1, sw2 = send_apdu(connection, INS_RECV_INIT, b'1', typecode)

    # send packet size (0x10)
    while data and data[0] > 0:
        buffer.extend(data[1:])
        data, sw1, sw2 = send_apdu(connection, INS_RECV_CONTINUE)

    return buffer

def send_select_aid(connection, last_part):
    response, sw1, sw2 = send_apdu(connection, 0xA4, 
                            [0xF0, 0x20, 0x02, 0x05, 0x21], 0x04)

    response = int.from_bytes(response, byteorder='big')

    if sw1 == 0x90 and sw2 == 0x00:
        print("✅ Communication with HCE tag successful.")
        return response
    else:
        print("❌ HCE tag responded with error status.")

    return 0


def send_state(connection, state):

    send_apdu(connection, INS_STATE, b'0', state)
    
    return 0
