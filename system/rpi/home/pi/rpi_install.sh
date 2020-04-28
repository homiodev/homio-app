#!/usr/bin/env bash
# Before install this file on RPI you need prepare wifi
#sudo raspi-config
# Install git
sudo apt-get update
sudo apt-get install default-jre

# Install postgresql
sudo apt install postgresql libpq-dev postgresql-client postgresql-client-common -y

# Create folder for launch.jar
mkdir /home/pi/system

# Creates launch script at start up
sudo sed  '/exit 0/i (cd /home/pi/system && java -Xdebug -Xrunjdwp:transport=dt_socket,address=8998,server=y,suspend=n -jar SmartHouse.jar &)' /etc/rc.local -i.bkp


# Create postgresql user
sudo su postgres
createuser pi -P --interactive
psql
create database smart;


#Install pigpio
#sudo apt-get install pigpio python-pigpio python3-pigpio

#Install i2C-tools
#sudo apt-get install i2c-tools
#sudo adduser pi i2c

# Disable WiFi Connections power_save mode
sudo iw dev wlan0 set power_save off
