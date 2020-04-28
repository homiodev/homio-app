# touchHome backend application

[![Build Status](https://travis-ci.com/touchhome/touchhome-backend.svg)](https://travis-ci.com/github/touchhome/touchhome-backend)

[![GitHub release](https://img.shields.io/github/v/release/touchhome/touchhome-backend)](https://github.com/touchhome/touchhome-backend/releases)
###Not ready for production

Backend for Smart TouchHome
http://touchhome.org/

###Deprecated. To fix issue Can not open SDP session. No such file or directory”
1.Open a terminal and enter: sudo nano /etc/systemd/system/bluetooth.target.wants/bluetooth.service
2.then change line: ExecStart=/usr/lib/bluetooth/bluetoothd
to: ExecStart=/usr/lib/bluetooth/bluetoothd -C	
3.sudo systemctl daemon-reload
4.sudo systemctl restart bluetooth

# Tips:
To debug code on rpi: Install plugin 'Embedded Linux JVM' (https://medium.com/@menchukanton/setup-intellij-idea-for-remote-debugging-java-code-on-a-raspberry-pi-6e9df09dfb95)

# Set Bluetooth device name:
If you want to change the bluetooth device name permanently, you have to create a file called /etc/machine-info which should have the following content:
PRETTY_HOSTNAME=device-name
Then issue "service bluetooth restart" after that
