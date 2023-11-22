[![GitHub Release][releases-shield]][releases]

###Not ready for production

Backend for Smart homio app
https://homio.org/

###Deprecated. To fix issue Can not open SDP session. No such file or directory”
1.Open a terminal and enter: sudo nano /etc/systemd/system/bluetooth.target.wants/bluetooth.service
2.then change line: ExecStart=/usr/lib/bluetooth/bluetoothd
to: ExecStart=/usr/lib/bluetooth/bluetoothd -C
3.sudo systemctl daemon-reload
4.sudo systemctl restart bluetooth

# Tips:

To debug code on rpi: Install plugin 'Embedded Linux
JVM' (https://medium.com/@menchukanton/setup-intellij-idea-for-remote-debugging-java-code-on-a-raspberry-pi-6e9df09dfb95)

# Local development

## Pre-requirements

1.Install PostgreSql server with default user/password and default port

# Run from IDE

Application starts as spring boot application with main class HomioApplication.java
To avoid errors with installing linux packages when starts application you need setup env variable -Ddevelopment=true

VM options:
--add-opens=java.base/java.net=ALL-UNNAMED
--add-opens=java.base/sun.net.www.protocol.jar=ALL-UNNAMED
-Ddevelopment=true

[releases-shield]: https://img.shields.io/github/v/release/homiodev/homio-app.svg

[releases]: https://github.com/homiodev/homio-app/releases
