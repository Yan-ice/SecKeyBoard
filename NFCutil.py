import time
from smartcard.Exceptions import NoCardException
from smartcard.System import readers
from smartcard.util import toHexString

from cryptography import x509
from cryptography.hazmat.primitives import serialization, hashes
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.serialization import load_pem_private_key

INS_INIT = 0x10
INS_CONTINUE = 0x11

INS_END_WITH_CERT = 0x12   # finish transfer, and the transfer item is CERT
INS_END_WITH_DH = 0x13     # finish transfer, and the transfer item is ECDH pub
INS_END_WITH_DH_SIGNATURE = 0x14

INS_STATUS = 0x20

def wait_for_card():
    reader = readers()
    if not reader:
        print("❌ No smart card readers found.")
        return
    reader = reader[0]
    connection = reader.createConnection()
    while True:
        try:
            connection.connect()
            print("Phone connected (HCE card detected)")
            return connection
        except NoCardException:
            # No card present; sleep and retry
            time.sleep(0.2)
        except Exception as e:
            print(f"⚠️ Unexpected error: {e}")
            time.sleep(2)

def send_apdu(connection, ins, payload=b''):
    # CLA=0x00, INS=自定义, P1=0x00, P2=0x00
    apdu = [0x00, ins, 0x00, 0x00, len(payload)] + list(payload)
    #print(f"发送 APDU: {toHexString(apdu)}")
    data, sw1, sw2 = connection.transmit(apdu)
    #print(f"响应: {toHexString(data)}, SW1={sw1:02X}, SW2={sw2:02X}")
    return data, sw1, sw2


def send_item(connection, data, typecode):
    MAX_CHUNK = 240
    chunks = [bytes([i//MAX_CHUNK]) + data[i:i+MAX_CHUNK]
                for i in range(0, len(data), MAX_CHUNK)]

    send_apdu(connection, INS_INIT, len(data).to_bytes(4, byteorder='big'))
    
    # send packet size (0x10)
    for i, chunk in enumerate(chunks):
        if i == len(chunks) - 1:
            ins = typecode  # end packet with typecode
        else:
            ins = INS_CONTINUE  # mid packet
        send_apdu(connection, ins, chunk)
	
def send_select_aid(connection, last_part):
    SELECT_AID = [0x00, 0xA4, 0x04, 0x00, 0x05, 0xF0, 0x20, 0x02, 0x05, last_part]
    try:
        print("📤 Sending SELECT AID...")
        response, sw1, sw2 = connection.transmit(SELECT_AID)

        response = int.from_bytes(response, byteorder='big')
        print(f"📥 Received: {response} (SW1 SW2: {sw1:02X} {sw2:02X})")

        if sw1 == 0x90 and sw2 == 0x00:
            print("✅ Communication with HCE tag successful.")
            return response
        else:
            print("❌ HCE tag responded with error status.")
    except Exception as e:
        print(f"❌ Error during APDU exchange: {e}")

    return 0

