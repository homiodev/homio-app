firewall blocking inbound UDP was my issue. I was surprised that opening the firewall was not on the front page. The following commands open the needed UDP ports on CentOS 7.

sudo firewall-cmd --permanent --add-port=6666/udp
sudo firewall-cmd --permanent --add-port=6667/udp
sudo firewall-cmd --permanent --add-port=6668/tcp
sudo firewall-cmd --reload
