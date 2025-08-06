
# Environment

Download the driver from:

https://www.acs.com.hk/cn/products/73/acr122u-nfc%E8%AF%BB%E5%86%99%E5%99%A8-usb%E6%8E%A5%E5%8F%A3/

或安装libccid：
```
sudo apt-get update
sudo apt-get install libccid
```

安装其他内容:
```
sudo apt-get install python3-dev libusb-dev libpcsclite-dev pcscd pcsc-tools pcscd
sudo systemctl enable pcscd
sudo systemctl start pcscd

sudo pip install pyscard
```

# Find and check your ACR122U

```
lsusb | grep ACR
# 应该看到类似输出：
# Bus 001 Device 003: ID 072f:2200 Advanced Card Systems, Ltd ACR122U
```

```
sudo systemctl status pcscd
sudo pcscd -f -d
```

# Trouble shooting: USB busy
https://bugzilla.redhat.com/show_bug.cgi?id=1555264

请运行以下命令查找 ACR122U 的路径（可能是 1-5, 1-5:1.0 等）：
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

(1-1替换为你找到的1-?)

If you find USB busy, run
```
sudo sh -c "echo '1-1:1.0' > /sys/bus/usb/drivers/pn533_usb/unbind"
```

Also:

ACR122U 有时会被 pn533_usb 模块抢占，导致 pcscd 无法识别。执行：

lsmod | grep pn533

如果你看到 pn533, nfc, pn533_usb 等模块，执行：

sudo modprobe -r pn533_usb pn533 nfc

然后重新启动 pcscd：

sudo systemctl restart pcscd

也可以彻底黑名单pn533，只需要写入这个文件：
echo -e "blacklist pn533_usb\nblacklist pn533\nblacklist nfc" | sudo tee /etc/modprobe.d/blacklist-pn533.conf

