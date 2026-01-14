# CHISS
CHISS is a haptic-based input system designed to thwart systematic shoulder surfing. 

## Repository Structure

```
SecKeyBoard
    AndroidApp: contains the android studio project.
    Host: contains the python backend code.
    Experiments:
        Efficiency: Efficiency test result and picture.
        SideChannel: SideChannel test result and picture.
        VibrationMonitor: VibrationMonitor test result and picture.
```

## Setup: Host Environment 

Prepare a host computer with Ubuntu 22.04/24.04 installed. Plug the ACR122U card reader into the computer.

Download the driver from:

https://www.acs.com.hk/cn/products/73/acr122u-nfc%E8%AF%BB%E5%86%99%E5%99%A8-usb%E6%8E%A5%E5%8F%A3/

Install packages:
```
sudo apt-get install python3-dev libusb-dev libpcsclite-dev pcscd pcsc-tools pcscd openssl
sudo systemctl enable pcscd
sudo systemctl start pcscd

pip3 install -r requirements.txt
```

Find and check your ACR122U:

```
lsusb | grep ACR
# Example output:
# Bus 001 Device 003: ID 072f:2200 Advanced Card Systems, Ltd ACR122U
```

```
sudo systemctl status pcscd
sudo pcscd -f -d
```

## Setup: Android Application

Use Android Studio to open the project, generate a `.jks` file and build android application.

Install the android application in a mobile device.

Note: Different mobile phones have various limitations on NFC functionality. If you need to directly and completely reproduce the NFC interaction process, it is recommended to use **Google Pixel 2**. 

## Run Experiment

If you only need to experience the keyboard (human-computer interaction), simply open the installed application and follow the in-app prompts.

 If you need to experience the complete protocol, run `main.py` in `Host` folder on your computer.

## Trouble shooting: USB busy
https://bugzilla.redhat.com/show_bug.cgi?id=1555264

Run the following command and find the location of pcscd（maybe 1-5, 1-5:1.0 etc.）：
```
grep -l 072f /sys/bus/usb/devices/*/idVendor | while read vendor_path; do
    device_dir=$(dirname "$vendor_path")
    if grep -q 2200 "$device_dir/idProduct"; then
        echo "ACR122U found at: $device_dir"
        if [ -f "$device_dir/bInterfaceClass" ]; then
            echo "Interface class: $(cat $device_dir/bInterfaceClass)"
        fi
    fi
done
```

If you find USB busy, run
```
sudo sh -c "echo '1-1:1.0(replace it)' > /sys/bus/usb/drivers/pn533_usb/unbind"
```

## Trouble shooting: ACR122U is preempted

The ACR122U is often preempted by the kernel's `pn533_usb` module, which prevents the `pcscd` service from recognizing the reader. Follow these steps to resolve the conflict:

**1. Check for Conflicting Modules**

Run the following command to check if the problematic drivers are currently loaded:

lsmod | grep pn533

**2. Manually Unload the Modules**

If you see pn533, nfc, or pn533_usb in the output, unload them using:

```
sudo modprobe -r pn533_usb pn533 nfc
```

**3. Restart the Smart Card Service**

Restart pcscd to allow it to claim the device:

sudo systemctl restart pcscd

**4. Permanently Blacklist the Modules (Recommended)**

To prevent these drivers from loading automatically in the future, create a blacklist configuration file:
```
echo -e "blacklist pn533_usb\nblacklist pn533\nblacklist nfc" | sudo tee /etc/modprobe.d/blacklist-pn533.conf
```
