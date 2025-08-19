from smartcard.System import readers
from smartcard.Exceptions import CardConnectionException
import time

# SELECT AID APDU 指令：00 A4 04 00 05 F0 20 02 05 20
SELECT_AID = [0x00, 0xA4, 0x04, 0x00, 0x05, 0xF0, 0x20, 0x02, 0x05, 0x20]

def send_select_aid(connection):
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

def run_reader():
    print("📡 Waiting for Android HCE device (Tap your phone)...")

    rlist = readers()
    if not rlist:
        print("❌ No smart card readers found.")
        return

    reader = rlist[0]
    print(f"✅ Using reader: {reader}")

    while True:
        try:
            connection = reader.createConnection()
            connection.connect()
            print("📲 HCE device detected!")

            send_select_aid(connection)

            print("🔄 Waiting for next tag...\n")
            time.sleep(1)
        except CardConnectionException:
            # No card present; sleep and retry
            time.sleep(0.5)
        except Exception as e:
            print(f"⚠️ Unexpected error: {e}")
            time.sleep(1)

if __name__ == "__main__":
    run_reader()

